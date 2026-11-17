package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bitácora de auditoría (tabla "auditoria"): quién hizo qué y cuándo.
 * CU-13 (y CU-14) la usan en su postcondición: "El sistema registra un log
 * de auditoría con la acción realizada".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auditoria", schema = "his")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Integer idAuditoria;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "accion", nullable = false, length = 100)
    private String accion;

    @Column(name = "entidad", length = 100)
    private String entidad;

    @Column(name = "id_entidad")
    private Integer idEntidad;

    @Column(name = "detalle", columnDefinition = "text")
    private String detalle;

    @Column(name = "direccion_ip", length = 45)
    private String direccionIp;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @PrePersist
    void alCrear() {
        if (this.fechaHora == null) {
            this.fechaHora = LocalDateTime.now();
        }
    }
}
