package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** El comprobante que se muestra tras un pago aprobado (RN-GLOBAL-005). */
@Data
@AllArgsConstructor
public class ComprobantePagoResponse {
    private boolean success;
    private String numeroTransaccion;
    private String nombrePaciente;
    private BigDecimal monto;
    private String formaPago;
    private LocalDateTime fechaHora;
    private String detalleServicio;
    private String sucursal;
    private String mensaje;
}
