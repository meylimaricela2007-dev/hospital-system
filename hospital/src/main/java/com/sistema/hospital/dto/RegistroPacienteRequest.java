package com.sistema.hospital.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Formulario público de registro de pacientes (CU-02).
 * Según el documento, solo el número de seguro médico es opcional
 * (paso 6: "...(opcional)"); DPI, NIT, teléfono, correo, usuario y
 * contraseña son obligatorios (pasos 2-9, sin esa anotación).
 */
@Data
public class RegistroPacienteRequest {

    @NotBlank(message = "El nombre completo es obligatorio.")
    @Size(min = 10, max = 100, message = "El nombre debe contener entre 10 y 100 caracteres.")
    private String nombreCompleto;

    @NotBlank(message = "El DPI es obligatorio.")
    @Pattern(regexp = "\\d{13}", message = "El DPI debe contener exactamente 13 dígitos numéricos.")
    private String dpi;

    @NotBlank(message = "El NIT es obligatorio.")
    @Pattern(regexp = "[a-zA-Z0-9-]{8,10}", message = "El NIT debe contener entre 8 y 10 caracteres (letras, números y guión).")
    private String nit;

    @NotBlank(message = "El teléfono es obligatorio.")
    @Pattern(regexp = "\\d{8}", message = "El teléfono debe contener exactamente 8 dígitos numéricos.")
    private String telefono;

    // Único campo opcional del formulario (paso 6 del flujo normal).
    // OJO: usamos @Pattern con "^$|..." en vez de @Size, porque @Size SÍ evalúa
    // la longitud de un string vacío "" (que es lo que manda el navegador cuando
    // el campo se deja en blanco) y lo rechazaba por tener 0 caracteres, aunque
    // el campo sea opcional. El patrón "^$|^.{5,50}$" acepta vacío O 5-50 caracteres.
    @Pattern(regexp = "^$|^.{5,50}$", message = "El número de seguro debe contener entre 5 y 50 caracteres.")
    private String numeroSeguro;

    @NotBlank(message = "El correo electrónico es obligatorio.")
    @Email(message = "El formato del correo electrónico no es válido.")
    private String correoElectronico;

    @NotBlank(message = "El nombre de usuario es obligatorio.")
    @Pattern(regexp = "[a-zA-Z0-9]{8,9}", message = "El usuario debe contener entre 8 y 9 caracteres alfanuméricos.")
    private String nombreUsuario;

    @NotBlank(message = "La contraseña es obligatoria.")
    // CAMBIADO: mínimo 12 -> 9 caracteres, mismo criterio que ahora usa
    // UsuarioFormularioRequest (creación de personal interno desde el panel
    // de Administrador). Se mantiene consistente entre ambos formularios.
    @Size(min = 9, message = "La contraseña debe contener al menos 9 caracteres.")
    private String contrasena;
}
