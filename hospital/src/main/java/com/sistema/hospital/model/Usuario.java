package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tabla "usuarios": guarda tanto al personal interno (Médico, Recepcionista,
 * Administrador, etc.) como a los pacientes. Se diferencian por su Rol.
 *
 * CORREGIDO respecto a la versión que tenías: idRol e idEstadoUsuario eran
 * Integer sueltos (sin relación). Con eso, en otras partes del código se
 * terminaba comparando "usuario.getIdRol() == 8" — un número mágico frágil,
 * que se rompe en silencio si algún día los roles se insertan en otro orden.
 * Ahora son relaciones @ManyToOne hacia Rol/EstadoUsuario, y se compara por
 * NOMBRE: usuario.getRol().getNombreRol().equals(Rol.PACIENTE).
 * También agregué las columnas que la tabla real exige y que faltaban:
 * nit, numeroSeguro, sucursal, especialidad, intentosFallidos,
 * bloqueadoHasta y fechaActualizacion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios", schema = "his")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "correo_electronico", nullable = false, unique = true, length = 150)
    private String correoElectronico;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 9)
    private String nombreUsuario;

    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(name = "dpi", unique = true, length = 13)
    private String dpi;

    @Column(name = "nit", length = 9)
    private String nit;

    @Column(name = "telefono", length = 8)
    private String telefono;

    @Column(name = "numero_seguro", length = 50)
    private String numeroSeguro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_usuario", nullable = false)
    private EstadoUsuario estadoUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal")
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialidad")
    private Especialidad especialidad;

    @Column(name = "intentos_fallidos", nullable = false)
    private Short intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /** Se ejecuta automáticamente antes del primer INSERT. */
    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
        if (this.intentosFallidos == null) {
            this.intentosFallidos = 0;
        }
    }

    /** Se ejecuta automáticamente antes de cada UPDATE. */
    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
