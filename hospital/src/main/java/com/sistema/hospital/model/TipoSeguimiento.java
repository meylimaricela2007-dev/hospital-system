package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** CU-08, FA02: motivo de una cita de seguimiento (ya tiene datos en tu BD). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tipos_seguimiento", schema = "his")
public class TipoSeguimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_seguimiento")
    private Integer idTipoSeguimiento;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;
}
