package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Efectivo, Visa, Mastercard, Débito. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "metodos_pago", schema = "his")
public class MetodoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo_pago")
    private Integer idMetodoPago;

    @Column(name = "nombre", nullable = false, unique = true, length = 50)
    private String nombre;
}
