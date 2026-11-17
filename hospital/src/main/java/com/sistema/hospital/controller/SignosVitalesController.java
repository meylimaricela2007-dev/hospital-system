package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.SignosVitalesService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-07: Toma de Signos Vitales. Igual que Recepción y Caja, todos los
 * endpoints exigen sesión de personal interno: los usa Enfermería desde su
 * panel, nunca el paciente desde su propia cuenta.
 */
@RestController
@RequestMapping("/api/signos-vitales")
public class SignosVitalesController {

    private final SignosVitalesService signosVitalesService;

    public SignosVitalesController(SignosVitalesService signosVitalesService) {
        this.signosVitalesService = signosVitalesService;
    }

    /** Paso 1 del flujo normal: la cola de pacientes (Paciente Presente / Signos Vitales). */
    @GetMapping("/cola")
    public ColaSignosVitalesDTO cola(HttpSession session) {
        exigirPersonalInterno(session);
        return signosVitalesService.obtenerCola();
    }

    /** Paso 1 del flujo normal: "Llamar y Tomar Signos" (anuncio TTS). */
    @PostMapping("/citas/{idCita}/llamar")
    public LlamarPacienteResponse llamar(@PathVariable Integer idCita, HttpSession session) {
        exigirPersonalInterno(session);
        return signosVitalesService.llamarPaciente(idCita);
    }

    /** Pasos 2-12 del flujo normal / FA01 / FA02 / FA03. */
    @PostMapping("/citas/{idCita}/registrar")
    public RegistrarSignosVitalesResponse registrar(@PathVariable Integer idCita,
                                                      @Valid @RequestBody RegistrarSignosVitalesRequest request,
                                                      HttpSession session) {
        SesionUsuarioDTO usuario = exigirPersonalInterno(session);
        return signosVitalesService.registrarSignosVitales(idCita, request, usuario.getIdUsuario());
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
