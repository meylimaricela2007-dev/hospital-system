package com.sistema.hospital.controller;

import com.sistema.hospital.dto.DashboardGraficasDTO;
import com.sistema.hospital.dto.SesionUsuarioDTO;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.service.DashboardGraficasService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Gráficas del dashboard del Administrador. La lógica está en DashboardGraficasService. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardGraficasController {

    @Autowired
    private DashboardGraficasService dashboardGraficasService;

    @GetMapping("/graficas")
    public DashboardGraficasDTO graficas(HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        if (!Rol.ADMINISTRADOR.equals(usuario.getRolNombre())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para ver estas gráficas.");
        }
        return dashboardGraficasService.obtener();
    }
}
