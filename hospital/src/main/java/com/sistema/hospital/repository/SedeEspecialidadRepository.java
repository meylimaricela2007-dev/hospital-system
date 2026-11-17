package com.sistema.hospital.repository;

import com.sistema.hospital.model.SedeEspecialidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SedeEspecialidadRepository extends JpaRepository<SedeEspecialidad, Integer> {
    List<SedeEspecialidad> findBySucursal_IdSucursalAndEstadoTrue(Integer idSucursal);

    // CU-13: filtro por ID del listado
    Page<SedeEspecialidad> findByIdSedeEspecialidad(Integer idSedeEspecialidad, Pageable pageable);

    // CU-13, FA05: detectar la combinación Sede + Especialidad ya existente
    Optional<SedeEspecialidad> findBySucursal_IdSucursalAndEspecialidad_IdEspecialidad(Integer idSucursal, Integer idEspecialidad);
}
