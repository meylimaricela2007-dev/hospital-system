package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aprobado, Rechazado, Pendiente, Error de comunicación. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "estados_pago", schema = "his")
public class EstadoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_pago")
    private Integer idEstadoPago;

    @Column(name = "nombre_estado", nullable = false, unique = true, length = 30)
    private String nombreEstado;

    public static final String APROBADO = "Aprobado";
    public static final String RECHAZADO = "Rechazado";
}
