package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Un medicamento del catálogo de farmacia (CU-08 FA04 / CU-10). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medicamentos", schema = "his")
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medicamento")
    private Integer idMedicamento;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "precio", nullable = false)
    private BigDecimal precio;

    @Column(name = "unidad", nullable = false, length = 50)
    private String unidad;

    @Column(name = "es_controlado", nullable = false)
    private Boolean esControlado = false;

    @Column(name = "stock_minimo")
    private Integer stockMinimo = 0;

    @Column(name = "estado", nullable = false)
    private Boolean estado;
}
