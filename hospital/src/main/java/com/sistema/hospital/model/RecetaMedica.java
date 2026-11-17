package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CU-08, FA04: una receta médica generada durante la consulta.
 * Nota: esta tabla (según tu base de datos real) NO tiene una columna de
 * "número de receta" como sí la tiene ordenes_laboratorio con
 * numero_orden; el id_receta autogenerado hace ese papel como referencia
 * para mostrarle al médico/paciente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recetas_medicas", schema = "his")
public class RecetaMedica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_receta")
    private Integer idReceta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false)
    private Consulta consulta;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;

    @Column(name = "notas", length = 500)
    private String notas;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleReceta> detalles = new ArrayList<>();

    @PrePersist
    void alCrear() {
        this.fechaEmision = LocalDateTime.now();
    }
}
