package com.sistema.hospital.repository;

import com.sistema.hospital.model.Consulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Integer> {

    /** Find-or-create: cada cita tiene a lo sumo una consulta. */
    Optional<Consulta> findByCita_IdCita(Integer idCita);
}
