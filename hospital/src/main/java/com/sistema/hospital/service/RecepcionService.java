package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * CU-05: Recepción y Verificación de Cita.
 * Reglas de negocio del Empleado Interno (Recepcionista) al recibir a un
 * paciente en la clínica: buscar su cita, verificar su estado y registrar
 * su llegada. Ver el documento "5_CU_Recepcion_y_Verificacion_de_Cita.docx"
 * para el detalle de cada flujo alterno (FA01-FA09) referenciado en los
 * comentarios de este archivo.
 */
@Service
public class RecepcionService {

    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EstadoCitaRepository estadoCitaRepository;
    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private EspecialidadRepository especialidadRepository;
    @Autowired
    private CatalogoService catalogoService;
    @Autowired
    private ReasignacionMedicoRepository reasignacionMedicoRepository;

    // Mismo precio fijo de consulta que usa CU-03/CU-04 (ver application.properties).
    // Se reutiliza para las citas de emergencia (FA01) por consistencia contable;
    // no hay todavía una tarifa distinta definida para emergencias.
    @Value("${app.citas.monto-consulta:150.00}")
    private BigDecimal montoConsulta;

    //  búsqueda de la cita
    @Transactional(readOnly = true)
    public RecepcionBusquedaResponse buscarPorNumeroCita(Integer idCita) {
        Optional<Cita> citaOpt = citaRepository.findById(idCita);
        if (citaOpt.isEmpty()) {
            return new RecepcionBusquedaResponse("SIN_RESULTADOS",
                    "No se encontró ninguna cita con el número " + idCita + ".",
                    null, null, null, null);
        }
        return armarResultadoParaCita(citaOpt.get());
    }

    /**
     * FA02 "Paciente llega sin número de cita": se busca por DPI. Si el
     * paciente no existe -> FA03. Si existe pero no tiene ninguna cita
     * activa -> FA04. Si tiene alguna -> se resuelve igual que la búsqueda
     * por número de cita (continúa en el paso 4 del flujo normal), tomando
     * siempre la más próxima en el tiempo.
     */
    @Transactional(readOnly = true)
    public RecepcionBusquedaResponse buscarPorDpi(String dpi) {
        Usuario paciente = usuarioRepository.findByDpi(dpi).orElse(null);

        if (paciente == null) {
            // FA03: paciente no registrado en el sistema
            return new RecepcionBusquedaResponse("PACIENTE_NO_REGISTRADO",
                    "No se encontró ningún paciente con ese DPI.",
                    "Es necesario registrar al paciente antes de continuar.",
                    null, null, null);
        }

        if (!Rol.PACIENTE.equalsIgnoreCase(paciente.getRol().getNombreRol())) {
            // Caso de borde no descrito literalmente en el documento, pero
            // necesario: el DPI buscado es de personal interno, no de un paciente.
            return new RecepcionBusquedaResponse("NO_ES_PACIENTE",
                    "Este DPI pertenece a un usuario del sistema interno, no a un paciente.",
                    null, null, null, null);
        }

        List<Cita> citasDelPaciente = citaRepository
                .findByPaciente_IdUsuarioOrderByFechaCitaAscHoraCitaAsc(paciente.getIdUsuario());

        // Una cita "activa" para efectos de Recepción es la que todavía puede atenderse:
        // no Cancelada, no Expirada, y tampoco una ya terminada (Atención Finalizada / No Asistió).
        // QA: antes solo se excluían Cancelada y Expirada, y como la lista viene ordenada de la
        // más ANTIGUA a la más reciente, `findFirst()` devolvía la cita vieja ya finalizada
        // (p. ej. paciente con DPI 1029384756101: cita #10 finalizada del 02/10 y cita #15 vigente),
        // y Recepción nunca podía registrar la llegada de la cita real. Ahora, entre las activas,
        // se toma la primera de hoy en adelante (la más próxima) y, si no hay, la más reciente.
        LocalDate hoy = LocalDate.now();
        List<Cita> activas = citasDelPaciente.stream()
                .filter(c -> {
                    String e = c.getEstadoCita().getNombreEstado();
                    return !EstadoCita.CANCELADA.equals(e) && !EstadoCita.EXPIRADA.equals(e)
                            && !EstadoCita.ATENCION_FINALIZADA.equals(e) && !EstadoCita.NO_ASISTIO.equals(e);
                })
                .toList();
        Optional<Cita> citaActiva = activas.stream()
                .filter(c -> !c.getFechaCita().isBefore(hoy))
                .findFirst();
        if (citaActiva.isEmpty() && !activas.isEmpty()) {
            citaActiva = Optional.of(activas.get(activas.size() - 1));
        }

        if (citaActiva.isEmpty()) {
            // FA06: no hay citas activas, pero la más reciente del paciente fue cancelada
            Optional<Cita> ultimaCancelada = citasDelPaciente.stream()
                    .filter(c -> EstadoCita.CANCELADA.equals(c.getEstadoCita().getNombreEstado()))
                    .reduce((primera, segunda) -> segunda);   // la lista viene ordenada, se queda con la última
            if (ultimaCancelada.isPresent()) {
                return armarResultadoParaCita(ultimaCancelada.get());
            }

            // FA04: paciente registrado, pero sin citas activas hoy
            return new RecepcionBusquedaResponse("PACIENTE_SIN_CITAS",
                    "El paciente " + paciente.getNombreCompleto() + " está registrado pero no tiene citas activas.",
                    "Puede crear una nueva cita para este paciente.",
                    null, paciente.getIdUsuario(), paciente.getNombreCompleto());
        }

        return armarResultadoParaCita(citaActiva.get());
    }

