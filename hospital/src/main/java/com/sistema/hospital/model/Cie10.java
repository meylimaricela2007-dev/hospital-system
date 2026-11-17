package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catálogo CIE-10 (CU-08, paso 4): el médico lo consulta con autocompletado al registrar el diagnóstico. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cie10", schema = "his")
public class Cie10 {

    @Id
    @Column(name = "codigo", length = 10)
    private String codigo;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Column(name = "estado", nullable = false)
    private Boolean estado = true;
}