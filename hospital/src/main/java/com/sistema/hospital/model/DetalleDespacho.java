package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** CU-11: una línea del despacho (qué renglón de la receta se entregó, cuánto y a qué precio). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "detalle_despacho", schema = "his")
public class DetalleDespacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_despacho")
    private Integer idDetalleDespacho;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_despacho", nullable = false)
    private DespachoMedicamento despacho;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_detalle_receta", nullable = false)
    private DetalleReceta detalleReceta;

    @Column(name = "cantidad_despachada", nullable = false)
    private Integer cantidadDespachada;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;
}
