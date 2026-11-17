package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.CitaService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** CU-03: Agendar Cita. También sirve de base para el walk-in de CU-05 FA04. */
@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping("/horarios")
    public List<HorarioDisponibleDTO> horarios(@RequestParam Integer medico,
                                                @RequestParam Integer sucursal,
                                                @RequestParam Integer especialidad) {
        return citaService.obtenerHorariosDisponibles(medico, sucursal, especialidad);
    }

    /** NUEVO: "Mis Citas" del portal del paciente (usuarioSesion.rolNombre puede ser cualquiera, ve sus propias citas). */
    @GetMapping("/mis-citas")
    public List<MiCitaDTO> misCitas(HttpSession session) {
        SesionUsuarioDTO usuario = exigirSesion(session);
        return citaService.listarMisCitas(usuario.getIdUsuario());
    }

    /** CU-12, paso 2: datos pre-cargados (paciente, médico, especialidad, sede) del seguimiento. Solo Médico. */
    @GetMapping("/seguimiento/contexto")
    public SeguimientoContextoDTO contextoSeguimiento(@RequestParam Integer idConsultaOrigen, HttpSession session) {
        SesionUsuarioDTO usuario = exigirSesion(session);
        exigirMedico(usuario);
        return citaService.obtenerContextoSeguimiento(idConsultaOrigen, usuario.getIdUsuario());
    }

    @PostMapping("/crear")
    public CitaCreadaResponse crear(@Valid @RequestBody CrearCitaRequest request, HttpSession session) {
        SesionUsuarioDTO usuario = exigirSesion(session);

        // CU-12: seguimiento. Solo el médico de la consulta; el paciente es SIEMPRE el de esa
        // consulta (no se confía en lo que mande el navegador) y la cita queda sin vencimiento
        // automático, igual que las que crea el personal interno (CU-06, FA03).
        if (request.getIdConsultaOrigen() != null) {
            exigirMedico(usuario);
            SeguimientoContextoDTO contexto = citaService.obtenerContextoSeguimiento(request.getIdConsultaOrigen(), usuario.getIdUsuario());
            return citaService.crearCita(request, contexto.getIdPaciente(), true);
        }

        Integer idPaciente = resolverIdPacienteParaLaReserva(request, usuario);
        // CU-06, FA03: "Las citas creadas por personal interno no se cancelan
        // automáticamente" (a diferencia de las que agenda el propio paciente
        // desde el portal, que sí tienen el temporizador de reserva de CU-03).
        // Antes esta bandera no existía y un walk-in creado por Recepción
        // (idPacienteWalkIn) heredaba el mismo temporizador de 5 minutos que
        // una cita en línea, así que podía expirar solo mientras el paciente
        // hacía fila para pagar en caja.
        boolean creadaPorPersonalInterno = request.getIdPacienteWalkIn() != null;
        return citaService.crearCita(request, idPaciente, creadaPorPersonalInterno);
    }

    /**
     * CORREGIDO: este endpoint devolvía el resumen de CUALQUIER cita (nombre
     * del paciente, médico, motivo de consulta...) sin exigir sesión alguna.
     * Bastaba con probar números consecutivos en la URL
     * (/api/citas/1/resumen, /api/citas/2/resumen, ...) para leer datos
     * médicos de otros pacientes. Ahora se exige sesión, y CitaService valida
     * que la cita sea del propio paciente o que quien pregunta sea personal
     * interno (un Recepcionista/Cajero sí necesita poder consultarla).
     */
    @GetMapping("/{idCita}/resumen")
    public CitaResumenResponse resumen(@PathVariable Integer idCita, HttpSession session) {
        SesionUsuarioDTO usuario = exigirSesion(session);
        boolean esPersonalInterno = !Rol.PACIENTE.equals(usuario.getRolNombre());
        return citaService.obtenerResumen(idCita, usuario.getIdUsuario(), esPersonalInterno);
    }

    /**
     * CORREGIDO (CU-05, FA04): antes este método SIEMPRE tomaba el id del
     * paciente de la sesión, lo cual impedía por diseño que un Recepcionista
     * agendara una cita "Nueva Cita (Walk-in)" a nombre de un paciente que
     * ya está en el sistema pero llegó sin cita. Ahora: si el request trae
     * idPacienteWalkIn, solo se respeta cuando quien tiene la sesión ES
     * personal interno (no un Paciente); así un paciente nunca puede mandar
     * el id de otro paciente en el JSON y agendarle una cita él mismo.
     */
    private Integer resolverIdPacienteParaLaReserva(CrearCitaRequest request, SesionUsuarioDTO usuario) {
        if (request.getIdPacienteWalkIn() != null) {
            if (Rol.PACIENTE.equals(usuario.getRolNombre())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No tiene permisos para agendar una cita a nombre de otro paciente.");
            }
            return request.getIdPacienteWalkIn();
        }
        return usuario.getIdUsuario();
    }

    private void exigirMedico(SesionUsuarioDTO usuario) {
        if (!Rol.MEDICO.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un médico puede agendar una cita de seguimiento.");
        }
    }

    private SesionUsuarioDTO exigirSesion(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        return usuario;
    }
}
