package com.sistema.hospital.controller;

import com.sistema.hospital.dto.SesionUsuarioDTO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import com.sistema.hospital.repository.EspecialidadRepository;
import com.sistema.hospital.repository.SucursalRepository;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ViewController {

    private final EspecialidadRepository especialidadRepository;
    private final SucursalRepository sucursalRepository;

    private static final Map<String, String> ICONOS = Map.of(
            "medicina general", "fa-house-medical",
            "pediatría", "fa-child",
            "ginecología", "fa-venus",
            "cardiología", "fa-heart-pulse"
    );

    // -----------------------------------------------------------------
    // Portal público
    // -----------------------------------------------------------------
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("titulo", "Inicio - HIS Hospital");
        model.addAttribute("especialidades", especialidadRepository.findByEstadoTrueOrderByNombreAsc());
        model.addAttribute("sedes", sucursalRepository.findByEstadoTrue());
        model.addAttribute("iconos", ICONOS);
        return "portal/index";
    }

    @GetMapping("/servicios")
    public String servicios(Model model) {
        model.addAttribute("titulo", "Servicios - HIS Hospital");
        model.addAttribute("especialidades", especialidadRepository.findByEstadoTrueOrderByNombreAsc());
        model.addAttribute("iconos", ICONOS);
        return "portal/servicios";
    }

    @GetMapping("/contacto")
    public String contacto(Model model) {
        model.addAttribute("titulo", "Contacto - HIS Hospital");
        model.addAttribute("sedes", sucursalRepository.findByEstadoTrue());
        return "portal/contacto";
    }

    // -----------------------------------------------------------------
    // CU-00 / CU-02: acceso
    // -----------------------------------------------------------------
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("titulo", "Iniciar Sesión - HIS Hospital");
        return "auth/login";
    }

    @GetMapping("/registro")
    public String registro(Model model, @RequestParam(required = false) String dpi) {
        model.addAttribute("titulo", "Crear Cuenta - HIS Hospital");
        model.addAttribute("dpiPrecargado", dpi);
        return "auth/registro";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    // -----------------------------------------------------------------
    // Paneles tras iniciar sesión
    // -----------------------------------------------------------------
    @GetMapping("/dashboard")
    public String dashboardPaciente(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login";
        model.addAttribute("titulo", "Mi Cuenta - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "portal/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String dashboardInterno(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        model.addAttribute("titulo", "Panel Interno - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "admin/dashboard";
    }

    @GetMapping("/login-admin")
    public String loginAdmin(Model model) {
        model.addAttribute("titulo", "Acceso Personal - HIS Hospital");
        return "auth/login-admin";
    }

    @GetMapping("/admin/usuarios")
    public String adminUsuarios(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) {
            return "redirect:/login-admin";
        }
        model.addAttribute("titulo", "Mantenimiento de Usuarios - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "admin/usuarios";
    }

    // CU-01, FA01: pantalla completa "Crear Usuario"
    @GetMapping("/admin/usuarios/crear")
    public String crearUsuario(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) {
            return "redirect:/login-admin";
        }
        model.addAttribute("titulo", "Crear Usuario - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "admin/usuario-formulario";
    }

    // CU-01, FA04: pantalla completa "Editar Usuario" (mismo HTML que crear, en modo edición)
    @GetMapping("/admin/usuarios/editar/{id}")
    public String editarUsuario(Model model, HttpSession session, @PathVariable Integer id) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) {
            return "redirect:/login-admin";
        }
        model.addAttribute("titulo", "Editar Usuario - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        model.addAttribute("idUsuarioEditar", id);
        return "admin/usuario-formulario";
    }

    // -----------------------------------------------------------------
    // CU-13: Especialidades por Sede (solo Administrador)
    // -----------------------------------------------------------------
    @GetMapping("/admin/sede-especialidad")
    public String sedeEspecialidad(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) {
            return "redirect:/login-admin";
        }
        model.addAttribute("titulo", "Especialidades por Sede - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "admin/sede-especialidad";
    }

    @GetMapping("/admin/sede-especialidad/crear")
    public String sedeEspecialidadCrear(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) {
            return "redirect:/login-admin";
        }
        model.addAttribute("titulo", "Asignar Especialidad - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "admin/sede-especialidad-formulario";
    }

    // -----------------------------------------------------------------
    // CU-03 / CU-04: agendar y pagar
    // -----------------------------------------------------------------
    @GetMapping("/citas/agendar")
    public String agendarCita(Model model, HttpSession session,
                               @RequestParam(required = false) Integer idPaciente,
                               @RequestParam(required = false) String nombrePaciente,
                               @RequestParam(required = false) Integer idConsultaOrigen) {
        if (session.getAttribute("usuario") == null) return "redirect:/login";
        model.addAttribute("titulo", "Agendar Cita - HIS Hospital");
        // CU-05, FA04: si la URL trae idPaciente, el Recepcionista llegó aquí
        // desde el módulo de Recepción para agendar una cita "walk-in" a
        // nombre de ESE paciente (no de quien tiene la sesión iniciada).
        // citas.js lee estos dos valores desde inputs ocultos en la vista.
        model.addAttribute("idPacienteWalkIn", idPaciente);
        model.addAttribute("nombrePacienteWalkIn", nombrePaciente);
        // NUEVO (CU-08, FA02): si además trae idConsultaOrigen, el Médico
        // llegó aquí desde la sección "Evaluados" para agendar un
        // seguimiento; citas.js lo manda de vuelta en el POST de creación.
        model.addAttribute("idConsultaOrigen", idConsultaOrigen);
        return "citas/agendar";
    }

    // Nota: no existe una pantalla intermedia "/citas/confirmar". Según el
    // documento de CU-03 (paso 5 del wizard), el resumen y la confirmación
    // ya ocurren DENTRO del propio wizard; al confirmar, se va directo a
    // "/pago" (CU-04).

    @GetMapping("/pago")
    public String pago(Model model, HttpSession session, @RequestParam Integer idCita) {
        if (session.getAttribute("usuario") == null) return "redirect:/login";
        model.addAttribute("titulo", "Pago en Línea - HIS Hospital");
        model.addAttribute("idCita", idCita);
        return "pago/pago";
    }

    // NUEVO: "Mis Citas" -> historial de citas del paciente en sesión.
    @GetMapping("/citas/mis-citas")
    public String misCitas(Model model, HttpSession session) {
        if (session.getAttribute("usuario") == null) return "redirect:/login";
        model.addAttribute("titulo", "Mis Citas - HIS Hospital");
        return "citas/mis-citas";
    }

    // -----------------------------------------------------------------
    // CU-05: Recepción y Verificación de Cita
    // -----------------------------------------------------------------
    @GetMapping("/recepcion")
    public String recepcion(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        // Igual que el resto de pantallas internas de este proyecto (ver
        // /admin/usuarios), la autorización por rol se hace comparando el
        // nombre del rol: aquí solo se excluye explícitamente a un Paciente.
        if (com.sistema.hospital.model.Rol.PACIENTE.equals(usuario.getRolNombre())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("titulo", "Recepción y Verificación de Cita - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "recepcion/recepcion";
    }

    // -----------------------------------------------------------------
    // CU-06: Cobro de Consulta en Caja
    // -----------------------------------------------------------------
    @GetMapping("/caja")
    public String caja(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (com.sistema.hospital.model.Rol.PACIENTE.equals(usuario.getRolNombre())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("titulo", "Cobro de Consulta en Caja - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "caja/caja";
    }

    // -----------------------------------------------------------------
    // CU-07: Toma de Signos Vitales
    // -----------------------------------------------------------------
    @GetMapping("/enfermeria")
    public String enfermeria(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (com.sistema.hospital.model.Rol.PACIENTE.equals(usuario.getRolNombre())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("titulo", "Toma de Signos Vitales - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "enfermeria/enfermeria";
    }

    // -----------------------------------------------------------------
    // CU-08: Consulta Médica
    // -----------------------------------------------------------------
    @GetMapping("/medico")
    public String medico(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        // A diferencia de Recepción/Caja/Enfermería, aquí sí se exige el rol
        // exacto "Médico" (mismo criterio que ConsultaMedicaController).
        if (!com.sistema.hospital.model.Rol.MEDICO.equals(usuario.getRolNombre())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("titulo", "Consulta Médica - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "medico/panel";
    }

    // -----------------------------------------------------------------
    // CU-09: Gestión de Laboratorio
    // -----------------------------------------------------------------
    @GetMapping("/laboratorio")
    public String laboratorio(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!puedeVerLaboratorio(usuario)) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Órdenes de Laboratorio - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "laboratorio/ordenes";
    }

    @GetMapping("/laboratorio/orden/{idOrden}")
    public String laboratorioDetalle(Model model, HttpSession session, @PathVariable Integer idOrden) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!puedeVerLaboratorio(usuario)) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Detalle de Orden de Laboratorio - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        model.addAttribute("idOrden", idOrden);
        // Solo el Laboratorista puede escribir resultados; el Administrador solo consulta.
        model.addAttribute("puedeEditar", com.sistema.hospital.model.Rol.LABORATORISTA.equals(usuario.getRolNombre()));
        return "laboratorio/orden-detalle";
    }

    private boolean puedeVerLaboratorio(SesionUsuarioDTO usuario) {
        return com.sistema.hospital.model.Rol.LABORATORISTA.equals(usuario.getRolNombre())
                || com.sistema.hospital.model.Rol.ADMINISTRADOR.equals(usuario.getRolNombre());
    }

    // -----------------------------------------------------------------
    // CU-11: Despacho de medicamentos (Farmacia)
    // -----------------------------------------------------------------
    @GetMapping("/farmacia")
    public String farmacia(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!com.sistema.hospital.model.Rol.FARMACEUTICO.equals(usuario.getRolNombre())) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Farmacia - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "farmacia/panel";
    }

    @GetMapping("/farmacia/despacho")
    public String farmaciaBuscar(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!com.sistema.hospital.model.Rol.FARMACEUTICO.equals(usuario.getRolNombre())) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Nuevo Despacho - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "farmacia/buscar";
    }

    @GetMapping("/farmacia/despacho/{idReceta}")
    public String farmaciaDespacho(Model model, HttpSession session, @PathVariable Integer idReceta) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!com.sistema.hospital.model.Rol.FARMACEUTICO.equals(usuario.getRolNombre())) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Despacho de Receta - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        model.addAttribute("idReceta", idReceta);
        return "farmacia/despacho";
    }

    // -----------------------------------------------------------------
    // CU-10: Cobro de Laboratorio en Caja
    // -----------------------------------------------------------------
    @GetMapping("/caja/laboratorio")
    public String cajaLaboratorio(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (com.sistema.hospital.model.Rol.PACIENTE.equals(usuario.getRolNombre())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("titulo", "Cobro de Laboratorio en Caja - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "caja/caja-laboratorio";
    }

    // -----------------------------------------------------------------
    // CU-15: Bitácora de Movimientos de Inventario (Farmacéutico y Administrador)
    // -----------------------------------------------------------------
    @GetMapping("/inventario")
    public String inventarioBitacora(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!puedeVerInventario(usuario)) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Bitácora de Inventario - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "inventario/bitacora";
    }

    @GetMapping("/inventario/nuevo")
    public String inventarioNuevo(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!puedeVerInventario(usuario)) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Nuevo Movimiento - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "inventario/movimiento-formulario";
    }

    @GetMapping("/inventario/movimiento/{idMovimiento}")
    public String inventarioDetalle(Model model, HttpSession session, @PathVariable Integer idMovimiento) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!puedeVerInventario(usuario)) return "redirect:/admin/dashboard";
        model.addAttribute("titulo", "Detalle de Movimiento - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        model.addAttribute("idMovimiento", idMovimiento);
        return "inventario/movimiento-detalle";
    }

    private boolean puedeVerInventario(SesionUsuarioDTO usuario) {
        return com.sistema.hospital.model.Rol.FARMACEUTICO.equals(usuario.getRolNombre())
                || com.sistema.hospital.model.Rol.ADMINISTRADOR.equals(usuario.getRolNombre());
    }

    // -----------------------------------------------------------------
    // CU-14: Mantenimiento de Catálogos del Sistema (solo Administrador)
    // -----------------------------------------------------------------
    @GetMapping("/admin/catalogos")
    public String catalogos(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) return "redirect:/login-admin";
        model.addAttribute("titulo", "Catálogos del Sistema - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "admin/catalogos";
    }

    @GetMapping("/admin/catalogos/{clave}")
    public String catalogoLista(Model model, HttpSession session, @PathVariable String clave) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) return "redirect:/login-admin";
        model.addAttribute("titulo", "Catálogo - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        model.addAttribute("clave", clave);
        return "admin/catalogo-lista";
    }

    @GetMapping("/admin/catalogos/{clave}/crear")
    public String catalogoCrear(Model model, HttpSession session, @PathVariable String clave) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) return "redirect:/login-admin";
        model.addAttribute("titulo", "Crear registro - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        model.addAttribute("clave", clave);
        model.addAttribute("idRegistro", "");
        return "admin/catalogo-formulario";
    }

    @GetMapping("/admin/catalogos/{clave}/editar/{id}")
    public String catalogoEditar(Model model, HttpSession session, @PathVariable String clave, @PathVariable Integer id) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null || !"Administrador".equals(usuario.getRolNombre())) return "redirect:/login-admin";
        model.addAttribute("titulo", "Editar registro - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        model.addAttribute("clave", clave);
        model.addAttribute("idRegistro", id);
        return "admin/catalogo-formulario";
    }

    // -----------------------------------------------------------------
    // CU-16: Agenda Médica (solo rol Médico)
    // -----------------------------------------------------------------
    @GetMapping("/medico/agenda")
    public String medicoAgenda(Model model, HttpSession session) {
        SesionUsuarioDTO usuario = (SesionUsuarioDTO) session.getAttribute("usuario");
        if (usuario == null) return "redirect:/login-admin";
        if (!com.sistema.hospital.model.Rol.MEDICO.equals(usuario.getRolNombre())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("titulo", "Agenda Médica - HIS Hospital");
        model.addAttribute("usuarioSesion", usuario);
        return "medico/agenda";
    }
}
