package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * CU-13: Especialidades por Sede.
 * El Administrador configura qué especialidades ofrece cada sede; esa
 * configuración es la que ve el paciente en el paso 2 del wizard de CU-03.
 *
 * Eliminar es LÓGICO (estado = false), igual que el resto de catálogos
 * (CU-14): así el historial de citas no pierde la referencia. Si luego se
 * vuelve a asignar la misma combinación, se reactiva el mismo registro en
 * vez de crear uno nuevo.
 */
@Service
public class SedeEspecialidadService {

    @Autowired
    private SedeEspecialidadRepository sedeEspecialidadRepository;
    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private EspecialidadRepository especialidadRepository;
    @Autowired
    private AuditoriaService auditoriaService;

    // Paso 3: listado con filtro por ID y paginación
    @Transactional(readOnly = true)
    public Page<SedeEspecialidadDTO> listar(String id, int page, int size) {
        int tamano = (size == 10 || size == 25 || size == 50) ? size : 10;
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamano, Sort.by(Sort.Direction.ASC, "idSedeEspecialidad"));

        Page<SedeEspecialidad> resultado;
        if (id == null || id.isBlank()) {
            resultado = sedeEspecialidadRepository.findAll(pageable);
        } else {
            try {
                resultado = sedeEspecialidadRepository.findByIdSedeEspecialidad(Integer.parseInt(id.trim()), pageable);
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID debe ser un número.");
            }
        }
        return resultado.map(SedeEspecialidadService::convertir);
    }

    // Paso 5: dropdowns del formulario (solo sedes y especialidades activas)
    @Transactional(readOnly = true)
    public List<CatalogoItemDTO> sedesActivas() {
        return sucursalRepository.findByEstadoTrue().stream()
                .map(s -> new CatalogoItemDTO(s.getIdSucursal(), s.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoItemDTO> especialidadesActivas() {
        return especialidadRepository.findByEstadoTrue().stream()
                .map(e -> new CatalogoItemDTO(e.getIdEspecialidad(), e.getNombre()))
                .toList();
    }

    // Pasos 7-9: asignar
    @CacheEvict(cacheNames = {"sucursales", "especialidadesPorSede", "medicosPorEspecialidad", "examenesLaboratorio", "medicamentosCatalogo", "tiposSeguimiento", "prioridades"}, allEntries = true)
    @Transactional
    public String asignar(AsignarSedeEspecialidadRequest request, Integer idUsuarioSesion) {
        Sucursal sede = sucursalRepository.findById(request.getIdSucursal())
                .filter(s -> Boolean.TRUE.equals(s.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar una sede."));
        Especialidad especialidad = especialidadRepository.findById(request.getIdEspecialidad())
                .filter(e -> Boolean.TRUE.equals(e.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar una especialidad."));

        SedeEspecialidad existente = sedeEspecialidadRepository
                .findBySucursal_IdSucursalAndEspecialidad_IdEspecialidad(sede.getIdSucursal(), especialidad.getIdEspecialidad())
                .orElse(null);

        SedeEspecialidad asignacion;
        if (existente != null) {
            if (Boolean.TRUE.equals(existente.getEstado())) {
                // FA05: validación server-side, índice único (Sede, Especialidad)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La asignación ya existe.");
            }
            existente.setEstado(true); // estaba eliminada (lógicamente): se reactiva
            asignacion = existente;
        } else {
            asignacion = new SedeEspecialidad();
            asignacion.setSucursal(sede);
            asignacion.setEspecialidad(especialidad);
            asignacion.setEstado(true); // RN-CU12-01: el estado se asigna automáticamente como activo
        }
        asignacion = sedeEspecialidadRepository.save(asignacion);

        auditoriaService.registrar(idUsuarioSesion, "ASIGNAR_ESPECIALIDAD_SEDE", "sedes_especialidades",
                asignacion.getIdSedeEspecialidad(),
                "Se asignó la especialidad '" + especialidad.getNombre() + "' a la sede '" + sede.getNombre() + "'.");

        return "Especialidad asignada a la sede correctamente";
    }

    // FA02: eliminar (lógico)
    @CacheEvict(cacheNames = {"sucursales", "especialidadesPorSede", "medicosPorEspecialidad", "examenesLaboratorio", "medicamentosCatalogo", "tiposSeguimiento", "prioridades"}, allEntries = true)
    @Transactional
    public String eliminar(Integer id, Integer idUsuarioSesion) {
        SedeEspecialidad asignacion = sedeEspecialidadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La asignación no existe."));
        if (!Boolean.TRUE.equals(asignacion.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La asignación ya fue eliminada.");
        }
        asignacion.setEstado(false);
        sedeEspecialidadRepository.save(asignacion);

        auditoriaService.registrar(idUsuarioSesion, "ELIMINAR_ESPECIALIDAD_SEDE", "sedes_especialidades", id,
                "Se quitó la especialidad '" + asignacion.getEspecialidad().getNombre()
                        + "' de la sede '" + asignacion.getSucursal().getNombre() + "'.");

        return "Asignación eliminada correctamente.";
    }

    private static SedeEspecialidadDTO convertir(SedeEspecialidad s) {
        boolean activo = Boolean.TRUE.equals(s.getEstado());
        return new SedeEspecialidadDTO(
                s.getIdSedeEspecialidad(),
                s.getSucursal().getIdSucursal(),
                s.getSucursal().getNombre(),
                s.getEspecialidad().getIdEspecialidad(),
                s.getEspecialidad().getNombre(),
                activo,
                activo ? "Activo" : "Inactivo"
        );
    }
}
