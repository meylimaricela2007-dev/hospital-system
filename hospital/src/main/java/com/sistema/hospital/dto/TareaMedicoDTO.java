package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/** CU-16: una tarea del panel lateral. */
@Data
@AllArgsConstructor
public class TareaMedicoDTO {
    private Integer idTarea;
    private String titulo;
    private String descripcion;
    private Integer idPrioridad;
    private String prioridad;
    private LocalDate fechaLimite;
    private boolean completada;
}
