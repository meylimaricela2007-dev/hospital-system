package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.BitacoraInventarioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

/** CU-15: Bitácora de Movimientos de Inventario. Personal de farmacia y Administrador. */
@RestController
@RequestMapping("/api/inventario")
public class BitacoraInventarioController {

    private final BitacoraInventarioService service;

    public BitacoraInventarioController(BitacoraInventarioService service) {
        this.service = service;
    }

    @GetMapping("/movimientos")
    public Page<MovimientoInventarioDTO> listar(@RequestParam(required = false) Integer idTipo,
                                                @RequestParam(required = false) Integer idMedicamento,
                                                @RequestParam(required = false) Integer idSucursal,
                                                @RequestParam(required = false) String referencia,
                                                @RequestParam(required = false) String usuario,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                HttpSession session) {
        SesionUsuarioDTO u = exigirAcceso(session);
        return service.listar(idTipo, idMedicamento, idSucursal, referencia, usuario, desde, hasta, page, size, u.getIdUsuario());
    }

    @GetMapping("/movimientos/{id}")
    public MovimientoInventarioDTO detalle(@PathVariable Integer id, HttpSession session) {
        return service.obtener(id, exigirAcceso(session).getIdUsuario());
    }

    @GetMapping("/resumen-mensual")
    public ResumenMensualInventarioDTO resumen(@RequestParam(required = false) Integer anio,
                                               @RequestParam(required = false) Integer mes,
                                               HttpSession session) {
        return service.resumenMensual(anio, mes, exigirAcceso(session).getIdUsuario());
    }

    @GetMapping("/catalogos")
    public MovimientoCatalogosDTO catalogos(HttpSession session) {
        return service.catalogos(exigirAcceso(session).getIdUsuario());
    }

    /** Panel informativo del formulario: stock actual y mínimo de medicamento + sucursal. */
    @GetMapping("/info")
    public InventarioInfoDTO info(@RequestParam Integer idMedicamento, @RequestParam Integer idSucursal, HttpSession session) {
        return service.info(idMedicamento, idSucursal, exigirAcceso(session).getIdUsuario());
    }

    @PostMapping("/movimientos")
    public Map<String, Object> registrar(@Valid @RequestBody RegistrarMovimientoRequest request, HttpSession session) {
        return service.registrar(request, exigirAcceso(session).getIdUsuario());
    }

    @PatchMapping("/movimientos/{id}/estado")
    public Map<String, Object> cambiarEstado(@PathVariable Integer id, HttpSession session) {
        String mensaje = service.cambiarEstado(id, exigirAcceso(session).getIdUsuario());
        return Map.of("success", true, "mensaje", mensaje);
    }

    private SesionUsuarioDTO exigirAcceso(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        if (!Rol.FARMACEUTICO.equals(usuario.getRolNombre()) && !Rol.ADMINISTRADOR.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este módulo.");
        }
        return usuario;
    }
}
