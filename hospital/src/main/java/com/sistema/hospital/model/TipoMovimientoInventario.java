package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catálogo: Compra, Devolución, Venta, Reclamo, Ajuste+, Ajuste-, Despacho. Se busca por nombre, no por id. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tipos_movimiento_inventario", schema = "his")
public class TipoMovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_movimiento")
    private Integer idTipoMovimiento;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    public static final String DESPACHO = "Despacho";
}
