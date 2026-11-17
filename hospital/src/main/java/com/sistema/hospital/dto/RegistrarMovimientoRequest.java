package com.sistema.hospital.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** CU-15, RN-CU13-01: datos del formulario "Nuevo Movimiento". */
@Data
public class RegistrarMovimientoRequest {

    @NotNull(message = "Debe seleccionar un medicamento.")
    private Integer idMedicamento;

    @NotNull(message = "Debe seleccionar una sucursal.")
    private Integer idSucursal;

    @NotNull(message = "Debe seleccionar el tipo de movimiento.")
    private Integer idTipoMovimiento;

    @NotNull(message = "La cantidad debe ser un número entero positivo.")
    @Min(value = 1, message = "La cantidad debe ser un número entero positivo.")
    private Integer cantidad;

    /** Obligatorio solo para Compra (se valida en el servicio, depende del tipo). */
    private BigDecimal costoUnitario;

    @Size(max = 100, message = "El número de referencia no puede superar 100 caracteres.")
    private String numeroReferencia;

    /** Notas / Justificación / Motivo (obligatorio según el tipo, ver servicio). */
    private String motivo;
}
