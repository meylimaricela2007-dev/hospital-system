package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Existencias de un medicamento en una sucursal (CU-11 las descuenta al
 * despachar). Hay a lo sumo un registro por (medicamento, sucursal).
 * "version_control" se incrementa en cada descuento (control de concurrencia).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventario_medicamentos", schema = "his")
public class InventarioMedicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventario")
    private Integer idInventario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medicamento", nullable = false)
    private Medicamento medicamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    @Column(name = "estado", nullable = false)
    private Boolean estado = true;

    @Column(name = "version_control", nullable = false)
    private Integer versionControl = 0;
}
