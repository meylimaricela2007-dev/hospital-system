package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Una opción de un <select>: un id y el texto a mostrar. */
@Data
@AllArgsConstructor
public class CatalogoItemDTO {
    private Integer id;
    private String nombre;
}
