package com.sistema.hospital.controller;

import com.sistema.hospital.dto.SesionUsuarioDTO;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.CatalogoAdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** CU-14: Mantenimiento de Catálogos del Sistema. Solo el Administrador. */
@RestController
@RequestMapping("/api/admin/catalogos")
public class CatalogoAdminController {

    private final CatalogoAdminService service;

    public CatalogoAdminController(CatalogoAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> indice(HttpSession session) {
        exigirAdministrador(session);
        return service.indice();
    }

    /** Columnas y campos del catálogo: el frontend dibuja la tabla y el formulario con esto. */
    @GetMapping("/{clave}/meta")
    public Map<String, Object> meta(@PathVariable String clave, HttpSession session) {
        exigirAdministrador(session);
        return service.meta(clave);
    }

    @GetMapping("/{clave}")
    public Page<Map<String, Object>> listar(@PathVariable String clave,
                                            @RequestParam(required = false) String texto,
                                            @RequestParam(required = false) String estado,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            HttpSession session) {
        exigirAdministrador(session);
        return service.listar(clave, texto, estado, page, size);
    }

    @GetMapping("/{clave}/{id}")
    public Map<String, Object> obtener(@PathVariable String clave, @PathVariable Integer id, HttpSession session) {
        exigirAdministrador(session);
        return service.obtener(clave, id);
    }

    @PostMapping("/{clave}")
    public Map<String, Object> crear(@PathVariable String clave, @RequestBody Map<String, Object> datos, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        return Map.of("success", true, "mensaje", service.crear(clave, datos, admin.getIdUsuario()));
    }

    @PutMapping("/{clave}/{id}")
    public Map<String, Object> actualizar(@PathVariable String clave, @PathVariable Integer id,
                                          @RequestBody Map<String, Object> datos, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        return Map.of("success", true, "mensaje", service.actualizar(clave, id, datos, admin.getIdUsuario()));
    }

    @DeleteMapping("/{clave}/{id}")
    public Map<String, Object> eliminar(@PathVariable String clave, @PathVariable Integer id, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        return Map.of("success", true, "mensaje", service.eliminar(clave, id, admin.getIdUsuario()));
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
