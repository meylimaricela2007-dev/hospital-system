package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** CU-15, RN-CU13-03: resumen mensual de la bitácora (totales + datos para las gráficas). */
@Data
@AllArgsConstructor
public class ResumenMensualInventarioDTO {
    private int anio;
    private int mes;
    private long totalMovimientos;
    private long unidadesEntrada;
    private long unidadesSalida;

    /** Gráfica 1: unidades movidas por tipo de movimiento. */
    private List<Punto> porTipo;
    /** Gráfica 2: entradas y salidas de cada día del mes. */
    private List<PuntoDia> porDia;
    /** Gráfica 3: los 5 medicamentos con más unidades de salida. */
    private List<Punto> topSalidas;

    @Data
    @AllArgsConstructor
    public static class Punto {
        private String etiqueta;
        private long valor;
    }

    @Data
    @AllArgsConstructor
    public static class PuntoDia {
        private int dia;
        private long entradas;
        private long salidas;
    }
}
