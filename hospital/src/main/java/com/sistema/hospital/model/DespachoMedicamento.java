package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CU-11: un despacho de medicamentos de una receta. El cobro físico queda
 * fuera del sistema (el despacho solo guarda el monto calculado).
 * "Cancelado" (FA03) deja constancia de que el paciente no adquirió los
 * medicamentos, con monto 0 y sin detalle.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "despachos_medicamentos", schema = "his")
public class DespachoMedicamento {

    public static final String COMPLETADO = "Completado";
    public static final String CANCELADO = "Cancelado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_despacho")
    private Integer idDespacho;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_receta", nullable = false)
    private RecetaMedica receta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_farmaceutico", nullable = false)
    private Usuario farmaceutico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = COMPLETADO;

    @Column(name = "fecha_despacho", nullable = false)
    private LocalDateTime fechaDespacho;

    @OneToMany(mappedBy = "despacho", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleDespacho> detalles = new ArrayList<>();

    @PrePersist
    void alCrear() {
        this.fechaDespacho = LocalDateTime.now();
    }
}
