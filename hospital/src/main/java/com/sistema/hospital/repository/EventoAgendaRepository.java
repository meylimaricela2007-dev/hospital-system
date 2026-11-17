package com.sistema.hospital.repository;

import com.sistema.hospital.model.EventoAgenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventoAgendaRepository extends JpaRepository<EventoAgenda, Integer> {

    /** Eventos del médico que se cruzan con el rango [inicio, fin). */
    @Query("select e from EventoAgenda e where e.medico.idUsuario = :idMedico and e.fechaInicio < :fin and e.fechaFin > :inicio order by e.fechaInicio")
    List<EventoAgenda> buscarEnRango(@Param("idMedico") Integer idMedico,
                                     @Param("inicio") LocalDateTime inicio,
                                     @Param("fin") LocalDateTime fin);

    /** ¿El médico tiene algún evento que ocupe ese intervalo? Lo usa CU-03 para no ofrecer ese horario. */
    @Query("select count(e) > 0 from EventoAgenda e where e.medico.idUsuario = :idMedico and e.fechaInicio < :fin and e.fechaFin > :inicio")
    boolean existeSolapamiento(@Param("idMedico") Integer idMedico,
                               @Param("inicio") LocalDateTime inicio,
                               @Param("fin") LocalDateTime fin);
}
