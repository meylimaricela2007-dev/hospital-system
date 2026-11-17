package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado de una orden de laboratorio:
 *   Pendiente  -> la generó el médico (CU-08) y aún no se ha pagado.
 *   En Proceso -> Caja cobró la orden (CU-10); ya se pueden tomar muestras.
 *   Completada -> Laboratorio publicó TODOS los resultados (CU-09).
 *   Cancelada  -> reservado para uso futuro.
 * OJO: el documento dice "En proceso (orderStatus = 1)", pero en la tabla
 * estados_orden_laboratorio el id 1 es "Pendiente" y el 2 es "En Proceso".
 * Por eso SIEMPRE se busca el estado por su NOMBRE (estas constantes),
 * nunca por su número.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "estados_orden_laboratorio", schema = "his")
public class EstadoOrdenLaboratorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_orden")
    private Integer idEstadoOrden;

    @Column(name = "nombre_estado", nullable = false, length = 30)
    private String nombreEstado;

    public static final String PENDIENTE = "Pendiente";
    public static final String EN_PROCESO = "En Proceso";
    public static final String COMPLETADA = "Completada";
    public static final String CANCELADA = "Cancelada";
}
