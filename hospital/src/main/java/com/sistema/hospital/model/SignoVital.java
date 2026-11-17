package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CU-07: un registro de signos vitales tomado por Enfermería antes de la
 * consulta médica. Guarda los 5 valores que pide el flujo normal (presión
 * arterial, temperatura, peso, talla, frecuencia cardíaca) más el indicador
 * de emergencia (FA01) y las alertas clínicas detectadas al momento (FA03),
 * que quedan en el expediente según la postcondición del caso de uso.
 *
 * NUEVO EN BASE DE DATOS: esta tabla todavía no existe. Créala en DBeaver
 * antes de levantar la aplicación:
 *
 *   CREATE TABLE his.signos_vitales (
 *       id_signo_vital       SERIAL PRIMARY KEY,
 *       id_cita              INTEGER NOT NULL REFERENCES his.citas(id_cita),
 *       id_enfermero         INTEGER NOT NULL REFERENCES his.usuarios(id_usuario),
 *       presion_sistolica    INTEGER NOT NULL,
 *       presion_diastolica   INTEGER NOT NULL,
 *       temperatura          NUMERIC(4,1) NOT NULL,
 *       peso                 NUMERIC(5,2) NOT NULL,
 *       talla                NUMERIC(5,1) NOT NULL,
 *       frecuencia_cardiaca  INTEGER NOT NULL,
 *       es_emergencia        BOOLEAN NOT NULL DEFAULT FALSE,
 *       alertas_clinicas     TEXT NULL,
 *       fecha_registro       TIMESTAMP NOT NULL
 *   );
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "signos_vitales", schema = "his")
public class SignoVital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_signos_vitales")
    private Integer idSignoVital;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_enfermero", nullable = false)
    private Usuario enfermero;

    @Column(name = "presion_sistolica", nullable = false)
    private Integer presionSistolica;

    @Column(name = "presion_diastolica", nullable = false)
    private Integer presionDiastolica;

    @Column(name = "temperatura", nullable = false)
    private BigDecimal temperatura;

    @Column(name = "peso", nullable = false)
    private BigDecimal peso;

    @Column(name = "talla", nullable = false)
    private BigDecimal talla;

    @Column(name = "frecuencia_cardiaca", nullable = false)
    private Integer frecuenciaCardiaca;

    @Column(name = "es_emergencia", nullable = false)
    private Boolean esEmergencia = false;

    // FA03 + postcondición: texto con las alertas clínicas detectadas al
    // momento de registrar (una por línea). Null si ninguna se disparó.
    @Column(name = "alertas_clinicas", columnDefinition = "TEXT")
    private String alertasClinicas;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @PrePersist
    void alCrear() {
        this.fechaRegistro = LocalDateTime.now();
    }
}
