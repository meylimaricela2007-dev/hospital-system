package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una sede física del hospital. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sucursales", schema = "his")
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sucursal")
    private Integer idSucursal;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "telefono", length = 8)
    private String telefono;

    @Column(name = "direccion", length = 500)
    private String direccion;

    @Column(name = "descripcion", length = 250)
    private String descripcion;

    @Column(name = "estado", nullable = false)
    private Boolean estado;
}
