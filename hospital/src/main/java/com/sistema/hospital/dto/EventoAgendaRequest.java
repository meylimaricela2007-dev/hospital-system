package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** CU-16, RN-CU14-01: formulario de evento. Con "todoElDia" solo cuenta la parte de fecha. */
@Data
public class EventoAgendaRequest {

    @NotBlank(message = "El título es obligatorio y debe tener entre 5 y 200 caracteres.")
    @Size(min = 5, max = 200, message = "El título es obligatorio y debe tener entre 5 y 200 caracteres.")
    private String titulo;

    @Size(max = 500, message = "La descripción no puede superar 500 caracteres.")
    private String descripcion;

    @NotNull(message = "La fecha de inicio es obligatoria.")
    private LocalDateTime fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria.")
    private LocalDateTime fechaFin;

    @NotNull(message = "Debe seleccionar el tipo de evento.")
    private Integer idTipoEvento;

    private Boolean todoElDia;
}
