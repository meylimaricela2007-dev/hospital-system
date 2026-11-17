package com.sistema.hospital.repository;

import com.sistema.hospital.model.Cita;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    /**
     * QA: lee la cita bloqueando la fila (SELECT ... FOR UPDATE) hasta que termine la
     * transacción. Evita que dos solicitudes simultáneas (doble clic en "Pagar", dos
     * cajeros, dos recepcionistas) procesen la misma cita a la vez y generen dos pagos
     * o dos cambios de estado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cita c where c.idCita = :id")
    Optional<Cita> buscarParaActualizar(@Param("id") Integer id);

    /** QA: ¿otra cita (distinta de la indicada) sigue usando este horario en un estado que no sea alguno de los excluidos? */
    boolean existsByHorario_IdHorarioAndIdCitaNotAndEstadoCita_NombreEstadoNotIn(
            Integer idHorario, Integer idCita, Collection<String> estadosExcluidos);

    /**
     * CORREGIDO (CU-05, FA02): antes filtrábamos por "fechaCita = hoy", con
     * la idea de que Recepción solo atiende citas del día. En la práctica
     * eso causaba que la búsqueda por DPI dijera "el paciente no tiene citas
     * activas" para una cita que sí existía y que la búsqueda por número de
     * cita sí encontraba sin problema (esa búsqueda nunca filtró por fecha).
     * Ahora buscamos TODAS las citas del paciente, sin importar la fecha —
     * igual que ya hace CajaService.buscarPorDpi (ver el método de abajo) —
     * y RecepcionService.buscarPorDpi toma la más próxima que no esté
     * Cancelada/Expirada.
     */
    List<Cita> findByPaciente_IdUsuarioOrderByFechaCitaAscHoraCitaAsc(Integer idPaciente);

    /**
     * NUEVO: listado "Mis Citas" del paciente (portal), de más reciente a
     * más antigua -a diferencia del método de arriba, que Recepción usa
     * ascendente porque busca "la próxima cita activa"-.
     */
    List<Cita> findByPaciente_IdUsuarioOrderByFechaCitaDescHoraCitaDesc(Integer idPaciente);

    /**
     * CU-06, paso 1 (búsqueda por DPI en Caja): a diferencia de Recepción,
     * aquí no importa la fecha -una cita "Pendiente de pago" de cualquier
     * día debería poder cobrarse-, así que filtramos directamente por
     * estado. CajaService recorre la lista y usa la primera que no esté
     * vencida (ver CajaService.estaExpirada).
     */
    List<Cita> findByPaciente_IdUsuarioAndEstadoCita_NombreEstadoOrderByFechaCitaAscHoraCitaAsc(Integer idPaciente, String nombreEstado);

    /**
     * CU-07, paso 1 del flujo normal: la cola de Enfermería. Trae las citas
     * de HOY que están "Paciente Presente" (esperando ser llamadas) o
     * "Signos Vitales" (ya llamadas, en proceso); SignosVitalesService
     * separa ambos grupos para pintar las dos columnas del panel.
     */
    List<Cita> findByFechaCitaAndEstadoCita_NombreEstadoInOrderByHoraCitaAsc(LocalDate fechaCita, List<String> nombresEstado);

    /**
     * CU-08, paso 1 del flujo normal: panel del médico. Trae SOLO las citas
     * de ESE médico, de hoy, en "En Espera" o "Consulta Médica"
     * -ConsultaMedicaService separa "En Consulta Médica" de "Evaluados —
     * Pendiente de cierre" mirando además el estado de la Consulta
     * asociada, ya que en esta base de datos ambas secciones comparten el
     * mismo estado_cita "Consulta Médica" (ver el comentario en
     * Consulta.java)-.
     */
    List<Cita> findByMedico_IdUsuarioAndFechaCitaAndEstadoCita_NombreEstadoInOrderByHoraCitaAsc(
            Integer idMedico, LocalDate fechaCita, List<String> nombresEstado);

    /** CU-12, RN-CU11-05: citas de seguimiento en un rango de fechas, para el recordatorio automático. */
    List<Cita> findByEsSeguimientoTrueAndFechaCitaBetweenAndEstadoCita_NombreEstadoIn(
            LocalDate desde, LocalDate hasta, List<String> nombresEstado);

    /** CU-16: citas de un médico en un rango de fechas, excluyendo ciertos estados (calendario de la agenda). */
    List<Cita> findByMedico_IdUsuarioAndFechaCitaBetweenAndEstadoCita_NombreEstadoNotInOrderByFechaCitaAscHoraCitaAsc(
            Integer idMedico, LocalDate desde, LocalDate hasta, java.util.Collection<String> estadosExcluidos);
}
