package com.sistema.hospital.repository;

import com.sistema.hospital.model.InventarioMedicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventarioMedicamentoRepository extends JpaRepository<InventarioMedicamento, Integer> {

    Optional<InventarioMedicamento> findByMedicamento_IdMedicamentoAndSucursal_IdSucursalAndEstadoTrue(Integer idMedicamento, Integer idSucursal);

    /**
     * Descuento atómico: solo descuenta si todavía hay existencias suficientes,
     * así dos despachos simultáneos nunca dejan el stock en negativo. Devuelve
     * 1 si descontó, 0 si ya no alcanzaba. Incrementa version_control.
     */
    @Modifying(flushAutomatically = true)
    @Query("update InventarioMedicamento i set i.stockActual = i.stockActual - :cantidad, i.versionControl = i.versionControl + 1 "
            + "where i.idInventario = :id and i.stockActual >= :cantidad")
    int descontarStock(@Param("id") Integer id, @Param("cantidad") Integer cantidad);

    /** Lee el stock real en la BD (sin pasar por la caché de la sesión), p. ej. justo después de descontar. */
    @Query("select i.stockActual from InventarioMedicamento i where i.idInventario = :id")
    Integer obtenerStock(@Param("id") Integer id);

    /** CU-15: la fila de inventario aunque esté inactiva (para reactivarla en una entrada). */
    Optional<InventarioMedicamento> findByMedicamento_IdMedicamentoAndSucursal_IdSucursal(Integer idMedicamento, Integer idSucursal);

    /** CU-15: entrada de stock atómica (Compra, Devolución, Ajuste+). Reactiva la fila y sube version_control. */
    @Modifying(flushAutomatically = true)
    @Query("update InventarioMedicamento i set i.stockActual = i.stockActual + :cantidad, i.estado = true, i.versionControl = i.versionControl + 1 "
            + "where i.idInventario = :id")
    int aumentarStock(@Param("id") Integer id, @Param("cantidad") Integer cantidad);
}
