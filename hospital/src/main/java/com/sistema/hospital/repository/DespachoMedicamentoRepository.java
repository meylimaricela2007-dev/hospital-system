package com.sistema.hospital.repository;

import com.sistema.hospital.model.DespachoMedicamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DespachoMedicamentoRepository extends JpaRepository<DespachoMedicamento, Integer> {
    List<DespachoMedicamento> findTop10BySucursal_IdSucursalOrderByFechaDespachoDesc(Integer idSucursal);
}
