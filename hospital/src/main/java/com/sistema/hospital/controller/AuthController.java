package com.sistema.hospital.controller;

import com.sistema.hospital.dto.LoginRequest;
import com.sistema.hospital.dto.LoginResponse;
import com.sistema.hospital.dto.RegistroPacienteRequest;
import com.sistema.hospital.service.AuthService;
import com.sistema.hospital.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindingResult;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    // CU-00: login (paso 9 del flujo normal)
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpSession session) {
        return authService.login(request, session);
    }

    // CU-02, paso 10-13: registro de pacientes
    @PostMapping("/registro")
    public Map<String, Object> registrar(@Valid @RequestBody RegistroPacienteRequest request,
                                         BindingResult resultado) {
        usuarioService.validarRegistro(request, resultado);
        usuarioService.registrarPaciente(request);
        return Map.of(
                "success", true,
                "mensaje", "¡Registro exitoso! Su cuenta ha sido creada. Ahora puede iniciar sesión con sus credenciales."
        );
    }
}
