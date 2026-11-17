package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.BindingResult;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.List;
import java.util.Optional;

/** Reglas de negocio de CU-00 (verificar DPI), CU-01 (mantenimiento de usuarios) y CU-02 (registro). */
@Service
public class UsuarioService {

    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private EstadoUsuarioRepository estadoUsuarioRepository;
    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private EspecialidadRepository especialidadRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    //verificar DPI
    @Transactional(readOnly = true)
    public VerificarDpiResponse verificarDpi(String dpi) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByDpi(dpi);
        if (usuarioOpt.isEmpty()) {
            return new VerificarDpiResponse(false, false,
                    "No se encontró un registro asociado a este DPI. Será redirigido al formulario de registro.");
        }
        Usuario usuario = usuarioOpt.get();
        boolean esPaciente = Rol.PACIENTE.equalsIgnoreCase(usuario.getRol().getNombreRol());
        return esPaciente
                ? new VerificarDpiResponse(true, true, "Usuario registrado. Será redirigido a la pantalla de inicio de sesión.")
                : new VerificarDpiResponse(true, false, "Este DPI pertenece a un usuario del sistema interno. Por favor, contacte a recepción.");
    }

    public Optional<Usuario> obtenerPorNombreUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario);
    }

    public Usuario guardar(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    // registro público de pacientes
    @Autowired
    private NotificacionService notificacionService;

    /** CU-02: junta TODOS los errores (formato + duplicados) y los lanza de una sola vez. */
    public void validarRegistro(RegistroPacienteRequest request, BindingResult resultado) {
        Map<String, String> errores = new LinkedHashMap<>();
        resultado.getFieldErrors().forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));

        if (!errores.containsKey("dpi") && request.getDpi() != null
                && usuarioRepository.existsByDpi(request.getDpi())) {
            errores.put("dpi", "Ya existe una cuenta registrada con este número de DPI. Si ya tiene cuenta, inicie sesión.");
        }
        if (!errores.containsKey("correoElectronico") && request.getCorreoElectronico() != null
                && usuarioRepository.findByCorreoElectronicoIgnoreCase(request.getCorreoElectronico()).isPresent()) {
            errores.put("correoElectronico", "Ya existe una cuenta registrada con este correo electrónico.");
        }
        if (!errores.containsKey("nombreUsuario") && request.getNombreUsuario() != null
                && usuarioRepository.findByNombreUsuario(request.getNombreUsuario()).isPresent()) {
            errores.put("nombreUsuario", "El nombre de usuario ya se encuentra registrado. Por favor, elija otro.");
        }

        if (!errores.isEmpty()) {
            throw new com.sistema.hospital.exception.CamposInvalidosException(errores);
        }
    }

    @Transactional
    public Usuario registrarPaciente(RegistroPacienteRequest request) {
        if (usuarioRepository.existsByDpi(request.getDpi())) {
            throw new com.sistema.hospital.exception.CampoInvalidoException("dpi",
                    "Ya existe una cuenta registrada con este número de DPI. Si ya tiene cuenta, inicie sesión.");
        }
        if (usuarioRepository.findByCorreoElectronicoIgnoreCase(request.getCorreoElectronico()).isPresent()) {
            throw new com.sistema.hospital.exception.CampoInvalidoException("correoElectronico",
                    "Ya existe una cuenta registrada con este correo electrónico.");
        }
        if (usuarioRepository.findByNombreUsuario(request.getNombreUsuario()).isPresent()) {
            throw new com.sistema.hospital.exception.CampoInvalidoException("nombreUsuario",
                    "El nombre de usuario ya se encuentra registrado. Por favor, elija otro.");
        }

        Usuario usuario = new Usuario();
        usuario.setNombreCompleto(request.getNombreCompleto());
        usuario.setCorreoElectronico(request.getCorreoElectronico());
        usuario.setNombreUsuario(request.getNombreUsuario());
        usuario.setContrasenaHash(passwordEncoder.encode(request.getContrasena()));
        usuario.setDpi(request.getDpi());
        usuario.setNit(request.getNit());
        usuario.setTelefono(request.getTelefono());
        usuario.setNumeroSeguro(vacioComoNulo(request.getNumeroSeguro()));
        usuario.setRol(buscarRolPorNombre(Rol.PACIENTE));
        usuario.setEstadoUsuario(buscarEstadoPorNombre(EstadoUsuario.ACTIVO));
        usuario = usuarioRepository.save(usuario);

        // correo de bienvenida.
        notificacionService.enviarBienvenida(usuario);

        return usuario;
    }

    // listar con filtro y paginación configurable
    @Transactional(readOnly = true)
    public Page<UsuarioDTO> listarUsuarios(String filtro, String valor, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Usuario> usuariosPage;

        if (valor == null || valor.isBlank() || filtro == null) {
            usuariosPage = usuarioRepository.findAll(pageable);
        } else {
            usuariosPage = switch (filtro) {
                case "ID" -> {
                    try {
                        yield usuarioRepository.findByIdUsuario(Integer.parseInt(valor.trim()), pageable);
                    } catch (NumberFormatException ex) {
                        yield Page.empty(pageable); // un ID no numérico simplemente no da resultados
                    }
                }
                case "Correo Electrónico" -> usuarioRepository.findByCorreoElectronicoContainingIgnoreCase(valor, pageable);
                case "Rol" -> usuarioRepository.findByRol_NombreRolContainingIgnoreCase(valor, pageable);
                case "Nombre de Usuario" -> usuarioRepository.findByNombreUsuarioContainingIgnoreCase(valor, pageable);
                case "DPI" -> usuarioRepository.findByDpiContainingIgnoreCase(valor, pageable);
                default -> usuarioRepository.findByNombreCompletoContainingIgnoreCase(valor, pageable);
            };
        }

        return usuariosPage.map(this::convertirADTO);
    }

    private UsuarioDTO convertirADTO(Usuario usuario) {
        return new UsuarioDTO(
                usuario.getIdUsuario(),
                usuario.getNombreCompleto(),
                usuario.getCorreoElectronico(),
                usuario.getRol().getNombreRol(),
                usuario.getNombreUsuario(),
                usuario.getEstadoUsuario().getNombreEstado()
        );
    }

    // traer todos los campos para precargar "Editar Usuario"
    @Transactional(readOnly = true)
    public UsuarioDetalleDTO obtenerDetalle(Integer idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontraron datos usuarios."));

        return new UsuarioDetalleDTO(
                usuario.getIdUsuario(),
                usuario.getNombreCompleto(),
                usuario.getCorreoElectronico(),
                usuario.getNombreUsuario(),
                usuario.getDpi(),
                usuario.getNit(),
                usuario.getTelefono(),
                usuario.getNumeroSeguro(),
                usuario.getRol().getIdRol(),
                usuario.getSucursal() != null ? usuario.getSucursal().getIdSucursal() : null,
                usuario.getEspecialidad() != null ? usuario.getEspecialidad().getIdEspecialidad() : null,
                EstadoUsuario.ACTIVO.equals(usuario.getEstadoUsuario().getNombreEstado())
        );
    }

    // CU-01: catálogos para los formularios
    @Transactional(readOnly = true)
    public CatalogosUsuarioResponse obtenerCatalogos() {
        List<CatalogoItemDTO> roles = rolRepository.findByEstadoTrue().stream()
                .map(r -> new CatalogoItemDTO(r.getIdRol(), r.getNombreRol())).toList();
        List<CatalogoItemDTO> sucursales = sucursalRepository.findByEstadoTrue().stream()
                .map(s -> new CatalogoItemDTO(s.getIdSucursal(), s.getNombre())).toList();
        List<CatalogoItemDTO> especialidades = especialidadRepository.findByEstadoTrue().stream()
                .map(e -> new CatalogoItemDTO(e.getIdEspecialidad(), e.getNombre())).toList();
        return new CatalogosUsuarioResponse(roles, sucursales, especialidades);
    }

    // CU-01, FA01: crear usuario (pantalla completa "Crear Usuario")
    @CacheEvict(cacheNames = {"medicosPorEspecialidad"}, allEntries = true)
    @Transactional
    public UsuarioDTO crear(UsuarioFormularioRequest request, Integer idAdmin) {
        if (request.getContrasena() == null || request.getContrasena().isBlank()) {
            throw new com.sistema.hospital.exception.CampoInvalidoException(
                    "contrasena", "La contraseña es obligatoria.");
        }
        validarUnicidad(request, null);

        Usuario usuario = new Usuario();
        aplicarDatosComunes(usuario, request);
        usuario.setContrasenaHash(passwordEncoder.encode(request.getContrasena()));
        Usuario guardado = usuarioRepository.save(usuario);

        auditoriaService.registrar(idAdmin, "CREAR_USUARIO", "usuarios", guardado.getIdUsuario(),
                "Usuario creado: " + guardado.getNombreUsuario() + " (" + guardado.getRol().getNombreRol() + ").");
        return convertirADTO(guardado);
    }

    @CacheEvict(cacheNames = {"medicosPorEspecialidad"}, allEntries = true)
    @Transactional
    public UsuarioDTO editar(Integer idUsuario, UsuarioFormularioRequest request, Integer idAdmin) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe."));

        validarUnicidad(request, idUsuario);
        aplicarDatosComunes(usuario, request);

        boolean cambioContrasena = request.getContrasena() != null && !request.getContrasena().isBlank();
        if (cambioContrasena) {
            usuario.setContrasenaHash(passwordEncoder.encode(request.getContrasena()));
        }
        Usuario guardado = usuarioRepository.save(usuario);

        auditoriaService.registrar(idAdmin, "ACTUALIZAR_USUARIO", "usuarios", guardado.getIdUsuario(),
                "Usuario actualizado: " + guardado.getNombreUsuario()
                        + (cambioContrasena ? " (se cambió la contraseña)." : "."));
        return convertirADTO(guardado);
    }

    @CacheEvict(cacheNames = {"medicosPorEspecialidad"}, allEntries = true)
    @Transactional
    public String eliminar(Integer idUsuario, Integer idAdmin) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe."));

        EstadoUsuario estadoInactivo = estadoUsuarioRepository.findByNombreEstado("Inactivo")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Estado Inactivo no encontrado."));

        usuario.setEstadoUsuario(estadoInactivo);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(idAdmin, "ELIMINAR_USUARIO", "usuarios", usuario.getIdUsuario(),
                "Usuario eliminado (baja lógica): " + usuario.getNombreUsuario() + ".");
        return usuario.getNombreUsuario();
    }

    // =================================================================
    // Helpers privados
    // =================================================================

    private void aplicarDatosComunes(Usuario usuario, UsuarioFormularioRequest request) {
        usuario.setNombreCompleto(request.getNombreCompleto());
        usuario.setNombreUsuario(request.getNombreUsuario());
        usuario.setCorreoElectronico(request.getCorreoElectronico());
        usuario.setDpi(vacioComoNulo(request.getDpi()));
        usuario.setNit(vacioComoNulo(request.getNit()));
        usuario.setTelefono(vacioComoNulo(request.getTelefono()));
        usuario.setNumeroSeguro(vacioComoNulo(request.getNumeroSeguro()));

        usuario.setRol(rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol seleccionado no existe.")));
        usuario.setSucursal(request.getIdSucursal() == null ? null :
                sucursalRepository.findById(request.getIdSucursal())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sucursal seleccionada no existe.")));
        usuario.setEspecialidad(request.getIdEspecialidad() == null ? null :
                especialidadRepository.findById(request.getIdEspecialidad())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La especialidad seleccionada no existe.")));
        usuario.setEstadoUsuario(buscarEstadoPorNombre(request.isEstadoActivo() ? EstadoUsuario.ACTIVO : EstadoUsuario.INACTIVO));
    }

    private String vacioComoNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private Rol buscarRolPorNombre(String nombre) {
        return rolRepository.findByNombreRol(nombre)
                .orElseThrow(() -> new IllegalStateException("El rol '" + nombre + "' no existe en el catálogo."));
    }

    private EstadoUsuario buscarEstadoPorNombre(String nombre) {
        return estadoUsuarioRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado '" + nombre + "' no existe en el catálogo."));
    }

    // CORREGIDO: antes lanzaba ResponseStatusException (sin campo asociado),
    // así que el frontend solo podía mostrar el mensaje en la alerta general
    // y no sabía resaltar el input "nombreUsuario" ni "dpi" en rojo. Ahora
    // usa CampoInvalidoException, que GlobalExceptionHandler traduce a
    // { errors: { nombreUsuario: "..." } } / { errors: { dpi: "..." } }.
    private void validarUnicidad(UsuarioFormularioRequest request, Integer idUsuarioActual) {
        usuarioRepository.findByNombreUsuario(request.getNombreUsuario()).ifPresent(existente -> {
            if (!existente.getIdUsuario().equals(idUsuarioActual)) {
                throw new com.sistema.hospital.exception.CampoInvalidoException(
                        "nombreUsuario", "El nombre de usuario ya está en uso.");
            }
        });
        if (request.getDpi() != null && !request.getDpi().isBlank()) {
            usuarioRepository.findByDpi(request.getDpi()).ifPresent(existente -> {
                if (!existente.getIdUsuario().equals(idUsuarioActual)) {
                    throw new com.sistema.hospital.exception.CampoInvalidoException(
                            "dpi", "Ya existe un usuario con ese DPI.");
                }
            });
        }
    }
}