    /** Traduce el estado real de una cita a la respuesta que espera el frontend (FA05/FA06/paso 4). */
    private RecepcionBusquedaResponse armarResultadoParaCita(Cita cita) {
        String estado = cita.getEstadoCita().getNombreEstado();
        RecepcionCitaDTO dto = convertirADTO(cita);

        if (EstadoCita.CANCELADA.equals(estado)) {
            // FA06: cita cancelada
            return new RecepcionBusquedaResponse("CITA_CANCELADA",
                    "La cita fue cancelada. El paciente debe agendar una nueva cita.",
                    null, dto, null, null);
        }
        if (EstadoCita.PENDIENTE_DE_PAGO.equals(estado)) {
            // FA05: cita sin pago confirmado
            return new RecepcionBusquedaResponse("CITA_PENDIENTE_PAGO",
                    "La cita del paciente tiene estado 'Pendiente de pago'. Debe realizar el pago en caja antes de ser atendido.",
                    null, dto, null, null);
        }
        // Confirmada, Paciente Presente, o cualquier estado posterior del flujo:
        // el paso 4 del flujo normal simplemente muestra los datos de la cita.
        return new RecepcionBusquedaResponse("CITA_ENCONTRADA", null, null, dto, null, null);
    }

    // =================================================================
    // Paso 8 del flujo normal: registrar la llegada
    // =================================================================

    /**
     * FA09 "Error al registrar llegada" incluida: si la cita ya no está en
     * estado "Confirmada" (por ejemplo, porque otro recepcionista ya
     * registró la llegada, o el paciente canceló mientras tanto), o si
     * @Version detecta que alguien más la modificó al mismo tiempo, se
     * responde con el mensaje de error que pide el documento en vez de
     * dejar que la excepción técnica llegue cruda al navegador.
     */
    @Transactional
    public RegistrarLlegadaResponse registrarLlegada(Integer idCita) {
        // QA: bloqueo de fila; dos recepcionistas simultáneos ya no pueden registrar la llegada a la vez (FA09).
        Cita cita = citaRepository.buscarParaActualizar(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));

        if (!EstadoCita.CONFIRMADA.equals(cita.getEstadoCita().getNombreEstado())) {
            // FA09: la cita ya no está en el estado que se esperaba (por
            // ejemplo, otro usuario ya registró su llegada).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Operación no permitida. La cita ya no se encuentra en estado 'Confirmada'.");
        }

        // QA: la llegada solo se registra el día de la cita. Antes se podía "llegar" a una cita
        // de otra fecha y quedaba en "Paciente Presente" para siempre, porque la cola de
        // Enfermería (CU-07) solo muestra las citas de HOY y el paciente nunca aparecía.
        if (!cita.getFechaCita().equals(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Operación no permitida. La cita está programada para el " + cita.getFechaCita()
                            + " y la llegada solo puede registrarse el día de la cita.");
        }

        cita.setEstadoCita(buscarEstadoCita(EstadoCita.PACIENTE_PRESENTE));
        cita.setHoraLlegada(LocalDateTime.now());

        try {
            cita = citaRepository.save(cita);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // FA09: dos recepcionistas intentaron registrar la llegada casi
            // al mismo tiempo; @Version en Cita detecta el choque.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Error al registrar la llegada.");
        }

        String mensaje = "La llegada del paciente " + cita.getPaciente().getNombreCompleto()
                + " ha sido registrada exitosamente. El paciente debe pasar a la sala de espera.";
        String indicador = "Llegada registrada — esperando llamado de enfermería";

