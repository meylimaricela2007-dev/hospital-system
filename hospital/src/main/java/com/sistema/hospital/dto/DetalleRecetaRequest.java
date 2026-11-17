package com.sistema.hospital.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** [RN-CU08-03] Un renglón de la receta: medicamento, dosis, frecuencia, duración, indicaciones. */
@Data
public class DetalleRecetaRequest {

    @NotNull(message = "Debe seleccionar un medicamento.")
    private Integer idMedicamento;

    @NotBlank(message = "La dosis es obligatoria.")
    private String dosis;

    @NotBlank(message = "La frecuencia es obligatoria.")
    private String frecuencia;

    @NotBlank(message = "La duración es obligatoria.")
    private String duracion;

    private String indicaciones;

    // No lo pide literalmente el texto del documento, pero la tabla
    // detalle_receta.cantidad es NOT NULL en tu base de datos real -sin
    // esto no hay forma de indicarle a farmacia (CU-10) cuántas unidades
    // despachar-, así que se agrega como campo obligatorio del formulario.
    @NotNull(message = "La cantidad es obligatoria.")
    @Min(value = 1, message = "La cantidad debe ser al menos 1.")
    private Integer cantidad;
}
