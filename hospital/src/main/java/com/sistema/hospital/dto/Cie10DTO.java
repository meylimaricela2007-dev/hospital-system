package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CU-08, paso 6: autocompletado de CIE-10. Esta base de datos no tiene una
 * tabla de catálogo CIE-10 (no está entre las 38 tablas del esquema), así
 * que -igual que se decidió para el resto de catálogos de referencia
 * puramente estáticos- vive como una lista fija dentro de CatalogoService,
 * no en la base de datos.
 */
@Data
@AllArgsConstructor
public class Cie10DTO {
    private String codigo;
    private String descripcion;
}
