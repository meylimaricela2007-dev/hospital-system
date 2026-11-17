package com.sistema.hospital.repository;

import com.sistema.hospital.model.OrdenLaboratorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdenLaboratorioRepository extends JpaRepository<OrdenLaboratorio, Integer> {
    List<OrdenLaboratorio> findByConsulta_IdConsulta(Integer idConsulta);

    // -----------------------------------------------------------------
    // CU-09, paso 1: tabla "Órdenes de Laboratorio" con filtros por
    // estado, paciente (nombre o DPI) y médico. Un filtro vacío ('') no
    // filtra nada. Se usan cadenas vacías en vez de null a propósito:
    // PostgreSQL no sabe qué tipo darle a un parámetro null dentro de un
    // LIKE y lanza "could not determine data type of parameter".
    // -----------------------------------------------------------------
    @Query("""
            SELECT o FROM OrdenLaboratorio o
              JOIN o.paciente p
              JOIN o.medico m
              JOIN o.estadoOrden e
             WHERE (:estado = '' OR e.nombreEstado = :estado)
               AND (:paciente = ''
                    OR LOWER(p.nombreCompleto) LIKE LOWER(CONCAT('%', :paciente, '%'))
                    OR p.dpi LIKE CONCAT('%', :paciente, '%'))
               AND (:medico = '' OR LOWER(m.nombreCompleto) LIKE LOWER(CONCAT('%', :medico, '%')))
            """)
    Page<OrdenLaboratorio> buscarConFiltros(@Param("estado") String estado,
                                            @Param("paciente") String paciente,
                                            @Param("medico") String medico,
                                            Pageable pageable);

    // -----------------------------------------------------------------
    // CU-10, paso 2-3: órdenes pendientes de pago (no externas: una orden
    // externa se realiza fuera del hospital y no se cobra en Caja).
    // -----------------------------------------------------------------
    Optional<OrdenLaboratorio> findByNumeroOrdenIgnoreCaseAndEstadoOrden_NombreEstadoAndEsExternaFalse(
            String numeroOrden, String nombreEstado);

    List<OrdenLaboratorio> findByPaciente_DpiAndEstadoOrden_NombreEstadoAndEsExternaFalseOrderByFechaCreacionAsc(
            String dpi, String nombreEstado);
}
