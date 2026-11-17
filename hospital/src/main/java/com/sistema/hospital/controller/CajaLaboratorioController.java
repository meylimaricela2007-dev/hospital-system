package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.CajaLaboratorioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-10: Cobro de Laboratorio en Caja. Mismo criterio de acceso que
 * CajaController (CU-06): cualquier personal interno, nunca un Paciente.
 */
@RestController
@RequestMapping("/api/caja/laboratorio")
public class CajaLaboratorioController {

    private final CajaLaboratorioService cajaLaboratorioService;

    public CajaLaboratorioController(CajaLaboratorioService cajaLaboratorioService) {
        this.cajaLaboratorioService = cajaLaboratorioService;
    }

    /**
     * Paso 2: tipo=DPI   -> valor es el DPI del paciente (13 dígitos) [RN-GLOBAL-001]
     *         tipo=ORDEN -> valor es el número de orden (ej. LAB-1A2B3C4D)
     */
    @GetMapping("/buscar")
    public BusquedaCajaLaboratorioResponse buscar(@RequestParam String tipo, @RequestParam String valor, HttpSession session) {
        exigirPersonalInterno(session);
        String texto = valor == null ? "" : valor.trim();

        if ("ORDEN".equalsIgnoreCase(tipo)) {
            if (texto.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe ingresar un número de orden.");
            }
            return cajaLaboratorioService.buscarPorNumeroOrden(texto);
        }

        if (texto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo DPI es obligatorio. Por favor, ingrese su número de DPI.");
        }
        if (!texto.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El DPI debe contener únicamente números. No se permiten letras ni caracteres especiales.");
        }
        if (texto.length() != 13) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El DPI debe contener exactamente 13 dígitos. Usted ingresó " + texto.length() + " dígitos.");
        }
        return cajaLaboratorioService.buscarPorDpi(texto);
    }

    /** Pasos 6-10 / FA03 (tarjeta) / FA04 (tarjeta rechazada). */
    @PostMapping("/ordenes/{idOrden}/registrar-pago")
    public ComprobanteCajaResponse registrarPago(@PathVariable Integer idOrden,
                                                  @Valid @RequestBody RegistrarPagoCajaRequest request,
                                                  HttpSession session) {
        SesionUsuarioDTO usuario = exigirPersonalInterno(session);
        return cajaLaboratorioService.registrarPago(idOrden, request, usuario.getIdUsuario());
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
