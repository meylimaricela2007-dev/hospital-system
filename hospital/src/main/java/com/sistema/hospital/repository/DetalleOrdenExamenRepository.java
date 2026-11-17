package com.sistema.hospital.repository;

import com.sistema.hospital.model.DetalleOrdenExamen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** CU-09: cada examen dentro de una orden, donde se guarda su resultado. */
public interface DetalleOrdenExamenRepository extends JpaRepository<DetalleOrdenExamen, Integer> {

    /** Busca el examen SOLO si pertenece a la orden indicada (evita editar un examen de otra orden). */
    Optional<DetalleOrdenExamen> findByIdDetalleOrdenAndOrden_IdOrden(Integer idDetalleOrden, Integer idOrden);
}
