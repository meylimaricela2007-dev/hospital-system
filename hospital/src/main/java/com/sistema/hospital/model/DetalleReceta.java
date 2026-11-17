package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * [RN-CU08-03] Un medicamento dentro de una receta: medicamento, dosis,
 * frecuencia, duración, indicaciones. Los campos fue_sustituido /
 * razon_sustitucion / medicamento_alternativo quedan en su valor por
 * defecto al crearla desde CU-08: los usará CU-10 (Farmacia) cuando un
 * medicamento no esté disponible en el momento del despacho.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "detalle_receta", schema = "his")
public class DetalleReceta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_receta")
    private Integer idDetalleReceta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_receta", nullable = false)
    private RecetaMedica receta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medicamento", nullable = false)
    private Medicamento medicamento;

    @Column(name = "dosis", nullable = false, length = 100)
    private String dosis;

    @Column(name = "frecuencia", nullable = false, length = 100)
    private String frecuencia;

    @Column(name = "duracion", nullable = false, length = 100)
    private String duracion;

    @Column(name = "indicaciones", length = 500)
    private String indicaciones;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    // Precio del medicamento AL MOMENTO de recetarlo (snapshot, igual que
    // detalle_orden_examenes.monto).
    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "fue_sustituido", nullable = false)
    private Boolean fueSustituido = false;

    @Column(name = "razon_sustitucion", length = 500)
    private String razonSustitucion;

    @Column(name = "medicamento_alternativo", length = 200)
    private String medicamentoAlternativo;
}
