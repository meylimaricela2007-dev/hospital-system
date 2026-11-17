package com.sistema.hospital.service;

import com.sistema.hospital.model.Auditoria;
import com.sistema.hospital.repository.AuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Punto único para escribir en la bitácora de auditoría. Si el registro de
 * auditoría llegara a fallar, NO debe tumbar la operación de negocio que ya
 * se hizo, por eso se atrapa cualquier error y solo se deja en consola.
 */
@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    public void registrar(Integer idUsuario, String accion, String entidad, Integer idEntidad, String detalle) {
        try {
            Auditoria a = new Auditoria();
            a.setIdUsuario(idUsuario);
            a.setAccion(accion);
            a.setEntidad(entidad);
            a.setIdEntidad(idEntidad);
            a.setDetalle(detalle);
            auditoriaRepository.save(a);
        } catch (Exception ex) {
            System.err.println("[AuditoriaService] No se pudo registrar la auditoría: " + ex.getMessage());
        }
    }
}
