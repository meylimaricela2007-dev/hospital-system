package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.RecepcionService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * CU-05: Recepción y Verificación de Cita.
 * Todos los endpoints exigen sesión de personal interno (no de un
 * Paciente): este módulo lo opera el Empleado Interno (Recepcionista) en
 * el mostrador de la clínica, nunca el paciente desde su propia cuenta.
 */
@RestController
@RequestMapping("/api/recepcion")
public class RecepcionController {

    private final RecepcionService recepcionService;

    public RecepcionController(RecepcionService recepcionService) {
        this.recepcionService = recepcionService;
    }

    /**
     * Paso 3 del flujo normal / FA02: buscar la cita del paciente.
     * tipo=CITA  -> valor es el número de cita (idCita)
     * tipo=DPI   -> valor es el DPI del paciente (13 dígitos)
     */
    @GetMapping("/buscar")
    public RecepcionBusquedaResponse buscar(@RequestParam String tipo, @RequestParam String valor, HttpSession session) {
        exigirPersonalInterno(session);

        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe ingresar un número de cita o DPI para buscar.");
        }

        if ("CITA".equalsIgnoreCase(tipo)) {
            Integer idCita;
            try {
                idCita = Integer.parseInt(valor.trim());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de cita debe ser numérico.");
            }
            return recepcionService.buscarPorNumeroCita(idCita);
        }

        String dpi = valor == null ? "" : valor.trim();
        if (!dpi.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El DPI debe contener únicamente números. No se permiten letras ni caracteres especiales.");
        }
        if (dpi.length() != 13) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El DPI debe contener exactamente 13 dígitos. Usted ingresó " + dpi.length() + " dígitos.");
        }
        return recepcionService.buscarPorDpi(dpi);
    }

    /** Paso 8 del flujo normal: "Registrar llegada". */
    @PostMapping("/citas/{idCita}/registrar-llegada")
    public RegistrarLlegadaResponse registrarLlegada(@PathVariable Integer idCita, HttpSession session) {
        exigirPersonalInterno(session);
        return recepcionService.registrarLlegada(idCita);
    }

    /** FA01: paciente llega por emergencia, sin cita previa. */
    @PostMapping("/emergencia")
    public RegistrarLlegadaResponse registrarEmergencia(@Valid @RequestBody RegistrarEmergenciaRequest request, HttpSession session) {
        exigirPersonalInterno(session);
        return recepcionService.registrarEmergencia(request);
    }

    /** FA07, paso 2: candidatos disponibles para reasignar médico. */
    @GetMapping("/citas/{idCita}/reasignacion")
    public ReasignacionInfoDTO candidatosReasignacion(@PathVariable Integer idCita, HttpSession session) {
        exigirPersonalInterno(session);
        return recepcionService.listarMedicosParaReasignacion(idCita);
    }

    /** FA07, paso 3: confirmar la reasignación. */
    @PostMapping("/citas/{idCita}/reasignar-medico")
    public Map<String, Object> reasignarMedico(@PathVariable Integer idCita,
                                                @Valid @RequestBody ReasignarMedicoRequest request,
                                                HttpSession session) {
        SesionUsuarioDTO usuario = exigirPersonalInterno(session);
        String mensaje = recepcionService.reasignarMedico(idCita, request, usuario.getIdUsuario());
        return Map.of("success", true, "mensaje", mensaje);
    }

    /**
     * Este proyecto no trae el starter completo de Spring Security (ver
     * SeguridadConfig.java), así que la autorización por rol se hace "a
     * mano" aquí, igual que en ViewController para /admin/**. No hay un
     * catálogo cerrado de "quién puede usar Recepción"; por ahora basta con
     * excluir explícitamente a los Pacientes, igual que hace el resto de la
     * aplicación con sus pantallas internas.
     */
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
