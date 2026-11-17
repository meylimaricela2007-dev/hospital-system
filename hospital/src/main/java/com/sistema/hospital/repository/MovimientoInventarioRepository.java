package com.sistema.hospital.repository;

import com.sistema.hospital.model.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Integer>, JpaSpecificationExecutor<MovimientoInventario> {
}
