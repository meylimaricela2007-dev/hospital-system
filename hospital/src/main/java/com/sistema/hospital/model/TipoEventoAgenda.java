package com.sistema.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catálogo "tipos_evento_agenda": Reunión, Descanso, Capacitación, Personal, Otro (CU-16). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tipos_evento_agenda", schema = "his")
public class TipoEventoAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_evento")
    private Integer idTipoEvento;

    @Column(name = "nombre", nullable = false, length = 30)
    private String nombre;
}
