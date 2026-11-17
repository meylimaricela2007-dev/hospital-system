package com.sistema.hospital.repository;

import com.sistema.hospital.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByDpi(String dpi);
    boolean existsByDpi(String dpi);
    boolean existsByCorreoElectronico(String correoElectronico); // correo ya registrado
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);
    Optional<Usuario> findByCorreoElectronicoIgnoreCase(String correoElectronico);

    // el selector Filtrar por campo
    Page<Usuario> findByIdUsuario(Integer id, Pageable pageable);
    Page<Usuario> findByNombreCompletoContainingIgnoreCase(String texto, Pageable pageable);
    Page<Usuario> findByCorreoElectronicoContainingIgnoreCase(String texto, Pageable pageable);
    Page<Usuario> findByRol_NombreRolContainingIgnoreCase(String texto, Pageable pageable);
    Page<Usuario> findByNombreUsuarioContainingIgnoreCase(String texto, Pageable pageable);
    Page<Usuario> findByDpiContainingIgnoreCase(String texto, Pageable pageable);

    /** CU-14: cuántos usuarios tienen un rol (para no desactivar un rol en uso). */
    long countByRol_IdRol(Integer idRol);
}
