package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estados por los que pasa una cita, en orden:
 * Pendiente de pago -> Confirmada -> Paciente Presente -> Signos Vitales
 * -> En Espera -> Consulta Médica -> Atención Finalizada
 * (o bien: Cancelada / Expirada / No Asistió en cualquier punto).
 * Por ahora (CU-03/CU-04) solo usamos los dos primeros.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "estados_cita", schema = "his")
public class EstadoCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_cita")
    private Integer idEstadoCita;

    @Column(name = "nombre_estado", nullable = false, unique = true, length = 30)
    private String nombreEstado;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    public static final String PENDIENTE_DE_PAGO = "Pendiente de pago";
    public static final String CONFIRMADA = "Confirmada";
    public static final String EXPIRADA = "Expirada";

    // NUEVO (CU-05): estados que usa la Recepción. Ya estaban contemplados en
    // el comentario de arriba (y, según ese mismo comentario, ya existen como
    // filas en la tabla "estados_cita"); simplemente nadie los había usado
    // todavía desde el código Java.
    public static final String PACIENTE_PRESENTE = "Paciente Presente";
    public static final String CANCELADA = "Cancelada";

    // NUEVO (CU-07): siguientes pasos del recorrido del paciente dentro de la
    // clínica. Mismo caso que arriba: ya estaban contemplados en el comentario
    // de la clase, solo faltaba usarlos desde el código.
    public static final String SIGNOS_VITALES = "Signos Vitales";
    public static final String EN_ESPERA = "En Espera";
    public static final String CONSULTA_MEDICA = "Consulta Médica"; // FA01: emergencia pasa aquí directo

    // NUEVO (CU-08): cierre de la atención. IMPORTANTE — a diferencia de lo
    // que se podría suponer, no existe un estado "Evaluado" en esta base de
    // datos: la cita permanece en CONSULTA_MEDICA desde que el médico la
    // inicia hasta que presiona "Finalizar Atención"; lo que distingue la
    // sección "Evaluados — Pendiente de cierre" del panel es el estado de
    // la Consulta asociada (ver Consulta.java), no un nuevo estado de cita.
    public static final String ATENCION_FINALIZADA = "Atención Finalizada";
    public static final String NO_ASISTIO = "No Asistió"; // FA06
}
