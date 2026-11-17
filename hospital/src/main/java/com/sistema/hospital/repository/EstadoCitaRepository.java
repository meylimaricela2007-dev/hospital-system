package com.sistema.hospital.repository;

import com.sistema.hospital.model.EstadoCita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoCitaRepository extends JpaRepository<EstadoCita, Integer> {
    Optional<EstadoCita> findByNombreEstado(String nombreEstado);
}
