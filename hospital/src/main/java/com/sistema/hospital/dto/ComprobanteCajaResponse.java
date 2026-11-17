package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CU-06, paso 9: "el sistema genera y muestra el comprobante de pago
 * (componente PaymentReceipt)". Es el mismo comprobante que ya usa el pago
 * en línea (ver ComprobantePagoResponse), más montoRecibido/cambio, que
 * solo tienen sentido para pagos en Efectivo (quedan null en tarjeta).
 */
@Data
@AllArgsConstructor
public class ComprobanteCajaResponse {
    private boolean success;
    private String numeroTransaccion;
    private String nombrePaciente;
    private BigDecimal monto;
    private String formaPago;
    private LocalDateTime fechaHora;
    private String detalleServicio;
    private String sucursal;
    private String mensaje;
    private BigDecimal montoRecibido; // null si no fue Efectivo
    private BigDecimal cambio;        // null si no fue Efectivo
}
