package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Las 3 secciones del panel del médico (CU-08, paso 1 del flujo normal). */
@Data
@AllArgsConstructor
public class PanelMedicoResponse {
    private List<CitaPanelMedicoDTO> enEspera;
    private List<CitaPanelMedicoDTO> enConsulta;
    private List<CitaPanelMedicoDTO> evaluados;
}
