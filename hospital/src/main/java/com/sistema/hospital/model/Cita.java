package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Una cita médica agendada por un paciente (CU-03). Es la tabla central del
 * sistema: signos vitales, consulta, laboratorio y pagos apuntan aquí.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "citas", schema = "his")
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Integer idCita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Usuario paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico", nullable = false)
    private Usuario medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialidad", nullable = false)
    private Especialidad especialidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_horario")
    private HorarioDisponible horario;

    @Column(name = "fecha_cita", nullable = false)
    private LocalDate fechaCita;

    @Column(name = "hora_cita", nullable = false)
    private LocalTime horaCita;

    @Column(name = "motivo_consulta", nullable = false, length = 2000)
    private String motivoConsulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_cita", nullable = false)
    private EstadoCita estadoCita;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    @Column(name = "es_emergencia", nullable = false)
    private Boolean esEmergencia = false;

    @Column(name = "es_seguimiento", nullable = false)
    private Boolean esSeguimiento = false;

    /**
     * CU-08, FA02 "Cita de seguimiento": por qué motivo se pide el
     * seguimiento (catálogo "tipos_seguimiento", ya tiene datos:
     * "Monitoreo de Tratamiento", "Revisión de Resultados de Laboratorio").
     * Null en cualquier cita que no sea de seguimiento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_seguimiento")
    private TipoSeguimiento tipoSeguimiento;

    /**
     * CU-08, FA02: la CONSULTA (no la cita) que originó este seguimiento.
     * Así se puede navegar desde una cita de seguimiento hasta la consulta
     * médica exacta que la generó, incluso si esa consulta corresponde a
     * una cita distinta (la anterior). Null en cualquier otra cita.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta_origen")
    private Consulta consultaOrigen;

    /**
     * CU-12, paso 6: prioridad con la que el médico agenda el seguimiento
     * (catálogo "prioridades": Baja, Normal, Alta). Null en cualquier otra cita.
     * NUEVO EN BASE DE DATOS (ver el script SQL de CU-11 a CU-13):
     *   ALTER TABLE his.citas ADD COLUMN IF NOT EXISTS id_prioridad integer REFERENCES his.prioridades (id_prioridad);
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prioridad")
    private Prioridad prioridad;

    @Column(name = "fecha_expiracion_reserva")
    private LocalDateTime fechaExpiracionReserva;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /**
     * CU-05, paso 8 del flujo normal: la hora exacta en que el Empleado
     * Interno (Recepcionista) registra la llegada del paciente. Antes de
     * este cambio la entidad no tenía dónde guardar ese dato.
     * NUEVO EN BASE DE DATOS: esta columna todavía no existe en tu tabla
     * "his.citas". Como application.properties usa ddl-auto=validate (no crea
     * ni modifica tablas solo), agrégala tú mismo en DBeaver antes de levantar
     * la aplicación:
     *   ALTER TABLE his.citas ADD COLUMN hora_llegada TIMESTAMP NULL;
     */    @Column(name = "hora_llegada")
    private LocalDateTime horaLlegada;

    /**
     * CU-05, FA09 "Error al registrar llegada": @Version activa el bloqueo
     * optimista de JPA. Si dos recepcionistas leen la misma cita y ambos
     * intentan guardarla, el segundo guardado falla con
     * ObjectOptimisticLockingFailureException en vez de sobrescribir en
     * silencio el trabajo del primero. RecepcionService.registrarLlegada()
     * atrapa esa excepción y muestra el mensaje "Operación no permitida"
     * que pide el documento del caso de uso.
     * NUEVO EN BASE DE DATOS: agrega también esta columna:
     *   ALTER TABLE his.citas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
     */
    // QA: estaba comentado aunque la columna "version" ya existe en tu base (BIGINT NOT NULL DEFAULT 0).
    // Sin @Version, el bloqueo optimista de CU-05 FA09 no funcionaba.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
