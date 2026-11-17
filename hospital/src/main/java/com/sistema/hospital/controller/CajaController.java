package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.CajaService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-06: Cobro de Consulta en Caja. Igual que Recepción (CU-05), todos los
 * endpoints exigen sesión de personal interno: los usa el Cajero desde su
 * ventanilla, nunca el paciente desde su propia cuenta.
 */
@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    /**
     * Paso 1 del flujo normal / FA02.
     * tipo=CITA -> valor es el número de cita
     * tipo=DPI  -> valor es el DPI del paciente
     */
    @GetMapping("/buscar")
    public BusquedaCajaResponse buscar(@RequestParam String tipo, @RequestParam String valor, HttpSession session) {
        exigirPersonalInterno(session);

        if ("CITA".equalsIgnoreCase(tipo)) {
            Integer idCita;
            try {
                idCita = Integer.parseInt(valor.trim());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de cita debe ser numérico.");
            }
            return cajaService.buscarPorNumeroCita(idCita);
        }

        String dpi = valor == null ? "" : valor.trim();
        if (!dpi.matches("\\d{13}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DPI debe contener exactamente 13 dígitos numéricos.");
        }
        return cajaService.buscarPorDpi(dpi);
    }

    /** Pasos 4-9 del flujo normal / FA01 (tarjeta) / FA04 (tarjeta rechazada). */
    @PostMapping("/citas/{idCita}/registrar-pago")
    public ComprobanteCajaResponse registrarPago(@PathVariable Integer idCita,
                                                  @Valid @RequestBody RegistrarPagoCajaRequest request,
                                                  HttpSession session) {
        SesionUsuarioDTO usuario = exigirPersonalInterno(session);
        return cajaService.registrarPago(idCita, request, usuario.getIdUsuario());
    }

    private SesionUsuarioDTO exigirPersonalInterno(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        if (Rol.PACIENTE.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este módulo.");
        }
        return usuario;
    }
}
