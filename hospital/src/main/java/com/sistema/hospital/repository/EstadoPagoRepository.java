package com.sistema.hospital.repository;

import com.sistema.hospital.model.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoPagoRepository extends JpaRepository<EstadoPago, Integer> {
    Optional<EstadoPago> findByNombreEstado(String nombreEstado);
}
