package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerificarDpiResponse {
    private boolean existe;
    private boolean esPaciente;
    private String mensaje;
}
