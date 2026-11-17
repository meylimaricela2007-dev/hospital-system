package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Datos de las gráficas del dashboard del Administrador. Todo sale de la base de datos en cada consulta. */
@Data
@AllArgsConstructor
public class DashboardGraficasDTO {
    private Grafica topMedicos;
    private Grafica diasSemana;
    private Grafica fechasConMasCitas;
    private Grafica medicamentos;
    private Grafica metodosPago;
    private String actualizado;          // hora en que se leyó la base de datos

    @Data
    @AllArgsConstructor
    public static class Grafica {
        private String titulo;
        private List<String> etiquetas;
        private List<Long> valores;
    }
}
