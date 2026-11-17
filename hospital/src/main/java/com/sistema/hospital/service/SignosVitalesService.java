package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CU-07: Toma de Signos Vitales.
 * El panel de Enfermería: llamar al siguiente paciente en sala de espera y
 * registrar sus signos vitales antes de la consulta médica. Ver
 * "7_CU_Toma_de_Signos_Vitales.docx" para el detalle de cada flujo alterno
 * (FA01-FA03) referenciado en los comentarios de este archivo.
 */
@Service
public class SignosVitalesService {

    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EstadoCitaRepository estadoCitaRepository;
    @Autowired
    private SignoVitalRepository signoVitalRepository;

    // =================================================================
    // Paso 1 del flujo normal: la cola de Enfermería
    // =================================================================

    @Transactional(readOnly = true)
    public ColaSignosVitalesDTO obtenerCola() {
        List<Cita> citasDeHoy = citaRepository.findByFechaCitaAndEstadoCita_NombreEstadoInOrderByHoraCitaAsc(
                LocalDate.now(), List.of(EstadoCita.PACIENTE_PRESENTE, EstadoCita.SIGNOS_VITALES));

        List<PacienteColaDTO> enEspera = new ArrayList<>();
        List<PacienteColaDTO> enProceso = new ArrayList<>();
        for (Cita cita : citasDeHoy) {
            PacienteColaDTO dto = convertirADTO(cita);
            if (EstadoCita.PACIENTE_PRESENTE.equals(cita.getEstadoCita().getNombreEstado())) {
                enEspera.add(dto);
            } else {
                enProceso.add(dto);
            }
        }
        return new ColaSignosVitalesDTO(enEspera, enProceso);
    }

    // =================================================================
    // Paso 1 del flujo normal: "Llamar y Tomar Signos"
    // =================================================================

    @Transactional
    public LlamarPacienteResponse llamarPaciente(Integer idCita) {
        Cita cita = citaRepository.findById(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));

        if (!EstadoCita.PACIENTE_PRESENTE.equals(cita.getEstadoCita().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este paciente no está en estado 'Paciente Presente'.");
        }

        cita.setEstadoCita(buscarEstadoCita(EstadoCita.SIGNOS_VITALES));
        citaRepository.save(cita);

        // "Turno número [N]": este proyecto no tiene un sistema de turnos
        // independiente todavía, así que usamos el número de cita (ya es el
        // identificador que el paciente conoce desde Recepción, CU-05) como
        // el "turno" del anuncio.
        String textoAnuncio = "Turno número " + cita.getIdCita() + ". Paciente " + cita.getPaciente().getNombreCompleto()
                + ", favor pasar a toma de signos vitales.";

        return new LlamarPacienteResponse(textoAnuncio, convertirADTO(cita));
    }

    // =================================================================
    // Pasos 2-12 del flujo normal + FA01/FA03: registrar signos vitales
    // =================================================================

    @Transactional
    public RegistrarSignosVitalesResponse registrarSignosVitales(Integer idCita, RegistrarSignosVitalesRequest request, Integer idEnfermero) {
        Cita cita = citaRepository.findById(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));

        if (!EstadoCita.SIGNOS_VITALES.equals(cita.getEstadoCita().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Debe llamar al paciente antes de registrar sus signos vitales.");
        }

        Usuario enfermero = usuarioRepository.findById(idEnfermero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar."));

        boolean esEmergencia = Boolean.TRUE.equals(request.getEsEmergencia());
        List<String> alertas = evaluarAlertasClinicas(request); // FA03

        SignoVital signo = new SignoVital();
        signo.setCita(cita);
        signo.setEnfermero(enfermero);
        signo.setPresionSistolica(request.getPresionSistolica());
        signo.setPresionDiastolica(request.getPresionDiastolica());
        signo.setTemperatura(request.getTemperatura());
        signo.setPeso(request.getPeso());
        signo.setTalla(request.getTalla());
        signo.setFrecuenciaCardiaca(request.getFrecuenciaCardiaca());
        signo.setEsEmergencia(esEmergencia);
        signo.setAlertasClinicas(alertas.isEmpty() ? null : String.join("\n", alertas));
        signoVitalRepository.save(signo);

        // FA01: emergencia pasa directo a Consulta Médica, sin volver a la sala de espera.
        cita.setEstadoCita(buscarEstadoCita(esEmergencia ? EstadoCita.CONSULTA_MEDICA : EstadoCita.EN_ESPERA));
        citaRepository.save(cita);

        String mensaje = esEmergencia
                ? "Signos vitales de emergencia registrados para paciente " + cita.getPaciente().getNombreCompleto()
                        + ". El paciente debe pasar directamente a consulta médica."
                : "Signos vitales del paciente " + cita.getPaciente().getNombreCompleto()
                        + " registrados correctamente. El paciente puede regresar a la sala de espera.";

        return new RegistrarSignosVitalesResponse(true, mensaje, alertas, esEmergencia);
    }

    /**
     * FA03 / [RN-CU07-06]: alertas de rango clínico normal, distintas del
     * rango de captura que ya validó @Valid en el controlador (RN-CU07-01 a
     * 05). El documento no especifica los valores numéricos exactos de
     * "rango clínico normal" (solo referencia el hook useVitalSignAlerts),
     * así que aquí se usan los rangos de referencia estándar para un adulto
     * en reposo. Si el equipo clínico define otros valores, este es el
     * único lugar que hay que tocar -y el mismo criterio debe replicarse en
     * signos-vitales.js, donde se muestran las alertas EN TIEMPO REAL
     * mientras Enfermería va escribiendo, antes de guardar el formulario.
     */
    private List<String> evaluarAlertasClinicas(RegistrarSignosVitalesRequest r) {
        List<String> alertas = new ArrayList<>();

        agregarSiFueraDeRango(alertas, "Presión sistólica",
                BigDecimal.valueOf(r.getPresionSistolica()), 90, 120, "mmHg");
        agregarSiFueraDeRango(alertas, "Presión diastólica",
                BigDecimal.valueOf(r.getPresionDiastolica()), 60, 80, "mmHg");
        agregarSiFueraDeRango(alertas, "Temperatura",
                r.getTemperatura(), 36.1, 37.2, "°C");
        agregarSiFueraDeRango(alertas, "Frecuencia cardíaca",
                BigDecimal.valueOf(r.getFrecuenciaCardiaca()), 60, 100, "lpm");

        return alertas;
    }

    private void agregarSiFueraDeRango(List<String> alertas, String etiqueta, BigDecimal valor, double min, double max, String unidad) {
        if (valor.doubleValue() < min || valor.doubleValue() > max) {
            alertas.add(etiqueta + " fuera de rango normal: " + valor + " " + unidad
                    + " (normal: " + min + "-" + max + " " + unidad + ").");
        }
    }

    private PacienteColaDTO convertirADTO(Cita cita) {
        return new PacienteColaDTO(
                cita.getIdCita(),
                cita.getPaciente().getNombreCompleto(),
                cita.getEspecialidad().getNombre(),
                cita.getMedico().getNombreCompleto(),
                cita.getHoraCita(),
                Boolean.TRUE.equals(cita.getEsEmergencia()),
                cita.getEstadoCita().getNombreEstado()
        );
    }

    private EstadoCita buscarEstadoCita(String nombre) {
        return estadoCitaRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de cita '" + nombre + "' no existe en el catálogo."));
    }
}
