package com.sistema.hospital.service;

import com.sistema.hospital.dto.LoginRequest;
import com.sistema.hospital.dto.LoginResponse;
import com.sistema.hospital.dto.SesionUsuarioDTO;
import com.sistema.hospital.model.EstadoUsuario;
import com.sistema.hospital.model.Rol;
import com.sistema.hospital.model.Usuario;
import com.sistema.hospital.repository.EstadoUsuarioRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;
    private static final String MENSAJE_BLOQUEADA = "Cuenta bloqueada temporalmente. Intente de nuevo en 15 minutos.";
    private static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";
    private static final String MENSAJE_ROL_NO_AUTORIZADO = "Este acceso es exclusivo para pacientes. Si es personal del hospital, use el panel administrativo.";

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EstadoUsuarioRepository estadoUsuarioRepository;

    @Transactional
    public LoginResponse login(LoginRequest request, HttpSession session) {
        Usuario usuario = usuarioService.obtenerPorNombreUsuario(request.getUsername()).orElse(null);
        if (usuario == null) {
            return new LoginResponse(false, null, MENSAJE_CREDENCIALES, null);
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(ahora)) {
                return new LoginResponse(false, null, MENSAJE_BLOQUEADA, MAX_INTENTOS_FALLIDOS);
            }
            // El bloqueo ya venció: se limpia y el usuario vuelve a tener 5 intentos nuevos.
            desbloquear(usuario);
        }

        // QA: antes un usuario "Inactivo" (el que el Administrador "elimina" en CU-01,
        // que es una baja lógica) podía seguir iniciando sesión. Se responde con el mismo
        // mensaje genérico para no revelar que la cuenta existe.
        if (EstadoUsuario.INACTIVO.equalsIgnoreCase(usuario.getEstadoUsuario().getNombreEstado())) {
            return new LoginResponse(false, null, MENSAJE_CREDENCIALES, null);
        }

        boolean credencialesValidas = passwordEncoder.matches(request.getPassword(), usuario.getContrasenaHash());

        if (!credencialesValidas) {
            return registrarIntentoFallido(usuario);
        }

        // Login correcto: reiniciar contador de intentos fallidos.
        usuario.setIntentosFallidos((short) 0);
        usuario.setBloqueadoHasta(null);
        usuarioService.guardar(usuario);

        if ("paciente".equalsIgnoreCase(request.getPortal())
                && !"Paciente".equalsIgnoreCase(usuario.getRol().getNombreRol())) {
            return new LoginResponse(false, usuario.getRol().getNombreRol(), MENSAJE_ROL_NO_AUTORIZADO, 0);
        }

        // Guardamos los datos mínimos del usuario en la sesión HTTP.
        session.setAttribute("usuario", new SesionUsuarioDTO(
                usuario.getIdUsuario(),
                usuario.getNombreCompleto(),
                usuario.getNombreUsuario(),
                usuario.getRol().getNombreRol()
        ));

        return new LoginResponse(true, usuario.getRol().getNombreRol(), "Bienvenido(a).", 0);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private LoginResponse registrarIntentoFallido(Usuario usuario) {
        short intentos = (short) (usuario.getIntentosFallidos() + 1);
        usuario.setIntentosFallidos(intentos);

        if (intentos >= MAX_INTENTOS_FALLIDOS) {
            usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
            estadoUsuarioRepository.findByNombreEstado(EstadoUsuario.BLOQUEADO)
                    .ifPresent(usuario::setEstadoUsuario);
            usuarioService.guardar(usuario);
            return new LoginResponse(false, null, MENSAJE_BLOQUEADA, (int) intentos);
        }

        usuarioService.guardar(usuario);
        int restantes = MAX_INTENTOS_FALLIDOS - intentos;
        return new LoginResponse(false, null,
                MENSAJE_CREDENCIALES + " Intentos restantes: " + restantes + ".", (int) intentos);
    }

    /** Fin del bloqueo temporal: contador en cero y, si estaba "Bloqueado", vuelve a "Activo". */
    private void desbloquear(Usuario usuario) {
        usuario.setIntentosFallidos((short) 0);
        usuario.setBloqueadoHasta(null);
        if (EstadoUsuario.BLOQUEADO.equalsIgnoreCase(usuario.getEstadoUsuario().getNombreEstado())) {
            estadoUsuarioRepository.findByNombreEstado(EstadoUsuario.ACTIVO)
                    .ifPresent(usuario::setEstadoUsuario);
        }
        usuarioService.guardar(usuario);
    }
}
