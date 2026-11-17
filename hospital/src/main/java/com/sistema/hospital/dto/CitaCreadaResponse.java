package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Respuesta al crear una cita: éxito + datos para la pantalla de confirmación/pago. */
@Data
@AllArgsConstructor
public class CitaCreadaResponse {
    private boolean success;
    private Integer idCita;
    private BigDecimal monto;
    private LocalDateTime fechaExpiracionReserva;
    private String mensaje;
    // NUEVO (CU-08, FA02): para armar "Cita de seguimiento agendada para el
    // [fecha] a las [hora]" en el frontend sin tener que pedir el resumen aparte.
    private LocalDate fechaCita;
    private LocalTime horaCita;
}
