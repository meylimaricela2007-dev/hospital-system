package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.service.CatalogoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Alimenta el wizard de citas.js y el panel del médico (CU-08) con catálogos de solo lectura. */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/sucursales")
    public List<CatalogoItemDTO> sucursales() {
        return catalogoService.obtenerSucursales().stream()
                .map(s -> new CatalogoItemDTO(s.getIdSucursal(), s.getNombre()))
                .toList();
    }
    
    @GetMapping("/especialidades-por-sucursal/{idSucursal}")
    public List<CatalogoItemDTO> especialidadesPorSucursal(@PathVariable Integer idSucursal) {
        return catalogoService.obtenerEspecialidadesPorSede(idSucursal);
    }

    @GetMapping("/medicos-por-especialidad/{idEspecialidad}")
    public List<MedicoDTO> medicosPorEspecialidad(@PathVariable Integer idEspecialidad,
                                                   @RequestParam Integer sucursal) {
        return catalogoService.obtenerMedicosPorEspecialidad(sucursal, idEspecialidad);
    }

    // ---------- CU-08 ----------

    @GetMapping("/examenes-laboratorio")
    public List<ExamenLaboratorioDTO> examenesLaboratorio() {
        return catalogoService.obtenerExamenesLaboratorio();
    }

    @GetMapping("/medicamentos")
    public List<MedicamentoDTO> medicamentos() {
        return catalogoService.obtenerMedicamentos();
    }

    @GetMapping("/tipos-seguimiento")
    public List<TipoSeguimientoDTO> tiposSeguimiento() {
        return catalogoService.obtenerTiposSeguimiento();
    }

    /** CU-12, paso 6: prioridades para la cita de seguimiento. */
    @GetMapping("/prioridades")
    public List<CatalogoItemDTO> prioridades() {
        return catalogoService.obtenerPrioridades();
    }

    /** CU-08, paso 6: autocompletado de CIE-10 mientras el médico escribe. */
    @GetMapping("/cie10")
    public List<Cie10DTO> cie10(@RequestParam(required = false) String buscar) {
        return catalogoService.buscarCie10(buscar);
    }
}
