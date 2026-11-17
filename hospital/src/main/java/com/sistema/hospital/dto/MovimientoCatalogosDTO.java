package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** CU-15: opciones de los dropdowns del listado y del formulario. */
@Data
@AllArgsConstructor
public class MovimientoCatalogosDTO {
    private List<CatalogoItemDTO> medicamentos;
    private List<CatalogoItemDTO> sucursales;
    /** Tipos de creación manual (sin Despacho), con la bandera de entrada/salida. */
    private List<TipoMovimientoOpcion> tipos;
    private boolean sucursalFija;
    /** Todos los tipos (incluye Despacho) solo para el filtro del listado. */
    private List<TipoMovimientoOpcion> tiposFiltro;

    @Data
    @AllArgsConstructor
    public static class TipoMovimientoOpcion {
        private Integer id;
        private String nombre;
        private boolean entrada;
    }
}
