package com.sistema.hospital.repository;

import com.sistema.hospital.model.EstadoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoUsuarioRepository extends JpaRepository<EstadoUsuario, Integer> {
    Optional<EstadoUsuario> findByNombreEstado(String nombreEstado);
}
