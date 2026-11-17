package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Datos de la tarjeta que llegan desde el formulario de pago (CU-04). */
@Data
public class PagoTarjetaRequest {

    @NotBlank(message = "Selecciona un tipo de tarjeta.")
    private String tipoTarjeta; // "Visa" | "Mastercard" | "Débito"

    @NotBlank(message = "El número de tarjeta es obligatorio.")
    @Pattern(regexp = "\\d{13,19}", message = "El número de tarjeta debe contener entre 13 y 19 dígitos.")
    private String numeroTarjeta;

    // QA (RN-CU04-02): 5-100 caracteres alfabéticos, sin caracteres especiales.
    @NotBlank(message = "El nombre del titular es obligatorio.")
    @Pattern(regexp = "[\\p{L} ]{5,100}",
            message = "El nombre del titular debe contener entre 5 y 100 caracteres alfabéticos sin especiales.")
    private String nombreTitular;

    @NotBlank(message = "La fecha de vencimiento es obligatoria.")
    @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}", message = "El formato de vencimiento debe ser MM/AA.")
    private String fechaVencimiento;

    // El CVV se valida solo en el navegador: nunca se envía ni se almacena (CU-04, paso 6).
}
