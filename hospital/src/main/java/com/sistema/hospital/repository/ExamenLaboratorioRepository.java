package com.sistema.hospital.repository;

import com.sistema.hospital.model.ExamenLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamenLaboratorioRepository extends JpaRepository<ExamenLaboratorio, Integer> {
    List<ExamenLaboratorio> findByEstadoTrueOrderByNombreAsc();
}
