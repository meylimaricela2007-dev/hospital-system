package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuardarConsultaResponse {
    private String mensaje;
    private String estadoConsulta;
}
