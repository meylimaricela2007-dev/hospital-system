package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.LaboratorioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-09: Gestión de Laboratorio.
 * Consultar órdenes: Laboratorista o Administrador.
 * Registrar / publicar resultados: solo Laboratorista.
 */
@RestController
@RequestMapping("/api/laboratorio")
public class LaboratorioController {

    private final LaboratorioService laboratorioService;

    public LaboratorioController(LaboratorioService laboratorioService) {
        this.laboratorioService = laboratorioService;
    }

    /** Paso 1: tabla de órdenes con filtros (estado, paciente, médico) y paginación. */
    @GetMapping("/ordenes")
    public Page<OrdenLaboratorioListaDTO> listar(@RequestParam(required = false) String estado,
                                                 @RequestParam(required = false) String paciente,
                                                 @RequestParam(required = false) String medico,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 HttpSession session) {
        exigirConsulta(session);
        return laboratorioService.listarOrdenes(estado, paciente, medico, page, size);
    }

    /** Pasos 2-3: detalle de una orden con sus exámenes. */
    @GetMapping("/ordenes/{idOrden}")
    public OrdenLaboratorioDetalleDTO detalle(@PathVariable Integer idOrden, HttpSession session) {
        exigirConsulta(session);
        return laboratorioService.obtenerDetalle(idOrden);
    }

    /** Pasos 9-10 / FA02: guardar el resultado de un examen. */
    @PostMapping("/ordenes/{idOrden}/examenes/{idDetalle}/resultado")
    public ResultadoLaboratorioResponse guardarResultado(@PathVariable Integer idOrden,
                                                         @PathVariable Integer idDetalle,
                                                         @Valid @RequestBody GuardarResultadoRequest request,
                                                         HttpSession session) {
        exigirLaboratorista(session);
        return laboratorioService.guardarResultado(idOrden, idDetalle, request);
    }

    /** Pasos 11-12: publicar el resultado de UN examen (publicación individual). */
    @PostMapping("/ordenes/{idOrden}/examenes/{idDetalle}/publicar")
    public ResultadoLaboratorioResponse publicar(@PathVariable Integer idOrden,
                                                 @PathVariable Integer idDetalle,
                                                 HttpSession session) {
        exigirLaboratorista(session);
        return laboratorioService.publicarResultado(idOrden, idDetalle);
    }

    private SesionUsuarioDTO exigirSesion(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        return usuario;
    }

    private void exigirConsulta(HttpSession session) {
        String rol = exigirSesion(session).getRolNombre();
        if (!Rol.LABORATORISTA.equals(rol) && !Rol.ADMINISTRADOR.equals(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este módulo.");
        }
    }

    private void exigirLaboratorista(HttpSession session) {
        if (!Rol.LABORATORISTA.equals(exigirSesion(session).getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el personal de laboratorio puede registrar o publicar resultados.");
        }
    }
}
