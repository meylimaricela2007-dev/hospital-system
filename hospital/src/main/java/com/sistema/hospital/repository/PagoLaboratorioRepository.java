package com.sistema.hospital.repository;

import com.sistema.hospital.model.PagoLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;

/** CU-10: pagos de órdenes de laboratorio registrados en Caja. */
public interface PagoLaboratorioRepository extends JpaRepository<PagoLaboratorio, Integer> {
}
