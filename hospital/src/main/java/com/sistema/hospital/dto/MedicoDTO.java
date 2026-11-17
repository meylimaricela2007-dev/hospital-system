package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Un médico en la lista del paso 3 del wizard de citas.js. */
@Data
@AllArgsConstructor
public class MedicoDTO {
    private Integer idUsuario;
    private String nombreCompleto;
    private String dpi;
}
