package com.sistema.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** CU-08, paso 2: "anuncia por altavoz mediante síntesis de voz (TTS)". */
@Data
@AllArgsConstructor
public class IniciarConsultaResponse {
    private String textoAnuncio;
}
