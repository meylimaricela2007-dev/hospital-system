package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.ConsultaMedicaService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** CU-08: Consulta Médica. Todos los endpoints exigen sesión de un Médico. */
@RestController
@RequestMapping("/api/medico")
public class ConsultaMedicaController {

    private final ConsultaMedicaService consultaMedicaService;

    public ConsultaMedicaController(ConsultaMedicaService consultaMedicaService) {
        this.consultaMedicaService = consultaMedicaService;
    }

    @GetMapping("/panel")
    public PanelMedicoResponse panel(HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        return consultaMedicaService.obtenerPanel(usuario.getIdUsuario());
    }

    @PostMapping("/citas/{idCita}/iniciar-consulta")
    public IniciarConsultaResponse iniciarConsulta(@PathVariable Integer idCita, HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        return consultaMedicaService.iniciarConsulta(idCita, usuario.getIdUsuario());
    }

    @PostMapping("/citas/{idCita}/no-asistio")
    public Map<String, Object> noAsistio(@PathVariable Integer idCita, HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        String mensaje = consultaMedicaService.marcarNoAsistio(idCita, usuario.getIdUsuario());
        return Map.of("success", true, "mensaje", mensaje);
    }

    @GetMapping("/citas/{idCita}/contexto")
    public ContextoConsultaDTO contexto(@PathVariable Integer idCita, HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        return consultaMedicaService.obtenerContexto(idCita, usuario.getIdUsuario());
    }

    @PostMapping("/citas/{idCita}/consulta")
    public GuardarConsultaResponse guardarConsulta(@PathVariable Integer idCita,
                                                    @Valid @RequestBody GuardarConsultaRequest request,
                                                    HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        return consultaMedicaService.guardarConsulta(idCita, usuario.getIdUsuario(), request);
    }

    @PostMapping("/citas/{idCita}/orden-laboratorio")
    public OrdenLaboratorioResponse ordenLaboratorio(@PathVariable Integer idCita,
                                                      @Valid @RequestBody GenerarOrdenLaboratorioRequest request,
                                                      HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        return consultaMedicaService.generarOrdenLaboratorio(idCita, usuario.getIdUsuario(), request);
    }

    @PostMapping("/citas/{idCita}/receta")
    public RecetaResponse receta(@PathVariable Integer idCita,
                                  @Valid @RequestBody GenerarRecetaRequest request,
                                  HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        return consultaMedicaService.generarReceta(idCita, usuario.getIdUsuario(), request);
    }

    @PostMapping("/citas/{idCita}/finalizar-atencion")
    public Map<String, Object> finalizarAtencion(@PathVariable Integer idCita, HttpSession session) {
        SesionUsuarioDTO usuario = exigirMedico(session);
        String mensaje = consultaMedicaService.finalizarAtencion(idCita, usuario.getIdUsuario());
        return Map.of("success", true, "mensaje", mensaje);
    }

    private SesionUsuarioDTO exigirMedico(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        // A diferencia de Recepción/Caja/Enfermería (que solo excluyen al rol
        // Paciente), aquí SÍ se exige el rol exacto "Médico": iniciar
        // consulta, diagnosticar y recetar son acciones clínicas que ningún
        // otro perfil interno debería poder hacer.
        if (!Rol.MEDICO.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este módulo.");
        }
        return usuario;
    }
}
