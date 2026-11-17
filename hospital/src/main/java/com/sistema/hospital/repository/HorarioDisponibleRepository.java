package com.sistema.hospital.repository;

import com.sistema.hospital.model.HorarioDisponible;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HorarioDisponibleRepository extends JpaRepository<HorarioDisponible, Integer> {

    /**
     * QA: lee el horario bloqueando la fila (SELECT ... FOR UPDATE). Sin esto, dos pacientes
     * que confirman el mismo horario al mismo tiempo podían reservarlo los dos (doble reserva).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HorarioDisponible h where h.idHorario = :id")
    Optional<HorarioDisponible> buscarParaReservar(@Param("id") Integer id);

    /**
     * Horarios que el paciente puede reservar: los que están "Disponible",
     * más los "Reservado" cuyo tiempo de reserva ya venció (otro paciente
     * empezó a pagar y no terminó a tiempo).
     */
    @Query("""
            SELECT h FROM HorarioDisponible h
            WHERE h.medico.idUsuario = :idMedico
              AND h.especialidad.idEspecialidad = :idEspecialidad
              AND h.sucursal.idSucursal = :idSucursal
              AND h.fecha >= CURRENT_DATE
              AND (h.estado = 'Disponible'
                   OR (h.estado = 'Reservado' AND h.reservadoHasta < :ahora))
            ORDER BY h.fecha, h.horaInicio
            """)
    List<HorarioDisponible> buscarDisponibles(@Param("idMedico") Integer idMedico,
                                               @Param("idSucursal") Integer idSucursal,
                                               @Param("idEspecialidad") Integer idEspecialidad,
                                               @Param("ahora") LocalDateTime ahora);
}
