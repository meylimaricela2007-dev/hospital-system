package com.sistema.hospital.repository;

import com.sistema.hospital.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {
    List<Rol> findByEstadoTrue();
    Optional<Rol> findByNombreRol(String nombreRol);
}
