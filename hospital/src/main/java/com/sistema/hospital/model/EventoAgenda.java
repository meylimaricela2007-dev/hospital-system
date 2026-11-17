package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CU-16: un evento personal en la agenda del médico (reunión, descanso,
 * capacitación...). Mientras dura, el médico no ofrece horarios para citas.
 * La tabla exige fecha_fin > fecha_inicio.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "eventos_agenda_medico", schema = "his")
public class EventoAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Integer idEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico", nullable = false)
    private Usuario medico;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_evento", nullable = false)
    private TipoEventoAgenda tipoEvento;

    @Column(name = "todo_el_dia", nullable = false)
    private Boolean todoElDia = false;
}
