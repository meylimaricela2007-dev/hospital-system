package com.sistema.hospital.repository;

import com.sistema.hospital.model.TipoMovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoMovimientoInventarioRepository extends JpaRepository<TipoMovimientoInventario, Integer> {
    Optional<TipoMovimientoInventario> findByNombre(String nombre);
}
