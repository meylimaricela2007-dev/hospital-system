package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "roles", schema = "his")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer idRol;

    @Column(name = "nombre_rol", nullable = false, unique = true, length = 50)
    private String nombreRol;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "estado", nullable = false)
    private Boolean estado;

    public static final String PACIENTE = "Paciente";
    public static final String ADMINISTRADOR = "Administrador";
    public static final String MEDICO = "Médico";
    public static final String RECEPCIONISTA = "Recepcionista";
    public static final String CAJERO = "Cajero";
    public static final String ENFERMERO = "Enfermería";
    public static final String LABORATORISTA = "Laboratorista";
    public static final String FARMACEUTICO = "Farmacéutico";
}
