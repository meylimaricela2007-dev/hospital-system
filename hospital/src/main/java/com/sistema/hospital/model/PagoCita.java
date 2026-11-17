package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** El pago de una cita: en línea con tarjeta (CU-04). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pagos_citas", schema = "his")
public class PagoCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Integer idPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_metodo_pago", nullable = false)
    private MetodoPago metodoPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_pago", nullable = false)
    private EstadoPago estadoPago;

    @Column(name = "numero_transaccion", unique = true, length = 50)
    private String numeroTransaccion;

    @Column(name = "uuid_idempotencia", nullable = false, unique = true)
    private UUID uuidIdempotencia;

    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    @Column(name = "moneda", nullable = false, length = 10)
    private String moneda = "GTQ";

    @Column(name = "tarjeta_ultimos4", length = 4)
    private String tarjetaUltimos4;

    @Column(name = "nombre_titular", length = 100)
    private String nombreTitular;

    @Column(name = "mensaje_respuesta", length = 255)
    private String mensajeRespuesta;

    /**
     * CU-06, paso 6-7 del flujo normal: solo se usan cuando el método de
     * pago es "Efectivo" (quedan en null para pagos con tarjeta). Guardamos
     * ambos valores -no solo el cambio- para tener el monto que el Cajero
     * recibió realmente, útil para el arqueo de caja más adelante.
     * NUEVO EN BASE DE DATOS:
     *   ALTER TABLE his.pagos_citas ADD COLUMN monto_recibido NUMERIC(10,2) NULL;
     *   ALTER TABLE his.pagos_citas ADD COLUMN cambio_devuelto NUMERIC(10,2) NULL;
     */
    @Column(name = "monto_recibido")
    private BigDecimal montoRecibido;

    @Column(name = "cambio_devuelto")
    private BigDecimal cambioDevuelto;

    /**
     * CU-06 vs CU-04: quién cobró. Null para los pagos en línea (CU-04), que
     * los procesa el propio paciente sin intervención de un Cajero.
     * NUEVO EN BASE DE DATOS:
     *   ALTER TABLE his.pagos_citas ADD COLUMN id_cajero INTEGER NULL
     *     REFERENCES his.usuarios(id_usuario);
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cajero")
    private Usuario cajero;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @PrePersist
    void alCrear() {
        if (this.uuidIdempotencia == null) {
            this.uuidIdempotencia = UUID.randomUUID();
        }
        this.fechaPago = LocalDateTime.now();
    }
}
