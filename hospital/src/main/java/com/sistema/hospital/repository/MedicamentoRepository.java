package com.sistema.hospital.repository;

import com.sistema.hospital.model.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {
    List<Medicamento> findByEstadoTrueOrderByNombreAsc();
}
