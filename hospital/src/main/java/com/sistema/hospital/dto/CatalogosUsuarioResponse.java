package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Todo lo que necesita el formulario de usuario (CU-01) para llenar sus <select>. */
@Data
@AllArgsConstructor
public class CatalogosUsuarioResponse {
    private List<CatalogoItemDTO> roles;
    private List<CatalogoItemDTO> sucursales;
    private List<CatalogoItemDTO> especialidades;
}
