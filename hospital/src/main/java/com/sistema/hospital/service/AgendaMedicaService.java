package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CU-16: Gestión de Agenda Médica.
 * El médico ve en un calendario sus citas programadas y sus eventos
 * personales, y administra sus tareas. Mientras un evento está vigente, el
 * médico NO aparece con ese horario disponible al agendar (ver CitaService).
 */
@Service
public class AgendaMedicaService {

    /** Citas que no se muestran en el calendario: aún no confirmadas o que ya no van a ocurrir. */
    private static final List<String> ESTADOS_OCULTOS = List.of(
            EstadoCita.PENDIENTE_DE_PAGO, EstadoCita.EXPIRADA, EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    /** Color de los eventos (RN-CU14-01: no es configurable por el usuario). */
    public static final String COLOR_EVENTO = "#8b5cf6";

    private static final Map<String, String> COLOR_CITA = Map.of(
            EstadoCita.CONFIRMADA, "#2563eb",
            EstadoCita.PACIENTE_PRESENTE, "#0d9488",
            EstadoCita.SIGNOS_VITALES, "#0891b2",
            EstadoCita.EN_ESPERA, "#d97706",
            EstadoCita.CONSULTA_MEDICA, "#ea580c",
            EstadoCita.ATENCION_FINALIZADA, "#16a34a");

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MINUTOS_CITA_POR_DEFECTO = 30;

    @Autowired
    private EventoAgendaRepository eventoRepository;
    @Autowired
    private TareaMedicoRepository tareaRepository;
    @Autowired
    private TipoEventoAgendaRepository tipoEventoRepository;
    @Autowired
    private PrioridadRepository prioridadRepository;
    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------- calendario

    /** Pasos 1-2: citas (coloreadas por estado) y eventos (violeta) dentro del rango visible del calendario. */
    @Transactional(readOnly = true)
    public List<AgendaItemDTO> calendario(Integer idMedico, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango de fechas no es válido.");
        }
        List<AgendaItemDTO> items = new ArrayList<>();

        for (EventoAgenda e : eventoRepository.buscarEnRango(idMedico, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay())) {
            items.add(new AgendaItemDTO("evento", e.getIdEvento(), e.getTitulo(), e.getFechaInicio(), e.getFechaFin(),
                    Boolean.TRUE.equals(e.getTodoElDia()), COLOR_EVENTO, e.getDescripcion(),
                    e.getTipoEvento().getIdTipoEvento(), e.getTipoEvento().getNombre(), null, null));
        }

        for (Cita c : citaRepository.findByMedico_IdUsuarioAndFechaCitaBetweenAndEstadoCita_NombreEstadoNotInOrderByFechaCitaAscHoraCitaAsc(
                idMedico, desde, hasta, ESTADOS_OCULTOS)) {
            LocalDateTime inicio = LocalDateTime.of(c.getFechaCita(), c.getHoraCita());
            LocalTime horaFin = c.getHorario() != null && c.getHorario().getHoraFin() != null
                    ? c.getHorario().getHoraFin() : c.getHoraCita().plusMinutes(MINUTOS_CITA_POR_DEFECTO);
            LocalDateTime fin = LocalDateTime.of(c.getFechaCita(), horaFin);
            if (!fin.isAfter(inicio)) {
                fin = inicio.plusMinutes(MINUTOS_CITA_POR_DEFECTO);
            }
            String estado = c.getEstadoCita().getNombreEstado();
            items.add(new AgendaItemDTO("cita", c.getIdCita(), "Cita · " + c.getPaciente().getNombreCompleto(), inicio, fin,
                    false, COLOR_CITA.getOrDefault(estado, "#64748b"), c.getMotivoConsulta(),
                    null, null, c.getPaciente().getNombreCompleto(), estado));
        }
        return items;
    }

    // ---------------------------------------------------------------- eventos

    @Transactional(readOnly = true)
    public Map<String, Object> catalogos() {
        return Map.of(
                "tiposEvento", tipoEventoRepository.findAll().stream()
                        .map(t -> new CatalogoItemDTO(t.getIdTipoEvento(), t.getNombre())).toList(),
                "prioridades", prioridadRepository.findAll().stream()
                        .map(p -> new CatalogoItemDTO(p.getIdPrioridad(), p.getNombre())).toList());
    }

    @Transactional
    public String crearEvento(EventoAgendaRequest r, Integer idMedico) {
        EventoAgenda evento = new EventoAgenda();
        evento.setMedico(medico(idMedico));
        aplicar(evento, r, true);
        eventoRepository.save(evento);
        // Paso 8: formato toLocaleDateString("es-GT") → dd/mm/yyyy
        return "Evento creado exitosamente. " + evento.getTitulo() + " — "
                + evento.getFechaInicio().toLocalDate().format(FORMATO_FECHA) + " a "
                + evento.getFechaFin().toLocalDate().format(FORMATO_FECHA) + ".";
    }

    @Transactional
    public String actualizarEvento(Integer id, EventoAgendaRequest r, Integer idMedico) {
        EventoAgenda evento = eventoPropio(id, idMedico);
        // La regla "fecha actual o futura" solo aplica si el médico cambia la fecha de inicio
        boolean cambioInicio = !normalizarInicio(r).equals(evento.getFechaInicio());
        aplicar(evento, r, cambioInicio);
        eventoRepository.save(evento);
        return "Evento actualizado exitosamente.";
    }

