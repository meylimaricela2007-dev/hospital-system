package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class CatalogoService {

    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private EspecialidadRepository especialidadRepository;
    @Autowired
    private SedeEspecialidadRepository sedeEspecialidadRepository;
    @Autowired
    private MedicoSedeRepository medicoSedeRepository;
    @Autowired
    private ExamenLaboratorioRepository examenLaboratorioRepository;
    @Autowired
    private MedicamentoRepository medicamentoRepository;
    @Autowired
    private TipoSeguimientoRepository tipoSeguimientoRepository;
    @Autowired
    private PrioridadRepository prioridadRepository;
    @Autowired
    private Cie10Repository cie10Repository;

    @Cacheable("sucursales")
    @Transactional(readOnly = true)
    public List<Sucursal> obtenerSucursales() {
        return sucursalRepository.findByEstadoTrue();
    }

    /** Especialidades activas ofrecidas en una sede en concreto. */
    @Cacheable("especialidadesPorSede")
    @Transactional(readOnly = true)
    public List<CatalogoItemDTO> obtenerEspecialidadesPorSede(Integer idSucursal) {
        return sedeEspecialidadRepository.findBySucursal_IdSucursalAndEstadoTrue(idSucursal).stream()
                .map(SedeEspecialidad::getEspecialidad)
                .map((Especialidad e) -> new CatalogoItemDTO(e.getIdEspecialidad(), e.getNombre()))
                .toList();
    }

    /** Médicos que atienden esa especialidad en esa sede. */
    @Cacheable("medicosPorEspecialidad")
    @Transactional(readOnly = true)
    public List<MedicoDTO> obtenerMedicosPorEspecialidad(Integer idSucursal, Integer idEspecialidad) {
        return medicoSedeRepository
                .findBySucursal_IdSucursalAndEspecialidad_IdEspecialidadAndEstadoTrue(idSucursal, idEspecialidad)
                .stream()
                .map(MedicoSede::getMedico)
                // QA: un médico dado de baja (Inactivo) en CU-01 seguía apareciendo para agendar citas.
                .filter(medico -> EstadoUsuario.ACTIVO.equals(medico.getEstadoUsuario().getNombreEstado()))
                .map(medico -> new MedicoDTO(medico.getIdUsuario(), medico.getNombreCompleto(), medico.getDpi()))
                .toList();
    }

    // =================================================================
    // CU-08: catálogos clínicos
    // =================================================================

    /** CU-08, FA01: exámenes de laboratorio disponibles para ordenar. */
    @Cacheable("examenesLaboratorio")
    @Transactional(readOnly = true)
    public List<ExamenLaboratorioDTO> obtenerExamenesLaboratorio() {
        return examenLaboratorioRepository.findByEstadoTrueOrderByNombreAsc().stream()
                .map(e -> new ExamenLaboratorioDTO(e.getIdExamen(), e.getNombre(), e.getLaboratorio().getNombre(), e.getPrecio()))
                .toList();
    }

    /** CU-08, FA04: medicamentos disponibles para recetar. */
    @Cacheable("medicamentosCatalogo")
    @Transactional(readOnly = true)
    public List<MedicamentoDTO> obtenerMedicamentos() {
        return medicamentoRepository.findByEstadoTrueOrderByNombreAsc().stream()
                .map(m -> new MedicamentoDTO(m.getIdMedicamento(), m.getNombre(), m.getUnidad(), m.getPrecio()))
                .toList();
    }

    /** CU-08, FA02: motivos de cita de seguimiento. */
    @Cacheable("tiposSeguimiento")
    @Transactional(readOnly = true)
    public List<TipoSeguimientoDTO> obtenerTiposSeguimiento() {
        return tipoSeguimientoRepository.findAll().stream()
                .map(t -> new TipoSeguimientoDTO(t.getIdTipoSeguimiento(), t.getNombre()))
                .toList();
    }

    /** CU-12, paso 6: prioridades del seguimiento (Baja, Normal, Alta). */
    @Cacheable("prioridades")
    @Transactional(readOnly = true)
    public List<CatalogoItemDTO> obtenerPrioridades() {
        return prioridadRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Prioridad::getIdPrioridad))
                .map(p -> new CatalogoItemDTO(p.getIdPrioridad(), p.getNombre()))
                .toList();
    }

    /** CU-08, paso 4: autocompletado de CIE-10 desde el catálogo de la base de datos (tabla his.cie10). */
    public List<Cie10DTO> buscarCie10(String texto) {
        String buscado = texto == null ? "" : texto.trim();
        return cie10Repository.buscar(buscado, PageRequest.of(0, 15)).stream()
                .map(c -> new Cie10DTO(c.getCodigo(), c.getDescripcion()))
                .toList();
    }
}