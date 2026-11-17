package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** CU-16: un elemento del calendario del médico (evento propio o cita programada). */
@Data
@AllArgsConstructor
public class AgendaItemDTO {
    /** "evento" o "cita". */
    private String tipo;
    /** Id del evento o de la cita (según el tipo). */
    private Integer id;
    private String titulo;
    private LocalDateTime inicio;
    private LocalDateTime fin;
    private boolean todoElDia;
    private String color;
    private String descripcion;
    /** Eventos: id y nombre del tipo de evento. */
    private Integer idTipoEvento;
    private String tipoEvento;
    /** Citas: paciente y estado. */
    private String paciente;
    private String estado;
}
