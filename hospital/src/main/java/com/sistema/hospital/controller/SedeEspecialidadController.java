package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.SedeEspecialidadService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** CU-13: Especialidades por Sede. Solo el Administrador. */
@RestController
@RequestMapping("/api/sede-especialidad")
public class SedeEspecialidadController {

    private final SedeEspecialidadService sedeEspecialidadService;

    public SedeEspecialidadController(SedeEspecialidadService sedeEspecialidadService) {
        this.sedeEspecialidadService = sedeEspecialidadService;
    }

    @GetMapping
    public Page<SedeEspecialidadDTO> listar(@RequestParam(required = false) String id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            HttpSession session) {
        exigirAdministrador(session);
        return sedeEspecialidadService.listar(id, page, size);
    }

    @GetMapping("/catalogos")
    public Map<String, List<CatalogoItemDTO>> catalogos(HttpSession session) {
        exigirAdministrador(session);
        return Map.of(
                "sedes", sedeEspecialidadService.sedesActivas(),
                "especialidades", sedeEspecialidadService.especialidadesActivas());
    }

    @PostMapping
    public Map<String, Object> asignar(@Valid @RequestBody AsignarSedeEspecialidadRequest request, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        String mensaje = sedeEspecialidadService.asignar(request, admin.getIdUsuario());
        return Map.of("success", true, "mensaje", mensaje);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable Integer id, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        String mensaje = sedeEspecialidadService.eliminar(id, admin.getIdUsuario());
        return Map.of("success", true, "mensaje", mensaje);
    }

    private SesionUsuarioDTO exigirAdministrador(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        if (!Rol.ADMINISTRADOR.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este módulo.");
        }
        return usuario;
    }
}
