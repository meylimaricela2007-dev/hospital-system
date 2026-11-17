package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CU-08: la consulta médica en sí (anamnesis, hallazgos, diagnóstico,
 * tratamiento). Una fila por cita -se busca/crea con find-or-create por
 * id_cita, nunca hay dos consultas para la misma cita-.
 *
 * Importante: a diferencia de lo que se podría suponer, en TU base de datos
 * real no existe un estado de cita "Evaluado" independiente. La sección del
 * panel "Evaluados — Pendiente de cierre" (paso 10 del flujo normal) se
 * calcula así: cita.estado_cita sigue siendo "Consulta Médica" TODO el
 * tiempo desde que el médico la inicia hasta que presiona "Finalizar
 * Atención"; lo que cambia es el campo "estado" DE ESTA TABLA (Consulta),
 * que pasa de "En curso" a "Finalizada" cuando el médico guarda con esa
 * opción. ConsultaMedicaService arma las 3 secciones del panel combinando
 * ambos campos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "consultas_medicas", schema = "his")
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consulta")
    private Integer idConsulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico", nullable = false)
    private Usuario medico;

    @Column(name = "motivo_visita", nullable = false, length = 2000)
    private String motivoVisita;

    @Column(name = "hallazgos_clinicos", columnDefinition = "TEXT")
    private String hallazgosClinicos;

    @Column(name = "codigo_cie10", length = 10)
    private String codigoCie10;

    @Column(name = "diagnostico", columnDefinition = "TEXT")
    private String diagnostico;

    @Column(name = "plan_tratamiento", columnDefinition = "TEXT")
    private String planTratamiento;

    @Column(name = "notas_adicionales", columnDefinition = "TEXT")
    private String notasAdicionales;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = EN_CURSO;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    public static final String EN_CURSO = "En curso";
    public static final String FINALIZADA = "Finalizada";

    @PrePersist
    void alCrear() {
        if (this.fechaInicio == null) {
            this.fechaInicio = LocalDateTime.now();
        }
        if (this.estado == null) {
            this.estado = EN_CURSO;
        }
    }
}
