package com.sistema.hospital.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** CU-11, pasos 6-9: lo que el farmacéutico confirma (cantidades ajustadas y sustituciones). */
@Data
public class DespacharRequest {

    @NotEmpty(message = "Debe indicar los medicamentos a despachar.")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "Falta el identificador del medicamento recetado.")
        private Integer idDetalleReceta;

        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 0, message = "La cantidad no puede ser negativa.")
        private Integer cantidad;

        // FA02: sustitución
        private Boolean sustituido;
        private String razonSustitucion;
        private Integer idMedicamentoAlternativo;
    }
}
