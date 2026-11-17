package com.sistema.hospital.service;

import com.sistema.hospital.model.Cita;
import com.sistema.hospital.model.EstadoCita;
import com.sistema.hospital.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-12, RN-CU11-05: recordatorio automático de las citas de seguimiento.
 * Cada hora revisa las citas de seguimiento que ocurren dentro de las
 * próximas N horas (por defecto 24) y le manda UN solo recordatorio al
 * paciente. El envío previo queda registrado en "notificaciones_correo",
 * que es lo que evita repetirlo en la siguiente revisión.
 */
@Service
public class RecordatorioSeguimientoService {

    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private NotificacionService notificacionService;

    @Value("${app.seguimiento.horas-recordatorio:24}")
    private int horasAntes;

    /** Corre 1 minuto después de arrancar y luego cada hora. */
    @Scheduled(initialDelay = 60_000, fixedRate = 3_600_000)
    @Transactional
    public void enviarRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusHours(horasAntes);

        List<Cita> candidatas = citaRepository.findByEsSeguimientoTrueAndFechaCitaBetweenAndEstadoCita_NombreEstadoIn(
                LocalDate.from(ahora), LocalDate.from(limite),
                List.of(EstadoCita.PENDIENTE_DE_PAGO, EstadoCita.CONFIRMADA));

        for (Cita cita : candidatas) {
            LocalDateTime momento = LocalDateTime.of(cita.getFechaCita(), cita.getHoraCita());
            if (momento.isBefore(ahora) || momento.isAfter(limite)) continue;
            if (notificacionService.yaSeEnvioRecordatorio(cita.getIdCita())) continue;
            notificacionService.enviarRecordatorioSeguimiento(cita);
        }
    }
}
