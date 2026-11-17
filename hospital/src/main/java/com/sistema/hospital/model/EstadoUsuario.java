package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Activo / Inactivo / Bloqueado. Controla si el usuario puede iniciar sesión. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "estados_usuario", schema = "his")
public class EstadoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_usuario")
    private Integer idEstadoUsuario;

    @Column(name = "nombre_estado", nullable = false, unique = true, length = 30)
    private String nombreEstado;

    public static final String ACTIVO = "Activo";
    public static final String INACTIVO = "Inactivo";
    public static final String BLOQUEADO = "Bloqueado";
}
