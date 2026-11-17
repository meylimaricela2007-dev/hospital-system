package com.sistema.hospital.repository;

import com.sistema.hospital.model.TareaMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TareaMedicoRepository extends JpaRepository<TareaMedico, Integer> {

    List<TareaMedico> findByMedico_IdUsuarioOrderByFechaLimiteAscIdTareaAsc(Integer idMedico);

    List<TareaMedico> findByMedico_IdUsuarioAndFechaLimiteOrderByIdTareaAsc(Integer idMedico, LocalDate fechaLimite);
}
