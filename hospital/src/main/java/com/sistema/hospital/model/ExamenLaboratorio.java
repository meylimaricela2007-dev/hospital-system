package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Un examen de laboratorio disponible para ordenar (CU-08, FA01). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "examenes_laboratorio", schema = "his")
public class ExamenLaboratorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_examen")
    private Integer idExamen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_laboratorio", nullable = false)
    private Laboratorio laboratorio;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "precio", nullable = false)
    private BigDecimal precio;

    @Column(name = "rango_referencia", length = 100)
    private String rangoReferencia;

    @Column(name = "unidad_medida", length = 30)
    private String unidadMedida;

    @Column(name = "estado", nullable = false)
    private Boolean estado;
}
