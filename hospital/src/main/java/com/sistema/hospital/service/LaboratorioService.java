package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * CU-09: Gestión de Laboratorio.
 * Reglas clave:
 *  - [RN-CU09-01] No se registran resultados hasta que la orden esté pagada
 *    en Caja (CU-10), es decir, en estado "En Proceso".
 *  - La publicación es INDIVIDUAL por examen (paso 11), no masiva.
 *  - [RNF-024] Un resultado publicado ya no se puede modificar.
 *  - Cuando el último examen se publica, la orden pasa a "Completada".
 */
@Service
public class LaboratorioService {

    @Autowired
    private OrdenLaboratorioRepository ordenLaboratorioRepository;
    @Autowired
    private DetalleOrdenExamenRepository detalleOrdenExamenRepository;
    @Autowired
    private EstadoOrdenLaboratorioRepository estadoOrdenLaboratorioRepository;

    // =================================================================
    // Paso 1: tabla de órdenes con filtros y paginación
    // =================================================================
    @Transactional(readOnly = true)
    public Page<OrdenLaboratorioListaDTO> listarOrdenes(String estado, String paciente, String medico, int page, int size) {
        int tamano = (size == 10 || size == 25 || size == 50) ? size : 10;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), tamano, Sort.by(Sort.Direction.DESC, "fechaCreacion"));

        return ordenLaboratorioRepository
                .buscarConFiltros(limpiar(estado), limpiar(paciente), limpiar(medico), pageable)
                .map(LaboratorioService::convertirALista);
    }

    // =================================================================
    // Pasos 2-3: detalle de la orden
    // =================================================================
    @Transactional(readOnly = true)
    public OrdenLaboratorioDetalleDTO obtenerDetalle(Integer idOrden) {
        OrdenLaboratorio orden = buscarOrden(idOrden);

        List<ExamenOrdenDTO> examenes = orden.getDetalles().stream()
                .sorted(Comparator.comparing(DetalleOrdenExamen::getIdDetalleOrden))
                .map(LaboratorioService::convertirExamen)
                .toList();

        return new OrdenLaboratorioDetalleDTO(
                orden.getIdOrden(),
                orden.getNumeroOrden(),
                orden.getPaciente().getNombreCompleto(),
                orden.getPaciente().getDpi(),
                orden.getMedico().getNombreCompleto(),
                orden.getEstadoOrden().getNombreEstado(),
                Boolean.TRUE.equals(orden.getEsExterna()),
                estaPagada(orden),
                orden.getNotas(),
                orden.getMontoTotal(),
                orden.getFechaCreacion(),
                examenes
        );
    }

    // =================================================================
    // Pasos 9-10 / FA02: guardar el resultado de un examen
    // =================================================================
    @Transactional
    public ResultadoLaboratorioResponse guardarResultado(Integer idOrden, Integer idDetalle, GuardarResultadoRequest request) {
        OrdenLaboratorio orden = buscarOrden(idOrden);
        validarOrdenProcesable(orden);

        DetalleOrdenExamen detalle = buscarDetalle(idOrden, idDetalle);
        if (Boolean.TRUE.equals(detalle.getPublicado())) {
            // RNF-024: los resultados publicados son inmutables
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El resultado ya fue publicado y no puede modificarse.");
        }
        if (request.getFechaResultado().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha del resultado no puede ser una fecha futura.");
        }

        detalle.setValorResultado(request.getValorResultado().trim());
        detalle.setUnidadResultado(vacioANull(request.getUnidadResultado()));
        detalle.setFechaResultado(request.getFechaResultado());
        detalle.setFueraDeRango(Boolean.TRUE.equals(request.getFueraDeRango())); // FA02: marcado manual
        detalle.setNotasResultado(vacioANull(request.getNotasResultado()));
        detalleOrdenExamenRepository.save(detalle);

        return new ResultadoLaboratorioResponse(
                "Resultado guardado exitosamente.",
                convertirExamen(detalle),
                orden.getEstadoOrden().getNombreEstado(),
                false
        );
    }

    // =================================================================
    // Pasos 11-14: publicar el resultado de un examen (uno por uno)
    // =================================================================
    @Transactional
    public ResultadoLaboratorioResponse publicarResultado(Integer idOrden, Integer idDetalle) {
        OrdenLaboratorio orden = buscarOrden(idOrden);
        validarOrdenProcesable(orden);

        DetalleOrdenExamen detalle = buscarDetalle(idOrden, idDetalle);
        if (Boolean.TRUE.equals(detalle.getPublicado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este resultado ya fue publicado.");
        }
        if (detalle.getValorResultado() == null || detalle.getValorResultado().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe guardar el resultado antes de publicarlo.");
        }

        detalle.setPublicado(true);
        detalleOrdenExamenRepository.save(detalle);

        // Paso 14: cuando TODOS los exámenes están publicados, la orden se completa.
        boolean todosPublicados = orden.getDetalles().stream()
                .allMatch(d -> Boolean.TRUE.equals(d.getPublicado()));
        if (todosPublicados) {
            orden.setEstadoOrden(buscarEstadoOrden(EstadoOrdenLaboratorio.COMPLETADA));
            ordenLaboratorioRepository.save(orden);
        }

        String mensaje = todosPublicados
                ? "Resultado publicado exitosamente. Todos los resultados fueron publicados: la orden quedó Completada."
                : "Resultado publicado exitosamente.";

        return new ResultadoLaboratorioResponse(
                mensaje,
                convertirExamen(detalle),
                orden.getEstadoOrden().getNombreEstado(),
                todosPublicados
        );
    }

    // =================================================================
    // Helpers
    // =================================================================

    /**
     * RN-CU09-01 y FA01: solo se procesan órdenes internas que ya fueron
     * pagadas ("En Proceso"). Una orden Completada ya tiene todo publicado.
     */
    private void validarOrdenProcesable(OrdenLaboratorio orden) {
        if (Boolean.TRUE.equals(orden.getEsExterna())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta es una orden externa: los exámenes se realizan en un laboratorio externo.");
        }
        String estado = orden.getEstadoOrden().getNombreEstado();
        if (EstadoOrdenLaboratorio.PENDIENTE.equals(estado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pago debe estar completado antes de proceder con la toma de muestras.");
        }
        if (!EstadoOrdenLaboratorio.EN_PROCESO.equals(estado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La orden está en estado '" + estado + "' y ya no admite cambios.");
        }
    }

    private static boolean estaPagada(OrdenLaboratorio orden) {
        String estado = orden.getEstadoOrden().getNombreEstado();
        return EstadoOrdenLaboratorio.EN_PROCESO.equals(estado) || EstadoOrdenLaboratorio.COMPLETADA.equals(estado);
    }

    private OrdenLaboratorio buscarOrden(Integer idOrden) {
        return ordenLaboratorioRepository.findById(idOrden)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La orden de laboratorio no existe."));
    }

    private DetalleOrdenExamen buscarDetalle(Integer idOrden, Integer idDetalle) {
        return detalleOrdenExamenRepository.findByIdDetalleOrdenAndOrden_IdOrden(idDetalle, idOrden)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El examen no pertenece a esta orden de laboratorio."));
    }

    private EstadoOrdenLaboratorio buscarEstadoOrden(String nombre) {
        return estadoOrdenLaboratorioRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de orden '" + nombre + "' no existe en el catálogo."));
    }

    static OrdenLaboratorioListaDTO convertirALista(OrdenLaboratorio o) {
        int publicados = (int) o.getDetalles().stream().filter(d -> Boolean.TRUE.equals(d.getPublicado())).count();
        return new OrdenLaboratorioListaDTO(
                o.getIdOrden(),
                o.getNumeroOrden(),
                o.getPaciente().getNombreCompleto(),
                o.getPaciente().getDpi(),
                o.getMedico().getNombreCompleto(),
                o.getEstadoOrden().getNombreEstado(),
                Boolean.TRUE.equals(o.getEsExterna()),
                o.getDetalles().size(),
                publicados,
                o.getMontoTotal(),
                o.getFechaCreacion()
        );
    }

    static ExamenOrdenDTO convertirExamen(DetalleOrdenExamen d) {
        ExamenLaboratorio examen = d.getExamen();
        return new ExamenOrdenDTO(
                d.getIdDetalleOrden(),
                examen.getNombre(),
                d.getMonto(),
                examen.getRangoReferencia(),
                examen.getUnidadMedida(),
                d.getValorResultado(),
                d.getUnidadResultado(),
                d.getFechaResultado(),
                Boolean.TRUE.equals(d.getFueraDeRango()),
                d.getNotasResultado(),
                Boolean.TRUE.equals(d.getPublicado())
        );
    }

    private static String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String vacioANull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
