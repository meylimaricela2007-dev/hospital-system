package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un examen concreto dentro de una orden de laboratorio. Los campos de
 * resultado (valor_resultado, fecha_resultado, fuera_de_rango, publicado)
 * quedan en null/false al crearla desde CU-08: los llenará CU-09
 * (Laboratorio) cuando ese caso de uso se construya.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "detalle_orden_examenes", schema = "his")
public class DetalleOrdenExamen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_orden")
    private Integer idDetalleOrden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden", nullable = false)
    private OrdenLaboratorio orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_examen", nullable = false)
    private ExamenLaboratorio examen;

    // Precio del examen AL MOMENTO de ordenarlo (no se recalcula si el
    // catálogo cambia de precio después).
    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    @Column(name = "valor_resultado", length = 100)
    private String valorResultado;

    @Column(name = "unidad_resultado", length = 30)
    private String unidadResultado;

    @Column(name = "fecha_resultado")
    private LocalDateTime fechaResultado;

    @Column(name = "fuera_de_rango", nullable = false)
    private Boolean fueraDeRango = false;

    @Column(name = "notas_resultado", columnDefinition = "TEXT")
    private String notasResultado;

    @Column(name = "publicado", nullable = false)
    private Boolean publicado = false;
}
