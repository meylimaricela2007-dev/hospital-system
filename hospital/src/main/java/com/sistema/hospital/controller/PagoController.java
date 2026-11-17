package com.sistema.hospital.controller;

import com.sistema.hospital.dto.ComprobantePagoResponse;
import com.sistema.hospital.dto.PagoTarjetaRequest;
import com.sistema.hospital.dto.SesionUsuarioDTO;
import com.sistema.hospital.service.PagoService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** CU-04: Pago en Línea. */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/citas/{idCita}")
    public ComprobantePagoResponse pagar(@PathVariable Integer idCita,
                                          @Valid @RequestBody PagoTarjetaRequest request,
                                          HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para pagar una cita.");
        }
        return pagoService.pagar(idCita, request, usuario.getIdUsuario());
    }
}
