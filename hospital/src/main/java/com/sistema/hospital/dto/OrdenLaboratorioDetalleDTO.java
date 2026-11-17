package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-09, paso 3: "El sistema muestra el resumen de la orden: número de
 * orden, paciente, médico, estado, monto total, indicador de orden externa
 * si aplica, y notas. Debajo lista cada examen con su nombre y monto."
 *
 * pagada = la orden ya pasó por Caja (CU-10), es decir, está "En Proceso"
 * o "Completada". Mientras no esté pagada, no se pueden registrar
 * resultados [RN-CU09-01].
 */
@Data
@AllArgsConstructor
public class OrdenLaboratorioDetalleDTO {
    private Integer idOrden;
    private String numeroOrden;
    private String nombrePaciente;
    private String dpiPaciente;
    private String nombreMedico;
    private String estado;
    private boolean esExterna;
    private boolean pagada;
    private String notas;
    private BigDecimal montoTotal;
    private LocalDateTime fechaCreacion;
    private List<ExamenOrdenDTO> examenes;
}
