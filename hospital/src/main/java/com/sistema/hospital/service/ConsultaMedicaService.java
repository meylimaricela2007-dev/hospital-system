package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regla de diseño importante (ver también el comentario en Consulta.java y
 * EstadoCita.java): en esta base de datos NO existe un estado de cita
 * "Evaluado". La cita permanece en "Consulta Médica" desde que el médico
 * la inicia hasta que presiona "Finalizar Atención"; la sección del panel
 * "Evaluados — Pendiente de cierre" se calcula mirando el estado de la
 * Consulta asociada (consulta.estado == "Finalizada"), no un nuevo estado
 * de cita.
 */
@Service
public class ConsultaMedicaService {

    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private ConsultaRepository consultaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EstadoCitaRepository estadoCitaRepository;
    @Autowired
    private ExamenLaboratorioRepository examenLaboratorioRepository;
    @Autowired
    private EstadoOrdenLaboratorioRepository estadoOrdenLaboratorioRepository;
    @Autowired
    private OrdenLaboratorioRepository ordenLaboratorioRepository;
    @Autowired
    private MedicamentoRepository medicamentoRepository;
    @Autowired
    private RecetaMedicaRepository recetaMedicaRepository;

    // =================================================================
    // Paso 1 del flujo normal: panel del médico
    // =================================================================

    @Transactional(readOnly = true)
    public PanelMedicoResponse obtenerPanel(Integer idMedico) {
        List<Cita> citasEnEspera = citaRepository.findByMedico_IdUsuarioAndFechaCitaAndEstadoCita_NombreEstadoInOrderByHoraCitaAsc(
                idMedico, LocalDate.now(), List.of(EstadoCita.EN_ESPERA));

        List<Cita> citasEnConsultaMedica = citaRepository.findByMedico_IdUsuarioAndFechaCitaAndEstadoCita_NombreEstadoInOrderByHoraCitaAsc(
                idMedico, LocalDate.now(), List.of(EstadoCita.CONSULTA_MEDICA));

        // Dentro de "Consulta Médica" hay dos grupos: los que el médico
        // sigue llenando ("En curso" o sin consulta todavía) y los que ya
        // guardó como "Finalizada" (sección "Evaluados").
        List<CitaPanelMedicoDTO> enConsulta = citasEnConsultaMedica.stream()
                .filter(c -> !estaEvaluada(c))
                .map(this::convertirAPanelDTO)
                .toList();
        List<CitaPanelMedicoDTO> evaluados = citasEnConsultaMedica.stream()
                .filter(this::estaEvaluada)
                .map(this::convertirAPanelDTO)
                .toList();

        return new PanelMedicoResponse(
                citasEnEspera.stream().map(this::convertirAPanelDTO).toList(),
                enConsulta,
                evaluados
        );
    }

    private boolean estaEvaluada(Cita cita) {
        return consultaRepository.findByCita_IdCita(cita.getIdCita())
                .map(c -> Consulta.FINALIZADA.equals(c.getEstado()))
                .orElse(false);
    }

    private CitaPanelMedicoDTO convertirAPanelDTO(Cita cita) {
        Integer idConsulta = consultaRepository.findByCita_IdCita(cita.getIdCita())
                .map(Consulta::getIdConsulta)
                .orElse(null);
        return new CitaPanelMedicoDTO(
                cita.getIdCita(),
                cita.getPaciente().getIdUsuario(),
                cita.getPaciente().getNombreCompleto(),
                cita.getEspecialidad().getNombre(),
                cita.getFechaCita(),
                cita.getHoraCita(),
                cita.getEstadoCita().getNombreEstado(),
                Boolean.TRUE.equals(cita.getEsEmergencia()),
                idConsulta
        );
    }

    // =================================================================
    // Paso 2: iniciar consulta (llamado con TTS, igual que CU-07)
    // =================================================================

