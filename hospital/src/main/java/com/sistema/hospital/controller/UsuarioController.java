package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.service.UsuarioService;
import com.sistema.hospital.model.Rol;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // CU-00, pasos 4-8: verificar DPI
    @GetMapping("/verificar-dpi")
    public VerificarDpiResponse verificarDpi(@RequestParam String dpi) {
        if (dpi == null || !dpi.matches("\\d{13}")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El DPI debe contener exactamente 13 dígitos numéricos.");
        }
        return usuarioService.verificarDpi(dpi);
    }

    // CU-01, pasos 4-7: listado con filtro y paginación
    @GetMapping("/listar")
    public Page<UsuarioDTO> listar(@RequestParam(required = false) String filtro,
                                    @RequestParam(required = false) String valor,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    HttpSession session) {
        exigirAdministrador(session);
        return usuarioService.listarUsuarios(filtro, valor, page, size);
    }

    @GetMapping("/catalogos")
    public CatalogosUsuarioResponse catalogos(HttpSession session) {
        exigirAdministrador(session);
        return usuarioService.obtenerCatalogos();
    }

    // CU-01, FA04 paso 2: precargar el formulario de edición
    @GetMapping("/{id}")
    public UsuarioDetalleDTO detalle(@PathVariable Integer id, HttpSession session) {
        exigirAdministrador(session);
        return usuarioService.obtenerDetalle(id);
    }

    // CU-01, FA01: crear usuario
    @PostMapping
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioFormularioRequest request, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request, admin.getIdUsuario()));
    }

    // Actualizar usuario
    @PutMapping("/{id}")
    public UsuarioDTO editar(@PathVariable Integer id, @Valid @RequestBody UsuarioFormularioRequest request, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        return usuarioService.editar(id, request, admin.getIdUsuario());
    }

    // CU-01, FA05: eliminar usuario (confirmado desde el diálogo modal)
    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable Integer id, HttpSession session) {
        SesionUsuarioDTO admin = exigirAdministrador(session);
        if (admin.getIdUsuario().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puede eliminar su propio usuario mientras tiene la sesión iniciada.");
        }
        String nombreUsuario = usuarioService.eliminar(id, admin.getIdUsuario());
        return Map.of(
                "success", true,
                "mensaje", "El usuario " + nombreUsuario + " ha sido eliminado correctamente."
        );
    }

    /**
     * QA: antes ningún endpoint de este controlador exigía sesión ni rol, así que cualquiera
     * (incluso sin iniciar sesión) podía listar, crear, editar o eliminar usuarios, por
     * ejemplo crear un Administrador con curl. Ahora solo el Administrador (RN-GLOBAL-007).
     * /verificar-dpi sigue siendo público porque lo usa el portal (CU-00).
     */
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
