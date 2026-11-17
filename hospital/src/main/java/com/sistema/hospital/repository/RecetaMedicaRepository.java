package com.sistema.hospital.repository;

import com.sistema.hospital.model.RecetaMedica;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecetaMedicaRepository extends JpaRepository<RecetaMedica, Integer> {

    /** QA: bloquea la receta mientras se despacha, para que dos farmacéuticos no la despachen dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecetaMedica r where r.idReceta = :id")
    Optional<RecetaMedica> buscarParaDespachar(@Param("id") Integer id);

    List<RecetaMedica> findByConsulta_IdConsulta(Integer idConsulta);

    // CU-11, paso 2: solo recetas activas (state = 1), con los filtros ID de Receta / ID de Consulta
    List<RecetaMedica> findByActivaTrueOrderByFechaEmisionDesc(Pageable pageable);
    List<RecetaMedica> findByActivaTrueAndIdRecetaOrderByFechaEmisionDesc(Integer idReceta, Pageable pageable);
    List<RecetaMedica> findByActivaTrueAndConsulta_IdConsultaOrderByFechaEmisionDesc(Integer idConsulta, Pageable pageable);
    List<RecetaMedica> findByActivaTrueAndIdRecetaAndConsulta_IdConsultaOrderByFechaEmisionDesc(Integer idReceta, Integer idConsulta, Pageable pageable);
}
