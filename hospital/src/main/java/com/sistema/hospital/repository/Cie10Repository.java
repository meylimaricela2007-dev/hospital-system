package com.sistema.hospital.repository;

import com.sistema.hospital.model.Cie10;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface Cie10Repository extends JpaRepository<Cie10, String> {

    /** Autocompletado: el código empieza con lo escrito, o la descripción lo contiene. Texto vacío = primeros códigos. */
    @Query("""
           SELECT c FROM Cie10 c
           WHERE c.estado = true
             AND (:q = '' OR LOWER(c.codigo) LIKE LOWER(CONCAT(:q, '%'))
                          OR LOWER(c.descripcion) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY c.codigo
           """)
    List<Cie10> buscar(@Param("q") String q, Pageable pageable);
}