    @Transactional
    public String eliminarEvento(Integer id, Integer idMedico) {
        eventoRepository.delete(eventoPropio(id, idMedico));
        return "Evento eliminado exitosamente.";
    }

    private LocalDateTime normalizarInicio(EventoAgendaRequest r) {
        return Boolean.TRUE.equals(r.getTodoElDia()) ? r.getFechaInicio().toLocalDate().atStartOfDay() : r.getFechaInicio();
    }

    /** RN-CU14-01. Con "todo el día" el evento va de las 00:00 del primer día a las 23:59:59 del último. */
    private void aplicar(EventoAgenda evento, EventoAgendaRequest r, boolean validarInicioFuturo) {
        boolean todoElDia = Boolean.TRUE.equals(r.getTodoElDia());
        LocalDateTime inicio = normalizarInicio(r);
        LocalDateTime fin;
        if (todoElDia) {
            if (r.getFechaFin().toLocalDate().isBefore(inicio.toLocalDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la fecha de inicio.");
            }
            fin = r.getFechaFin().toLocalDate().atTime(23, 59, 59);
        } else {
            fin = r.getFechaFin();
            if (!fin.isAfter(inicio)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la fecha de inicio.");
            }
        }
        if (validarInicioFuturo && inicio.toLocalDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio debe ser la fecha actual o una fecha futura.");
        }
        TipoEventoAgenda tipo = tipoEventoRepository.findById(r.getIdTipoEvento())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar el tipo de evento."));

        evento.setTitulo(r.getTitulo().trim());
        evento.setDescripcion(r.getDescripcion() == null || r.getDescripcion().isBlank() ? null : r.getDescripcion().trim());
        evento.setFechaInicio(inicio);
        evento.setFechaFin(fin);
        evento.setTipoEvento(tipo);
        evento.setTodoElDia(todoElDia);
    }

    private EventoAgenda eventoPropio(Integer id, Integer idMedico) {
        EventoAgenda evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El evento no existe."));
        if (!evento.getMedico().getIdUsuario().equals(idMedico)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El evento no pertenece a su agenda.");
        }
        return evento;
    }

    // ---------------------------------------------------------------- tareas

    /** FA06: tareas con fecha límite en el día elegido (o todas si fecha es null), filtradas por estado. */
    @Transactional(readOnly = true)
    public TareasMedicoResponse tareas(Integer idMedico, LocalDate fecha, String filtro) {
        List<TareaMedico> base = fecha == null
                ? tareaRepository.findByMedico_IdUsuarioOrderByFechaLimiteAscIdTareaAsc(idMedico)
                : tareaRepository.findByMedico_IdUsuarioAndFechaLimiteOrderByIdTareaAsc(idMedico, fecha);

        int completadas = (int) base.stream().filter(t -> Boolean.TRUE.equals(t.getCompletada())).count();
        List<TareaMedicoDTO> visibles = base.stream()
                .filter(t -> switch (filtro == null ? "pendientes" : filtro) {
                    case "completadas" -> Boolean.TRUE.equals(t.getCompletada());
                    case "todas" -> true;
                    default -> !Boolean.TRUE.equals(t.getCompletada());
                })
                .map(AgendaMedicaService::convertir)
                .toList();
        return new TareasMedicoResponse(visibles, base.size(), base.size() - completadas, completadas);
    }

    @Transactional
    public String crearTarea(TareaMedicoRequest r, Integer idMedico) {
        if (r.getFechaLimite().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha límite no puede ser anterior a hoy.");
        }
        Prioridad prioridad = prioridadRepository.findById(r.getIdPrioridad())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar la prioridad."));
        TareaMedico t = new TareaMedico();
        t.setMedico(medico(idMedico));
        t.setTitulo(r.getTitulo().trim());
        t.setDescripcion(r.getDescripcion() == null || r.getDescripcion().isBlank() ? null : r.getDescripcion().trim());
        t.setPrioridad(prioridad);
        t.setFechaLimite(r.getFechaLimite());
        t.setCompletada(false);
        tareaRepository.save(t);
        return "Tarea creada exitosamente.";
    }

    @Transactional
    public TareaMedicoDTO cambiarCompletada(Integer id, Integer idMedico) {
        TareaMedico t = tareaPropia(id, idMedico);
        t.setCompletada(!Boolean.TRUE.equals(t.getCompletada()));
        return convertir(tareaRepository.save(t));
    }

    @Transactional
    public String eliminarTarea(Integer id, Integer idMedico) {
        tareaRepository.delete(tareaPropia(id, idMedico));
        return "Tarea eliminada exitosamente.";
    }

    private TareaMedico tareaPropia(Integer id, Integer idMedico) {
        TareaMedico t = tareaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La tarea no existe."));
        if (!t.getMedico().getIdUsuario().equals(idMedico)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La tarea no pertenece a su agenda.");
        }
        return t;
    }

    private Usuario medico(Integer idMedico) {
        return usuarioRepository.findById(idMedico)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar."));
    }

    private static TareaMedicoDTO convertir(TareaMedico t) {
        return new TareaMedicoDTO(t.getIdTarea(), t.getTitulo(), t.getDescripcion(),
                t.getPrioridad().getIdPrioridad(), t.getPrioridad().getNombre(),
                t.getFechaLimite(), Boolean.TRUE.equals(t.getCompletada()));
    }
}
