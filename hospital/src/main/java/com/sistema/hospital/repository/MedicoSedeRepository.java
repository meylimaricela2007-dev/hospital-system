package com.sistema.hospital.repository;

import com.sistema.hospital.model.MedicoSede;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicoSedeRepository extends JpaRepository<MedicoSede, Integer> {
    List<MedicoSede> findBySucursal_IdSucursalAndEspecialidad_IdEspecialidadAndEstadoTrue(
            Integer idSucursal, Integer idEspecialidad);
}
