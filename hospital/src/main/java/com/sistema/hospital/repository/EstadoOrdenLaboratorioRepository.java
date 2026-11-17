package com.sistema.hospital.repository;

import com.sistema.hospital.model.EstadoOrdenLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoOrdenLaboratorioRepository extends JpaRepository<EstadoOrdenLaboratorio, Integer> {
    Optional<EstadoOrdenLaboratorio> findByNombreEstado(String nombreEstado);
}
