package com.sistema.hospital.repository;

import com.sistema.hospital.model.Especialidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspecialidadRepository extends JpaRepository<Especialidad, Integer> {
    List<Especialidad> findByEstadoTrue();
    List<Especialidad> findByEstadoTrueOrderByNombreAsc();
}