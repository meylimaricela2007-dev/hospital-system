package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** CU-11, pasos 11-13: resumen del despacho registrado. */
@Data
@AllArgsConstructor
public class DespachoResponse {
    private String mensaje;
    private Integer idDespacho;
    private Integer idReceta;
    private String paciente;
    private BigDecimal montoTotal;
    private List<Linea> lineas;
    /** FA02: "Medicamento [original] sustituido por [alternativa]. El médico tratante será notificado..." */
    private List<String> sustituciones;
    /** FA04: "ALERTA: El medicamento [nombre] ha alcanzado el nivel de stock mínimo..." */
    private List<String> alertasStockMinimo;

    @Data
    @AllArgsConstructor
    public static class Linea {
        private String medicamento;
        private String unidad;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
        private String sustituyeA; // nombre del medicamento recetado originalmente, si hubo sustitución
    }
}
