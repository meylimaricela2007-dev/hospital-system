package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Un espacio concreto (médico + fecha + hora) que el paciente puede reservar.
 * estado: "Disponible" | "Reservado" (mientras paga) | "Ocupado" (cita ya pagada).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "horarios_disponibles", schema = "his")
public class HorarioDisponible {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_horario")
    private Integer idHorario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico", nullable = false)
    private Usuario medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialidad", nullable = false)
    private Especialidad especialidad;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "reservado_hasta")
    private LocalDateTime reservadoHasta;

    public static final String DISPONIBLE = "Disponible";
    public static final String RESERVADO = "Reservado";
    public static final String OCUPADO = "Ocupado";
}
