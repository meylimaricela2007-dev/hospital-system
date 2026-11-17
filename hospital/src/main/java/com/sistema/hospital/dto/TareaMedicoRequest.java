package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** CU-16, RN-CU14-02: formulario de tarea. */
@Data
public class TareaMedicoRequest {

    @NotBlank(message = "El título es obligatorio y debe tener entre 5 y 200 caracteres.")
    @Size(min = 5, max = 200, message = "El título es obligatorio y debe tener entre 5 y 200 caracteres.")
    private String titulo;

    @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres.")
    private String descripcion;

    @NotNull(message = "Debe seleccionar la prioridad.")
    private Integer idPrioridad;

    @NotNull(message = "La fecha límite es obligatoria.")
    private LocalDate fechaLimite;
}