    @Transactional
    public IniciarConsultaResponse iniciarConsulta(Integer idCita, Integer idMedico) {
        Cita cita = obtenerCitaDelMedico(idCita, idMedico);

        if (!EstadoCita.EN_ESPERA.equals(cita.getEstadoCita().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta cita ya no se encuentra en estado 'En Espera'.");
        }

        cita.setEstadoCita(buscarEstadoCita(EstadoCita.CONSULTA_MEDICA));
        citaRepository.save(cita);

        String texto = "Turno número " + cita.getIdCita() + ". Paciente " + cita.getPaciente().getNombreCompleto()
                + ", favor pasar a consulta médica.";
        return new IniciarConsultaResponse(texto);
    }

    // =================================================================
    // FA06: paciente no se presenta cuando es llamado
    // =================================================================

    @Transactional
    public String marcarNoAsistio(Integer idCita, Integer idMedico) {
        Cita cita = obtenerCitaDelMedico(idCita, idMedico);

        if (!EstadoCita.EN_ESPERA.equals(cita.getEstadoCita().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede marcar 'No Asistió' mientras la cita está 'En Espera'.");
        }

        cita.setEstadoCita(buscarEstadoCita(EstadoCita.NO_ASISTIO));
        citaRepository.save(cita);
        return "Cita #" + cita.getIdCita() + " marcada como No Asistió.";
    }

    // =================================================================
    // Paso 3-4: formulario de consulta
    // =================================================================

    @Transactional(readOnly = true)
    public ContextoConsultaDTO obtenerContexto(Integer idCita, Integer idMedico) {
        Cita cita = obtenerCitaEnConsulta(idCita, idMedico);
        Optional<Consulta> existente = consultaRepository.findByCita_IdCita(idCita);

        return new ContextoConsultaDTO(
                cita.getIdCita(),
                cita.getPaciente().getNombreCompleto(),
                Boolean.TRUE.equals(cita.getEsEmergencia()),
                existente.map(Consulta::getMotivoVisita).orElse(null),
                existente.map(Consulta::getHallazgosClinicos).orElse(null),
                existente.map(Consulta::getCodigoCie10).orElse(null),
                existente.map(Consulta::getDiagnostico).orElse(null),
                existente.map(Consulta::getPlanTratamiento).orElse(null),
                existente.map(Consulta::getNotasAdicionales).orElse(null),
                existente.map(Consulta::getEstado).orElse(null)
        );
    }

    /**
     * Paso 9 del flujo normal / FA05 "Intento de finalizar sin diagnóstico".
     * IMPORTANTE: guardar como "Finalizada" NO mueve la cita a otro estado
     * de cita (sigue en "Consulta Médica"); solo cambia consulta.estado, que
     * es lo que hace que la tarjeta pase de la sección "En Consulta Médica"
     * a "Evaluados — Pendiente de cierre" en el panel (ver obtenerPanel()).
     */
    @Transactional
    public GuardarConsultaResponse guardarConsulta(Integer idCita, Integer idMedico, GuardarConsultaRequest request) {
        Cita cita = obtenerCitaEnConsulta(idCita, idMedico);

        boolean finalizando = Consulta.FINALIZADA.equals(request.getEstadoConsulta());
        if (finalizando && (request.getDiagnostico() == null || request.getDiagnostico().isBlank())) {
            // FA05: mensaje EXACTO del documento
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No es posible finalizar la consulta sin registrar un diagnóstico. El campo Diagnóstico es obligatorio.");
        }

        Consulta consulta = consultaRepository.findByCita_IdCita(idCita).orElseGet(() -> {
            Consulta nueva = new Consulta();
            nueva.setCita(cita);
            nueva.setMedico(cita.getMedico());
            return nueva;
        });

        consulta.setMotivoVisita(request.getMotivoVisita());
        consulta.setHallazgosClinicos(request.getHallazgosClinicos());
        consulta.setCodigoCie10(request.getCodigoCie10() == null || request.getCodigoCie10().isBlank() ? null : request.getCodigoCie10());
        consulta.setDiagnostico(request.getDiagnostico());
        consulta.setPlanTratamiento(request.getPlanTratamiento());
        consulta.setNotasAdicionales(request.getNotasAdicionales());
        consulta.setEstado(request.getEstadoConsulta());
        if (finalizando) {
            consulta.setFechaFin(java.time.LocalDateTime.now());
        }
        consultaRepository.save(consulta);

        String mensaje = finalizando
                ? "La consulta ha sido finalizada exitosamente. El paciente puede proceder a las siguientes indicaciones médicas."
                : "La consulta quedó guardada como 'En curso'. Puede continuarla más tarde desde este mismo panel.";

        return new GuardarConsultaResponse(mensaje, consulta.getEstado());
    }

    // =================================================================
    // FA01: orden de laboratorio (solo desde "Evaluados")
    // =================================================================

    @Transactional
    public OrdenLaboratorioResponse generarOrdenLaboratorio(Integer idCita, Integer idMedico, GenerarOrdenLaboratorioRequest request) {
        Cita cita = obtenerCitaDelMedico(idCita, idMedico);
        Consulta consulta = obtenerConsultaFinalizada(idCita);

        // QA: se quitan ids repetidos (antes, repetir un examen daba "no existen" por comparar
        // tamaños) y se rechazan exámenes dados de baja en el catálogo (estado inactivo).
        List<Integer> idsUnicos = request.getIdsExamenes().stream().distinct().toList();
        List<ExamenLaboratorio> examenes = examenLaboratorioRepository.findAllById(idsUnicos);
        if (examenes.size() != idsUnicos.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o más exámenes seleccionados no existen.");
        }
        if (examenes.stream().anyMatch(e -> !Boolean.TRUE.equals(e.getEstado()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o más exámenes seleccionados ya no están disponibles.");
        }

        BigDecimal montoTotal = examenes.stream().map(ExamenLaboratorio::getPrecio).reduce(BigDecimal.ZERO, BigDecimal::add);

        OrdenLaboratorio orden = new OrdenLaboratorio();
        orden.setNumeroOrden("LAB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        orden.setConsulta(consulta);
        orden.setPaciente(cita.getPaciente());
        orden.setMedico(cita.getMedico());
        orden.setEstadoOrden(buscarEstadoOrden(EstadoOrdenLaboratorio.PENDIENTE));
        orden.setEsExterna(Boolean.TRUE.equals(request.getEsExterna())); // CU-09, FA01
        orden.setNotas(request.getObservaciones());
        orden.setMontoTotal(montoTotal);

        for (ExamenLaboratorio examen : examenes) {
            DetalleOrdenExamen detalle = new DetalleOrdenExamen();
            detalle.setOrden(orden);
            detalle.setExamen(examen);
            detalle.setMonto(examen.getPrecio());
            orden.getDetalles().add(detalle);
        }
        ordenLaboratorioRepository.save(orden);

        List<String> nombresExamenes = examenes.stream().map(ExamenLaboratorio::getNombre).toList();
        // FA01, paso 6: mensaje EXACTO del documento
        String mensaje = "Orden de laboratorio generada exitosamente. Número de orden: " + orden.getNumeroOrden()
                + ". Exámenes: " + String.join(", ", nombresExamenes) + ". El paciente debe dirigirse al área de laboratorio.";

        return new OrdenLaboratorioResponse(mensaje, orden.getNumeroOrden(), nombresExamenes, montoTotal);
    }

    // =================================================================
    // FA04: receta médica (solo desde "Evaluados")
    // =================================================================

    @Transactional
    public RecetaResponse generarReceta(Integer idCita, Integer idMedico, GenerarRecetaRequest request) {
        obtenerCitaDelMedico(idCita, idMedico);
        Consulta consulta = obtenerConsultaFinalizada(idCita);

        RecetaMedica receta = new RecetaMedica();
        receta.setConsulta(consulta);
        receta.setNotas(request.getNotas());

        List<String> resumen = new java.util.ArrayList<>();
        for (DetalleRecetaRequest item : request.getMedicamentos()) {
            Medicamento medicamento = medicamentoRepository.findById(item.getIdMedicamento())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno de los medicamentos seleccionados no existe."));

            DetalleReceta detalle = new DetalleReceta();
            detalle.setReceta(receta);
            detalle.setMedicamento(medicamento);
            detalle.setDosis(item.getDosis());
            detalle.setFrecuencia(item.getFrecuencia());
            detalle.setDuracion(item.getDuracion());
            detalle.setIndicaciones(item.getIndicaciones());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(medicamento.getPrecio());
            receta.getDetalles().add(detalle);

            resumen.add(medicamento.getNombre() + " (" + item.getDosis() + ", " + item.getFrecuencia() + ")");
        }
        recetaMedicaRepository.save(receta);

        // FA04, paso 6: mensaje EXACTO del documento
        String mensaje = "Receta médica generada exitosamente. Medicamentos: " + String.join(", ", resumen)
                + ". El paciente puede adquirirlos en la farmacia de la clínica.";

        return new RecetaResponse(mensaje, receta.getIdReceta(), resumen);
    }

    // =================================================================
    // Paso 11: finalizar atención (solo desde "Evaluados")
    // =================================================================

    @Transactional
    public String finalizarAtencion(Integer idCita, Integer idMedico) {
        Cita cita = obtenerCitaDelMedico(idCita, idMedico);
        obtenerConsultaFinalizada(idCita); // valida que ya esté en "Evaluados"

        cita.setEstadoCita(buscarEstadoCita(EstadoCita.ATENCION_FINALIZADA));
        citaRepository.save(cita);
        return "Atención finalizada para cita #" + cita.getIdCita() + ".";
    }

    // =================================================================
    // Helpers
    // =================================================================

    /** Cualquier cita del médico en sesión, sin importar su estado (para No Asistió / iniciar). */
    private Cita obtenerCitaDelMedico(Integer idCita, Integer idMedico) {
        Cita cita = citaRepository.findById(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));
        if (!cita.getMedico().getIdUsuario().equals(idMedico)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta cita no está asignada a su usuario.");
        }
        return cita;
    }

    /** La cita debe estar en "Consulta Médica" (cualquiera de las dos subsecciones). */
    private Cita obtenerCitaEnConsulta(Integer idCita, Integer idMedico) {
        Cita cita = obtenerCitaDelMedico(idCita, idMedico);
        if (!EstadoCita.CONSULTA_MEDICA.equals(cita.getEstadoCita().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta cita no se encuentra en estado 'Consulta Médica'.");
        }
        return cita;
    }

    /** FA01/FA04/finalizar: exige que la consulta ya esté guardada como "Finalizada" (sección "Evaluados"). */
    private Consulta obtenerConsultaFinalizada(Integer idCita) {
        Consulta consulta = consultaRepository.findByCita_IdCita(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Debe guardar la consulta como 'Finalizada' antes de continuar."));
        if (!Consulta.FINALIZADA.equals(consulta.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Debe guardar la consulta como 'Finalizada' antes de continuar.");
        }
        return consulta;
    }

    private EstadoCita buscarEstadoCita(String nombre) {
        return estadoCitaRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de cita '" + nombre + "' no existe en el catálogo."));
    }

    private EstadoOrdenLaboratorio buscarEstadoOrden(String nombre) {
        return estadoOrdenLaboratorioRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de orden '" + nombre + "' no existe en el catálogo."));
    }
}
