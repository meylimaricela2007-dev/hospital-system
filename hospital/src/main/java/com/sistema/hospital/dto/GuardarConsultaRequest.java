package com.sistema.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * CU-08, paso 4: "El formulario presenta los campos: Motivo de Visita
 * (obligatorio), Hallazgos Clínicos, Código CIE-10, Diagnóstico
 * (obligatorio para finalizar), Plan de Tratamiento, Notas Adicionales y
 * Estado de Consulta (En curso / Finalizada)."
 *
 * "Diagnóstico obligatorio para finalizar" es una regla CONDICIONAL (FA05):
 * solo aplica si estadoConsulta = "Finalizada". Por eso no lleva @NotBlank
 * aquí; ConsultaMedicaService.guardarConsulta() la valida a mano y devuelve
 * el mensaje exacto del documento cuando falla.
 */
@Data
public class GuardarConsultaRequest {

    @NotBlank(message = "El motivo de visita es obligatorio.")
    @Size(min = 10, max = 2000, message = "El motivo de visita debe contener entre 10 y 2000 caracteres.")
    private String motivoVisita;

    private String hallazgosClinicos;

    @Pattern(regexp = "^$|^[A-Za-z0-9.]{1,10}$", message = "El código CIE-10 no es válido.")
    private String codigoCie10;

    private String diagnostico;

    private String planTratamiento;

    private String notasAdicionales;

    @NotBlank(message = "Debe seleccionar el estado de la consulta.")
    @Pattern(regexp = "En curso|Finalizada", message = "El estado de consulta debe ser 'En curso' o 'Finalizada'.")
    private String estadoConsulta;
}
