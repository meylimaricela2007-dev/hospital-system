package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CitaService {

    @Autowired
    private HorarioDisponibleRepository horarioDisponibleRepository;
    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EstadoCitaRepository estadoCitaRepository;
    @Autowired
    private TipoSeguimientoRepository tipoSeguimientoRepository;
    @Autowired
    private ConsultaRepository consultaRepository;
    @Autowired
    private PrioridadRepository prioridadRepository;
    @Autowired
    private NotificacionService notificacionService;
    @Autowired
    private EventoAgendaRepository eventoAgendaRepository;

    // Precio fijo de la consulta y minutos de reserva temporal, configurables
    // sin tocar código (ver application.properties). Si no se configuran,
    // usan estos valores por defecto.
    @Value("${app.citas.monto-consulta:150.00}")
    private BigDecimal montoConsulta;

    @Value("${app.citas.minutos-reserva:5}")
    private int minutosReserva;

    /** CU-16: ¿el médico tiene un evento (reunión, descanso, capacitación...) que se cruza con este horario? */
    private boolean medicoOcupadoPorEvento(HorarioDisponible h) {
        LocalDateTime inicio = LocalDateTime.of(h.getFecha(), h.getHoraInicio());
        LocalDateTime fin = LocalDateTime.of(h.getFecha(), h.getHoraFin());
        return eventoAgendaRepository.existeSolapamiento(h.getMedico().getIdUsuario(), inicio, fin);
    }

    /** Paso 4 del wizard: horarios disponibles de un médico en una especialidad/sede. */
    @Transactional(readOnly = true)
    public List<HorarioDisponibleDTO> obtenerHorariosDisponibles(Integer idMedico, Integer idSucursal, Integer idEspecialidad) {
        return horarioDisponibleRepository.buscarDisponibles(idMedico, idSucursal, idEspecialidad, LocalDateTime.now())
                .stream()
                .filter(h -> LocalDateTime.of(h.getFecha(), h.getHoraInicio()).isAfter(LocalDateTime.now())) // QA: no ofrecer horas que ya pasaron hoy (RN-CU03-05)
                .filter(h -> !medicoOcupadoPorEvento(h)) // CU-16: no ofrecer horarios que chocan con su agenda
                .map(h -> new HorarioDisponibleDTO(h.getIdHorario(), h.getFecha(), h.getHoraInicio()))
                .toList();
    }

    /**
     * CORREGIDO (CU-06, FA03): el parámetro creadaPorPersonalInterno evita que
     * un walk-in agendado por Recepción/Caja quede con el mismo temporizador
     * de expiración que una cita agendada por el propio paciente en línea. El
     * documento de CU-06 es explícito: "Las citas creadas por personal interno
     * no se cancelan automáticamente". Cuando es true, fechaExpiracionReserva
     * y horario.reservadoHasta quedan en null, que en todo el resto del código
     * (PagoService, CajaService) ya se interpreta como "sin vencimiento".
     */
    @Transactional
    public CitaCreadaResponse crearCita(CrearCitaRequest request, Integer idPaciente, boolean creadaPorPersonalInterno) {
        // QA: se bloquea la fila del horario (SELECT ... FOR UPDATE). Antes dos pacientes que
        // confirmaban el mismo horario a la vez pasaban los dos la validación "sigue disponible"
        // y reservaban el mismo horario con el mismo médico (doble reserva).
        HorarioDisponible horario = horarioDisponibleRepository.buscarParaReservar(request.getHorarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario seleccionado no existe."));

        // QA: el horario elegido debe corresponder a la sede/especialidad/médico que llegan en la solicitud.
        if (!horario.getMedico().getIdUsuario().equals(request.getMedicoId())
                || !horario.getSucursal().getIdSucursal().equals(request.getSucursalId())
                || !horario.getEspecialidad().getIdEspecialidad().equals(request.getEspecialidadId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El horario seleccionado no corresponde al médico, sede o especialidad elegidos.");
        }

        // QA (RN-CU03-05): no se puede agendar en una hora que ya pasó.
        if (!LocalDateTime.of(horario.getFecha(), horario.getHoraInicio()).isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe seleccionar una fecha y hora futuras. El horario seleccionado ya pasó.");
        }

        boolean sigueDisponible = HorarioDisponible.DISPONIBLE.equals(horario.getEstado())
                || (HorarioDisponible.RESERVADO.equals(horario.getEstado())
                    && horario.getReservadoHasta() != null
                    && horario.getReservadoHasta().isBefore(LocalDateTime.now()));

        if (!sigueDisponible || medicoOcupadoPorEvento(horario)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El horario seleccionado ya no está disponible. Por favor, elija otro horario.");
        }

        Usuario paciente = usuarioRepository.findById(idPaciente)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para agendar una cita."));

        LocalDateTime expiracion = creadaPorPersonalInterno ? null : LocalDateTime.now().plusMinutes(minutosReserva);

        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setMedico(horario.getMedico());
        cita.setSucursal(horario.getSucursal());
        cita.setEspecialidad(horario.getEspecialidad());
        cita.setHorario(horario);
        cita.setFechaCita(horario.getFecha());
        cita.setHoraCita(horario.getHoraInicio());
        cita.setMotivoConsulta(request.getMotivo());
        cita.setEstadoCita(buscarEstadoPorNombre(EstadoCita.PENDIENTE_DE_PAGO));
        cita.setMontoTotal(montoConsulta);
        cita.setFechaExpiracionReserva(expiracion);

        // CU-08 FA02 / CU-12: cita de seguimiento. El médico la agenda desde la
        // sección "Evaluados", enlazada con la consulta que la originó. Llega
        // aquí ya como creadaPorPersonalInterno=true (sin límite de tiempo
        // para pagar). [RN-CU11-01] El tipo de seguimiento es obligatorio y
        // [RN-CU11-02] la cita debe ser con el MISMO médico, sede y especialidad.
        TipoSeguimiento tipoSeguimiento = null;
        if (request.getIdConsultaOrigen() != null) {
            Consulta consultaOrigen = consultaRepository.findById(request.getIdConsultaOrigen())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La consulta de origen no existe."));
            if (request.getIdTipoSeguimiento() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar el tipo de seguimiento.");
            }
            tipoSeguimiento = tipoSeguimientoRepository.findById(request.getIdTipoSeguimiento())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de seguimiento no existe."));

            Cita citaOrigen = consultaOrigen.getCita();
            boolean mismoMedico = horario.getMedico().getIdUsuario().equals(consultaOrigen.getMedico().getIdUsuario());
            boolean mismaSede = horario.getSucursal().getIdSucursal().equals(citaOrigen.getSucursal().getIdSucursal());
            boolean mismaEspecialidad = horario.getEspecialidad().getIdEspecialidad().equals(citaOrigen.getEspecialidad().getIdEspecialidad());
            if (!mismoMedico || !mismaSede || !mismaEspecialidad) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El seguimiento debe agendarse con el mismo médico, sede y especialidad de la consulta.");
            }

            cita.setConsultaOrigen(consultaOrigen);
            cita.setEsSeguimiento(true);
            cita.setTipoSeguimiento(tipoSeguimiento);
            cita.setPrioridad(resolverPrioridad(request.getIdPrioridad()));
        }

        cita = citaRepository.save(cita);

        // Reservamos el horario para que nadie más lo tome mientras este paciente paga.
        horario.setEstado(HorarioDisponible.RESERVADO);
        horario.setReservadoHasta(expiracion);
        horarioDisponibleRepository.save(horario);

        String mensaje;
        if (request.getIdConsultaOrigen() != null) {
            // CU-12, paso 8: mensaje EXACTO del documento
            mensaje = "Cita de seguimiento agendada exitosamente. Tipo: " + tipoSeguimiento.getNombre()
                    + ". Paciente: " + paciente.getNombreCompleto() + ".";
            // CU-12, RN-CU11-04: notificación al correo del paciente
            // (NotificacionService atrapa sus propios errores de envío)
            notificacionService.enviarCitaSeguimiento(cita);
        } else {
            mensaje = creadaPorPersonalInterno
                    ? "Cita reservada. Puede pagarse en caja sin límite de tiempo."
                    : "Cita reservada. Tiene " + minutosReserva + " minutos para completar el pago.";
        }
        return new CitaCreadaResponse(true, cita.getIdCita(), cita.getMontoTotal(), expiracion, mensaje,
                cita.getFechaCita(), cita.getHoraCita());
    }

    /**
     * CU-12, paso 2: consulta la consulta padre y su cita para pre-cargar
     * paciente, médico, especialidad y sucursal. Solo el médico que atendió
     * esa consulta puede agendar su seguimiento.
     */
    @Transactional(readOnly = true)
    public SeguimientoContextoDTO obtenerContextoSeguimiento(Integer idConsulta, Integer idMedicoSesion) {
        Consulta consulta = consultaRepository.findById(idConsulta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La consulta de origen no existe."));
        if (!consulta.getMedico().getIdUsuario().equals(idMedicoSesion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el médico que atendió la consulta puede agendar su seguimiento.");
        }
        Cita cita = consulta.getCita();
        return new SeguimientoContextoDTO(
                consulta.getIdConsulta(),
                cita.getPaciente().getIdUsuario(),
                cita.getPaciente().getNombreCompleto(),
                consulta.getMedico().getIdUsuario(),
                consulta.getMedico().getNombreCompleto(),
                cita.getEspecialidad().getIdEspecialidad(),
                cita.getEspecialidad().getNombre(),
                cita.getSucursal().getIdSucursal(),
                cita.getSucursal().getNombre());
    }

    /** CU-12, paso 6: la prioridad elegida, o "Normal" si no se indicó. */
    private Prioridad resolverPrioridad(Integer idPrioridad) {
        if (idPrioridad != null) {
            return prioridadRepository.findById(idPrioridad)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La prioridad seleccionada no existe."));
        }
        return prioridadRepository.findAll().stream()
                .filter(p -> "Normal".equalsIgnoreCase(p.getNombre()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resumen de la cita para las pantallas de confirmación (CU-03), pago
     * (CU-04) y Recepción (CU-05).
     *
     * CORREGIDO: antes este método no recibía quién lo estaba pidiendo, así
     * que el controlador no tenía forma de impedir que un paciente consultara
     * la cita de otro. Ahora se exige que la cita sea del propio paciente, a
     * menos que quien pregunta sea personal interno (Recepcionista, Cajero,
     * etc.), que sí necesita poder ver cualquier cita para atender a los
     * pacientes que llegan a la clínica.
     */
    @Transactional(readOnly = true)
    public CitaResumenResponse obtenerResumen(Integer idCita, Integer idUsuarioSesion, boolean esPersonalInterno) {
        Cita cita = citaRepository.findById(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));

        if (!esPersonalInterno && !cita.getPaciente().getIdUsuario().equals(idUsuarioSesion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta cita no pertenece a su cuenta.");
        }

        return new CitaResumenResponse(
                cita.getIdCita(),
                cita.getPaciente().getNombreCompleto(),
                cita.getMedico().getNombreCompleto(),
                cita.getEspecialidad().getNombre(),
                cita.getSucursal().getNombre(),
                cita.getFechaCita(),
                cita.getHoraCita(),
                cita.getMotivoConsulta(),
                cita.getEstadoCita().getNombreEstado(),
                cita.getMontoTotal(),
                cita.getFechaExpiracionReserva()
        );
    }

    private EstadoCita buscarEstadoPorNombre(String nombre) {
        return estadoCitaRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de cita '" + nombre + "' no existe en el catálogo."));
    }

    /**
     * NUEVO: "Mis Citas" del portal del paciente. Trae TODAS sus citas
     * (pasadas y futuras, en cualquier estado) para que pueda revisar su
     * historial completo, no solo las pendientes de pago.
     */
    @Transactional(readOnly = true)
    public List<MiCitaDTO> listarMisCitas(Integer idPaciente) {
        return citaRepository.findByPaciente_IdUsuarioOrderByFechaCitaDescHoraCitaDesc(idPaciente).stream()
                .map(cita -> new MiCitaDTO(
                        cita.getIdCita(),
                        cita.getEspecialidad().getNombre(),
                        cita.getMedico().getNombreCompleto(),
                        cita.getSucursal().getNombre(),
                        cita.getFechaCita(),
                        cita.getHoraCita(),
                        cita.getMotivoConsulta(),
                        cita.getEstadoCita().getNombreEstado(),
                        cita.getMontoTotal(),
                        Boolean.TRUE.equals(cita.getEsEmergencia())
                ))
                .toList();
    }
}
