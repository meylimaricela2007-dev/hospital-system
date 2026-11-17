package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** CU-16, FA06: tareas filtradas más los conteos del conjunto (antes del filtro de estado). */
@Data
@AllArgsConstructor
public class TareasMedicoResponse {
    private List<TareaMedicoDTO> tareas;
    private int total;
    private int pendientes;
    private int completadas;
}
