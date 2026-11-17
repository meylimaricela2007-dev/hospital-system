package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CU-05, FA07 "Reasignación de médico": bitácora de auditoría. Antes esta
 * tabla no existía en la base de datos, así que RecepcionService.
 * reasignarMedico() solo actualizaba cita.medico sin dejar rastro de quién
 * hizo el cambio, cuándo, ni por qué (la nota se descartaba). Ahora sí
 * queda guardado un registro histórico completo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reasignaciones_medico", schema = "his")
public class ReasignacionMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reasignacion")
    private Integer idReasignacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico_anterior", nullable = false)
    private Usuario medicoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico_nuevo", nullable = false)
    private Usuario medicoNuevo;

    @Column(name = "nota", length = 500)
    private String nota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_responsable", nullable = false)
    private Usuario usuarioResponsable;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @PrePersist
    void alCrear() {
        this.fechaHora = LocalDateTime.now();
    }
}
