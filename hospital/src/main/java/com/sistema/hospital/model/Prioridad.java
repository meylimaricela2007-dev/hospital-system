package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catálogo "prioridades" (Baja, Normal, Alta). CU-12 lo usa para la prioridad de la cita de seguimiento. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prioridades", schema = "his")
public class Prioridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prioridad")
    private Integer idPrioridad;

    @Column(name = "nombre", nullable = false)
    private String nombre;
}
