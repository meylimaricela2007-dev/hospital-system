package com.sistema.hospital.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** Datos del formulario "Nuevo/Editar usuario". Cada anotación es una regla de negocio. */
@Data
public class UsuarioFormularioRequest {

    @NotBlank(message = "El nombre completo es obligatorio.")
    @Size(min = 10, max = 100, message = "El nombre debe contener entre 10 y 100 caracteres.")
    private String nombreCompleto;

    @NotBlank(message = "El nombre de usuario es obligatorio.")
    @Pattern(regexp = "[a-zA-Z0-9]{8,9}", message = "El usuario debe contener entre 8 y 9 caracteres alfanuméricos.")
    private String nombreUsuario;

    // Obligatoria solo al CREAR; al editar, vacía = "no cambiar la contraseña" (se valida en el service).
    // Mismo detalle que en RegistroPacienteRequest: @Size rechaza "" aunque el campo
    // sea opcional, así que usamos @Pattern con "^$|..." para permitir vacío.
    // CAMBIADO: el mínimo para cuentas de personal interno (creadas por un
    // Administrador desde este formulario) baja de 12 a 9 caracteres, a
    // pedido explícito. El registro público de pacientes (CU-02,
    // RegistroPacienteRequest) sigue pidiendo 12: no se tocó porque no fue
    // parte de lo solicitado y es una cuenta que el propio dueño protege.
    @Pattern(regexp = "^$|^.{9,}$", message = "La contraseña debe contener al menos 9 caracteres.")
    private String contrasena;

    @NotBlank(message = "El correo electrónico es obligatorio.")
    @Email(message = "El formato del correo electrónico no es válido.")
    private String correoElectronico;

    @NotNull(message = "Debe seleccionar un rol.")
    private Integer idRol;

    private Integer idEspecialidad; // solo si el rol es Médico
    private Integer idSucursal;     // sede donde trabaja

    @Pattern(regexp = "^$|^\\d{13}$", message = "El DPI debe contener exactamente 13 dígitos numéricos.")
    private String dpi;

    @Pattern(regexp = "^$|^[a-zA-Z0-9-]{8,10}$", message = "El NIT debe contener entre 8 y 10 caracteres (letras, números y guión).")
    private String nit;

    @Pattern(regexp = "^$|^\\d{8}$", message = "El teléfono debe contener exactamente 8 dígitos numéricos.")
    private String telefono;

    @Size(max = 50, message = "El número de seguro no puede superar los 50 caracteres.")
    private String numeroSeguro;

    private boolean estadoActivo = true;
}
