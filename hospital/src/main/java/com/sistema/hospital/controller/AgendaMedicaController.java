package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.AgendaMedicaService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** CU-16: Gestión de Agenda Médica. Solo el rol Médico, siempre sobre su propia agenda. */
@RestController
@RequestMapping("/api/agenda")
public class AgendaMedicaController {

    private final AgendaMedicaService service;

    public AgendaMedicaController(AgendaMedicaService service) {
        this.service = service;
    }

    @GetMapping("/calendario")
    public List<AgendaItemDTO> calendario(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                          HttpSession session) {
        return service.calendario(exigirMedico(session).getIdUsuario(), desde, hasta);
    }

    @GetMapping("/catalogos")
    public Map<String, Object> catalogos(HttpSession session) {
        exigirMedico(session);
        return service.catalogos();
    }

    @PostMapping("/eventos")
    public Map<String, Object> crearEvento(@Valid @RequestBody EventoAgendaRequest request, HttpSession session) {
        return Map.of("success", true, "mensaje", service.crearEvento(request, exigirMedico(session).getIdUsuario()));
    }

    @PutMapping("/eventos/{id}")
    public Map<String, Object> actualizarEvento(@PathVariable Integer id, @Valid @RequestBody EventoAgendaRequest request,
                                                HttpSession session) {
        return Map.of("success", true, "mensaje", service.actualizarEvento(id, request, exigirMedico(session).getIdUsuario()));
    }

    @DeleteMapping("/eventos/{id}")
    public Map<String, Object> eliminarEvento(@PathVariable Integer id, HttpSession session) {
        return Map.of("success", true, "mensaje", service.eliminarEvento(id, exigirMedico(session).getIdUsuario()));
    }

    @GetMapping("/tareas")
    public TareasMedicoResponse tareas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                       @RequestParam(defaultValue = "pendientes") String filtro,
                                       HttpSession session) {
        return service.tareas(exigirMedico(session).getIdUsuario(), fecha, filtro);
    }

    @PostMapping("/tareas")
    public Map<String, Object> crearTarea(@Valid @RequestBody TareaMedicoRequest request, HttpSession session) {
        return Map.of("success", true, "mensaje", service.crearTarea(request, exigirMedico(session).getIdUsuario()));
    }

    @PatchMapping("/tareas/{id}/completada")
    public TareaMedicoDTO cambiarCompletada(@PathVariable Integer id, HttpSession session) {
        return service.cambiarCompletada(id, exigirMedico(session).getIdUsuario());
    }

    @DeleteMapping("/tareas/{id}")
    public Map<String, Object> eliminarTarea(@PathVariable Integer id, HttpSession session) {
        return Map.of("success", true, "mensaje", service.eliminarTarea(id, exigirMedico(session).getIdUsuario()));
    }

    private SesionUsuarioDTO exigirMedico(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        if (!Rol.MEDICO.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el personal médico puede acceder a la agenda.");
        }
        return usuario;
    }
}
