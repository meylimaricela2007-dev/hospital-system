package com.sistema.hospital.controller;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.FarmaciaService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** CU-11: Despacho de medicamentos. Solo el rol Farmacéutico. */
@RestController
@RequestMapping("/api/farmacia")
public class FarmaciaController {

    private final FarmaciaService farmaciaService;

    public FarmaciaController(FarmaciaService farmaciaService) {
        this.farmaciaService = farmaciaService;
    }

    @GetMapping("/despachos-recientes")
    public List<DespachoRecienteDTO> recientes(HttpSession session) {
        return farmaciaService.despachosRecientes(exigirFarmaceutico(session).getIdUsuario());
    }

    /** Paso 2: búsqueda de recetas activas por ID de receta / ID de consulta. */
    @GetMapping("/recetas")
    public List<RecetaFarmaciaDTO> buscar(@RequestParam(required = false) Integer idReceta,
                                          @RequestParam(required = false) Integer idConsulta,
                                          HttpSession session) {
        exigirFarmaceutico(session);
        return farmaciaService.buscarRecetas(idReceta, idConsulta);
    }

    /** Pasos 4-5: valida vigencia y devuelve medicamentos + disponibilidad. */
    @GetMapping("/recetas/{idReceta}")
    public DespachoPreviewDTO detalle(@PathVariable Integer idReceta, HttpSession session) {
        return farmaciaService.obtenerDetalle(idReceta, exigirFarmaceutico(session).getIdUsuario());
    }

    /** Pasos 6-13: confirmar el despacho. */
    @PostMapping("/recetas/{idReceta}/despachar")
    public DespachoResponse despachar(@PathVariable Integer idReceta,
                                      @Valid @RequestBody DespacharRequest request,
                                      HttpSession session) {
        return farmaciaService.despachar(idReceta, request, exigirFarmaceutico(session).getIdUsuario());
    }

    /** FA03: el paciente no desea adquirir los medicamentos. */
    @PostMapping("/recetas/{idReceta}/cancelar")
    public Map<String, Object> cancelar(@PathVariable Integer idReceta, HttpSession session) {
        String mensaje = farmaciaService.cancelar(idReceta, exigirFarmaceutico(session).getIdUsuario());
        return Map.of("success", true, "mensaje", mensaje);
    }

    private SesionUsuarioDTO exigirFarmaceutico(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        if (!Rol.FARMACEUTICO.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el personal de farmacia puede acceder a este módulo.");
        }
        return usuario;
    }
}
