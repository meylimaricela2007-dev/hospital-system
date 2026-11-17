package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Qué especialidades se ofrecen en cada sede (paso 2 del wizard de CU-03). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sedes_especialidades", schema = "his")
public class SedeEspecialidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sede_especialidad")
    private Integer idSedeEspecialidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialidad", nullable = false)
    private Especialidad especialidad;

    @Column(name = "estado", nullable = false)
    private Boolean estado;
}
