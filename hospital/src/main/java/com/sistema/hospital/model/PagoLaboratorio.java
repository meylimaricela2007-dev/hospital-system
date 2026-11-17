package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CU-10: el pago de una orden de laboratorio, cobrado presencialmente en
 * Caja. Es el equivalente de PagoCita (CU-06), pero para órdenes de
 * laboratorio. La tabla ya existe en la base de datos: pagos_laboratorio.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pagos_laboratorio", schema = "his")
public class PagoLaboratorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago_laboratorio")
    private Integer idPagoLaboratorio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden", nullable = false)
    private OrdenLaboratorio orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_metodo_pago", nullable = false)
    private MetodoPago metodoPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_pago", nullable = false)
    private EstadoPago estadoPago;

    @Column(name = "numero_transaccion", length = 50)
    private String numeroTransaccion;

    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    // Solo para Efectivo (null si fue con tarjeta)
    @Column(name = "monto_recibido")
    private BigDecimal montoRecibido;

    @Column(name = "cambio_devuelto")
    private BigDecimal cambioDevuelto;

    // Solo para tarjeta (null si fue Efectivo)
    @Column(name = "tarjeta_ultimos4", length = 4)
    private String tarjetaUltimos4;

    // Quién cobró (siempre un Cajero: este pago nunca se hace en línea)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cajero", nullable = false)
    private Usuario cajero;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @PrePersist
    void alCrear() {
        this.fechaPago = LocalDateTime.now();
    }
}