        if (Boolean.TRUE.equals(cita.getEsEmergencia())) {
            // FA08: cita con prioridad de emergencia
            mensaje = "Paciente " + cita.getPaciente().getNombreCompleto()
                    + " registrado con prioridad de EMERGENCIA. El paciente debe pasar directamente a toma de signos vitales.";
            indicador = "EMERGENCIA — pasar directamente a signos vitales";
        }

        return new RegistrarLlegadaResponse(true, mensaje, indicador,
                Boolean.TRUE.equals(cita.getEsEmergencia()), convertirADTO(cita));
    }

    // =================================================================
    // FA01: paciente llega por emergencia (sin cita previa)
    // =================================================================

    /**
     * Crea una cita "walk-in" de emergencia y la marca como "Paciente
     * Presente" de una vez (se salta Pendiente de pago/Confirmada: una
     * emergencia no espera pago para ser atendida). El médico se asigna
     * automáticamente: el primero disponible de esa especialidad y sede,
     * ya que en una emergencia el Recepcionista no elige médico uno por
     * uno como en el wizard normal de CU-03.
     *
     * ACTUALIZADO: el documento indica que después de este paso "se
     * continúa directamente al proceso de toma de signos vitales [CU-07]".
     * La cita queda en "Paciente Presente" con esEmergencia=true, que es
     * exactamente el estado que ahora lee el panel de Enfermería (CU-07):
     * ahí aparece en la cola, priorizada por ser emergencia, y al tomarle
     * los signos vitales con el indicador "¿Emergencia?" activado, esa
     * misma cita pasa directo a "Consulta Médica" sin volver a sala de
     * espera (CU-07, FA01). No hace falta ningún enlace especial aquí: el
     * flujo ya queda conectado únicamente por el estado de la cita.
     */
    @Transactional
    public RegistrarLlegadaResponse registrarEmergencia(RegistrarEmergenciaRequest request) {
        Usuario paciente = usuarioRepository.findByDpi(request.getDpi())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se encontró ningún paciente con ese DPI. Es necesario registrarlo antes de continuar."));

        if (!Rol.PACIENTE.equalsIgnoreCase(paciente.getRol().getNombreRol())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este DPI pertenece a un usuario del sistema interno, no a un paciente.");
        }

        Sucursal sucursal = sucursalRepository.findById(request.getIdSucursal())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede seleccionada no existe."));
        Especialidad especialidad = especialidadRepository.findById(request.getIdEspecialidad())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La especialidad seleccionada no existe."));

        List<MedicoDTO> disponibles = catalogoService.obtenerMedicosPorEspecialidad(
                request.getIdSucursal(), request.getIdEspecialidad());
        if (disponibles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay médicos disponibles para esa especialidad en la sede seleccionada.");
        }
        Usuario medico = usuarioRepository.findById(disponibles.get(0).getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "El médico asignado ya no existe."));

        String motivo = (request.getMotivoConsulta() == null || request.getMotivoConsulta().isBlank())
                ? "Atención de emergencia" : request.getMotivoConsulta().trim();
        // QA: la tabla citas exige motivo de al menos 10 caracteres (CHECK en la BD) y el
        // documento (RN-CU03-03) pide 10-2000. Antes un motivo corto como "dolor" llegaba
        // a la BD y devolvía un error 500 genérico.
        if (motivo.length() < 10 || motivo.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El motivo debe contener entre 10 y 2000 caracteres. Usted ingresó " + motivo.length() + " caracteres.");
        }

        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setSucursal(sucursal);
        cita.setEspecialidad(especialidad);
        cita.setFechaCita(LocalDate.now());
        cita.setHoraCita(LocalTime.now());
        cita.setMotivoConsulta(motivo);
        cita.setEstadoCita(buscarEstadoCita(EstadoCita.PACIENTE_PRESENTE));
        cita.setMontoTotal(montoConsulta);
        cita.setEsEmergencia(true);
        cita.setHoraLlegada(LocalDateTime.now());
        cita = citaRepository.save(cita);

        String mensaje = "Paciente " + paciente.getNombreCompleto()
                + " registrado con prioridad de EMERGENCIA. El paciente debe pasar directamente a toma de signos vitales.";

        return new RegistrarLlegadaResponse(true, mensaje,
                "EMERGENCIA — pasar directamente a signos vitales", true, convertirADTO(cita));
    }

    // =================================================================
    // FA07: reasignación de médico
    // =================================================================

    /** Trae el resumen de la cita y la lista de médicos candidatos (misma sede/especialidad, excluyendo al actual). */
    @Transactional(readOnly = true)
    public ReasignacionInfoDTO listarMedicosParaReasignacion(Integer idCita) {
        Cita cita = obtenerCitaReasignable(idCita);

        List<MedicoDTO> candidatos = catalogoService
                .obtenerMedicosPorEspecialidad(cita.getSucursal().getIdSucursal(), cita.getEspecialidad().getIdEspecialidad())
                .stream()
                .filter(m -> !m.getIdUsuario().equals(cita.getMedico().getIdUsuario()))
                .toList();

        return new ReasignacionInfoDTO(convertirADTO(cita), candidatos);
    }

    /**
     * CORREGIDO: ahora sí persiste la reasignación en "reasignaciones_medico"
     * (idUsuarioResponsable = quién hizo el cambio, tomado de la sesión).
     * Antes esa tabla no existía en la base de datos, así que la nota se
     * descartaba y no quedaba ningún rastro de auditoría del cambio.
     */
    @Transactional
    public String reasignarMedico(Integer idCita, ReasignarMedicoRequest request, Integer idUsuarioResponsable) {
        Cita cita = obtenerCitaReasignable(idCita);

        Usuario nuevoMedico = usuarioRepository.findById(request.getIdMedicoNuevo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El médico seleccionado no existe."));
        if (!Rol.MEDICO.equalsIgnoreCase(nuevoMedico.getRol().getNombreRol())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario seleccionado no es un médico.");
        }
        Usuario medicoAnterior = cita.getMedico();
        // QA: el documento (FA07) dice "médicos disponibles de la misma sede y especialidad".
        // Antes bastaba con que el usuario tuviera rol Médico: por API se podía asignar
        // cualquier médico, incluso el mismo o uno de otra sede/especialidad.
        boolean candidatoValido = catalogoService
                .obtenerMedicosPorEspecialidad(cita.getSucursal().getIdSucursal(), cita.getEspecialidad().getIdEspecialidad())
                .stream()
                .anyMatch(m -> m.getIdUsuario().equals(nuevoMedico.getIdUsuario()));
        if (!candidatoValido || nuevoMedico.getIdUsuario().equals(medicoAnterior.getIdUsuario())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El médico seleccionado debe ser distinto al actual y pertenecer a la misma sede y especialidad de la cita.");
        }
        Usuario responsable = usuarioRepository.findById(idUsuarioResponsable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar."));

        cita.setMedico(nuevoMedico);
        citaRepository.save(cita);

        ReasignacionMedico reasignacion = new ReasignacionMedico();
        reasignacion.setCita(cita);
        reasignacion.setMedicoAnterior(medicoAnterior);
        reasignacion.setMedicoNuevo(nuevoMedico);
        reasignacion.setNota(request.getNotaMotivo());
        reasignacion.setUsuarioResponsable(responsable);
        reasignacionMedicoRepository.save(reasignacion);

        return "Médico reasignado correctamente";
    }

    /** FA07, precondición: solo se puede reasignar en citas "Confirmada" o "Paciente Presente". */
    private Cita obtenerCitaReasignable(Integer idCita) {
        Cita cita = citaRepository.findById(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));
        String estado = cita.getEstadoCita().getNombreEstado();
        if (!EstadoCita.CONFIRMADA.equals(estado) && !EstadoCita.PACIENTE_PRESENTE.equals(estado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede reasignar médico en citas con estado 'Confirmada' o 'Paciente Presente'.");
        }
        return cita;
    }

    // =================================================================
    // Helpers
    // =================================================================

    private RecepcionCitaDTO convertirADTO(Cita cita) {
        return new RecepcionCitaDTO(
                cita.getIdCita(),
                cita.getPaciente().getIdUsuario(),
                cita.getPaciente().getNombreCompleto(),
                cita.getMedico().getIdUsuario(),
                cita.getMedico().getNombreCompleto(),
                cita.getEspecialidad().getNombre(),
                cita.getSucursal().getNombre(),
                cita.getFechaCita(),
                cita.getHoraCita(),
                cita.getMotivoConsulta(),
                cita.getEstadoCita().getNombreEstado(),
                Boolean.TRUE.equals(cita.getEsEmergencia()),
                cita.getHoraLlegada(),
                cita.getPrioridad() != null ? cita.getPrioridad().getNombre() : null
        );
    }

    private EstadoCita buscarEstadoCita(String nombre) {
        return estadoCitaRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de cita '" + nombre + "' no existe en el catálogo."));
    }
}
