package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * CU-06, paso 4 en adelante. Un mismo formulario cubre tanto el flujo normal
 * (Efectivo) como FA01 (tarjeta): el frontend solo manda los campos que
 * aplican según el método elegido; CajaService valida cuáles son
 * obligatorios en cada caso.
 */
@Data
public class RegistrarPagoCajaRequest {

    @NotBlank(message = "Debe seleccionar un método de pago.")
    private String metodoPago; // "Efectivo" | "Visa" | "Mastercard" | "Débito"

    // Obligatorio solo si metodoPago = "Efectivo" (paso 6 del flujo normal).
    private BigDecimal montoRecibido;

    // Obligatorio solo si metodoPago != "Efectivo" (FA01, paso 3: "últimos 4 dígitos... como referencia").
    private String tarjetaUltimos4;
}
