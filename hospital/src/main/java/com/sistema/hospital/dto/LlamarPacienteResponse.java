package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CU-07, paso 1: "El sistema anuncia por altavoz mediante síntesis de voz
 * (TTS): 'Turno número [N]. Paciente [Nombre], favor pasar a toma de signos
 * vitales.'". El backend no tiene bocina; solo arma el texto exacto y el
 * navegador lo reproduce con la Web Speech API (ver signos-vitales.js).
 */
@Data
@AllArgsConstructor
public class LlamarPacienteResponse {
    private String textoAnuncio;
    private PacienteColaDTO cita;
}
