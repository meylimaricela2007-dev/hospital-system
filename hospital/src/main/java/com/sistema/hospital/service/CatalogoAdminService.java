package com.sistema.hospital.service;

import com.sistema.hospital.exception.CampoInvalidoException;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * CU-14: Mantenimiento de Catálogos del Sistema.
 *
 * Un solo servicio atiende los 8 catálogos CRUD (Especialidades, Sucursales,
 * Estados de Cita, Laboratorios, Exámenes de Laboratorio, Medicamentos, Roles
 * e Inventario de Medicamentos). Cada catálogo es un "Handler" que declara sus
 * columnas y campos (el frontend dibuja la tabla y el formulario a partir de
 * eso) y sabe validar y guardar sus propios datos. Sucursal-Especialidad ya
 * tiene su propio módulo (CU-13).
 *
 * Eliminar es LÓGICO (estado = false). Excepciones, porque su tabla no tiene
 * columna "estado" o el sistema depende de ellos por nombre:
 *  - Estados de Cita: se pueden crear y editar la descripción; no se renombran
 *    ni se eliminan (el código los busca por nombre).
 *  - Roles: no se renombran (el código compara por nombre) y no se pueden
 *    desactivar mientras tengan usuarios asignados.
 */
@Service
public class CatalogoAdminService {

    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private EstadoCitaRepository estadoCitaRepository;
    @Autowired private LaboratorioRepository laboratorioRepository;
    @Autowired private ExamenLaboratorioRepository examenRepository;
    @Autowired private MedicamentoRepository medicamentoRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private InventarioMedicamentoRepository inventarioRepository;
    @Autowired private MovimientoInventarioRepository movimientoRepository;
    @Autowired private TipoMovimientoInventarioRepository tipoMovimientoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditoriaService auditoriaService;

    private final Map<String, Handler> handlers = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        handlers.put("especialidades", new EspecialidadHandler());
        handlers.put("sucursales", new SucursalHandler());
        handlers.put("estados-cita", new EstadoCitaHandler());
        handlers.put("laboratorios", new LaboratorioHandler());
        handlers.put("examenes", new ExamenHandler());
        handlers.put("medicamentos", new MedicamentoHandler());
        handlers.put("roles", new RolHandler());
        handlers.put("inventario", new InventarioHandler());
    }

    // ======================================================================
    // API pública del servicio
    // ======================================================================

    /** Pantalla de inicio del módulo: un renglón por catálogo. */
    public List<Map<String, Object>> indice() {
        List<Map<String, Object>> lista = new ArrayList<>();
        handlers.forEach((clave, h) -> lista.add(mapa("clave", clave, "titulo", h.titulo, "descripcion", h.descripcion, "icono", h.icono)));
        return lista;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> meta(String clave) {
        Handler h = handler(clave);
        return mapa("clave", clave, "titulo", h.titulo, "singular", h.singular, "tieneEstado", h.tieneEstado,
                "puedeEliminar", h.puedeEliminar, "columnas", h.columnas(), "campos", h.campos());
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listar(String clave, String texto, String estado, int page, int size) {
        Handler h = handler(clave);
        int tamano = (size == 10 || size == 25 || size == 50) ? size : 10;

        String filtro = texto == null ? "" : texto.trim().toLowerCase();
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> f : h.filas()) {
            if (!filtro.isEmpty()) {
                boolean coincide = String.valueOf(f.get("id")).equals(filtro)
                        || String.valueOf(f.get("etiqueta")).toLowerCase().contains(filtro);
                if (!coincide) continue;
            }
            if (h.tieneEstado && estado != null && !estado.isBlank()) {
                boolean activo = Boolean.TRUE.equals(f.get("estado"));
                if ("activos".equals(estado) && !activo) continue;
                if ("inactivos".equals(estado) && activo) continue;
            }
            filas.add(f);
        }
        filas.sort(Comparator.comparing(f -> (Integer) f.get("id")));

        Pageable pageable = PageRequest.of(Math.max(page, 0), tamano);
        int desde = (int) Math.min(pageable.getOffset(), filas.size());
        int hasta = Math.min(desde + tamano, filas.size());
        return new PageImpl<>(filas.subList(desde, hasta), pageable, filas.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(String clave, Integer id) {
        return handler(clave).fila(id);
    }

    // QA: al cambiar un catálogo se vacía la caché de catálogos para no mostrar datos viejos.
    @CacheEvict(cacheNames = {"sucursales", "especialidadesPorSede", "medicosPorEspecialidad", "examenesLaboratorio", "medicamentosCatalogo", "tiposSeguimiento", "prioridades"}, allEntries = true)
    @Transactional
    public String crear(String clave, Map<String, Object> datos, Integer idUsuario) {
        Handler h = handler(clave);
        Map<String, Object> fila = h.crear(datos == null ? Map.of() : datos, idUsuario);
        auditoriaService.registrar(idUsuario, "CREAR_CATALOGO", h.tabla, (Integer) fila.get("id"),
                "Se creó el registro '" + fila.get("etiqueta") + "' en " + h.titulo + ".");
        return "El registro " + fila.get("etiqueta") + " ha sido creado exitosamente.";
    }

    @CacheEvict(cacheNames = {"sucursales", "especialidadesPorSede", "medicosPorEspecialidad", "examenesLaboratorio", "medicamentosCatalogo", "tiposSeguimiento", "prioridades"}, allEntries = true)
    @Transactional
    public String actualizar(String clave, Integer id, Map<String, Object> datos, Integer idUsuario) {
        Handler h = handler(clave);
        Map<String, Object> fila = h.actualizar(id, datos == null ? Map.of() : datos);
        auditoriaService.registrar(idUsuario, "ACTUALIZAR_CATALOGO", h.tabla, id,
                "Se actualizó el registro '" + fila.get("etiqueta") + "' en " + h.titulo + ".");
        return "El registro " + fila.get("etiqueta") + " ha sido actualizado correctamente.";
    }

    @CacheEvict(cacheNames = {"sucursales", "especialidadesPorSede", "medicosPorEspecialidad", "examenesLaboratorio", "medicamentosCatalogo", "tiposSeguimiento", "prioridades"}, allEntries = true)
    @Transactional
    public String eliminar(String clave, Integer id, Integer idUsuario) {
        Handler h = handler(clave);
        if (!h.puedeEliminar) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Este catálogo no permite eliminar registros.");
        }
        String etiqueta = h.eliminar(id);
        auditoriaService.registrar(idUsuario, "ELIMINAR_CATALOGO", h.tabla, id,
                "Se eliminó (lógicamente) el registro '" + etiqueta + "' de " + h.titulo + ".");
        return "El registro " + etiqueta + " ha sido eliminado correctamente.";
    }

    private Handler handler(String clave) {
        Handler h = handlers.get(clave);
        if (h == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El catálogo solicitado no existe.");
        }
        return h;
    }

    // ======================================================================
    // Utilidades de validación (RN-CU15-01 a RN-CU15-04)
    // ======================================================================

    private static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], pares[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> columna(String key, String label, String tipo) {
        return mapa("key", key, "label", label, "tipo", tipo);
    }

    private static Map<String, Object> campo(String key, String label, String tipo, boolean requerido, Integer maxLength) {
        Map<String, Object> c = mapa("key", key, "label", label, "tipo", tipo, "requerido", requerido);
        if (maxLength != null) c.put("maxLength", maxLength);
        return c;
    }

    private static String texto(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String textoObligatorio(Map<String, Object> d, String key, int max, String mensajeVacio) {
        String s = texto(d, key);
        if (s == null) throw new CampoInvalidoException(key, mensajeVacio);
        if (s.length() > max) throw new CampoInvalidoException(key, "No puede superar " + max + " caracteres.");
        return s;
    }

    private static String textoOpcional(Map<String, Object> d, String key, int max) {
        String s = texto(d, key);
        if (s != null && s.length() > max) throw new CampoInvalidoException(key, "No puede superar " + max + " caracteres.");
        return s;
    }

    private static boolean booleano(Map<String, Object> d, String key, boolean porDefecto) {
        Object v = d.get(key);
        if (v == null) return porDefecto;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private static BigDecimal decimal(Map<String, Object> d, String key, String mensajeInvalido) {
        String s = texto(d, key);
        if (s == null) throw new CampoInvalidoException(key, mensajeInvalido);
        try {
            BigDecimal v = new BigDecimal(s);
            if (v.compareTo(BigDecimal.ZERO) <= 0) throw new CampoInvalidoException(key, mensajeInvalido);
            if (v.stripTrailingZeros().scale() > 2) throw new CampoInvalidoException(key, "Debe tener máximo 2 decimales.");
            if (v.compareTo(new BigDecimal("99999999.99")) > 0) throw new CampoInvalidoException(key, "El valor es demasiado grande.");
            return v.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (NumberFormatException ex) {
            throw new CampoInvalidoException(key, mensajeInvalido);
        }
    }

    private static Integer entero(Map<String, Object> d, String key, String mensajeInvalido) {
        String s = texto(d, key);
        if (s == null) return null;
        try {
            return new BigDecimal(s).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new CampoInvalidoException(key, mensajeInvalido);
        }
    }

    /**
     * RN-CU15-01: no pueden existir dos registros ACTIVOS con el mismo nombre en el mismo catálogo.
     * En los catálogos cuya tabla tiene un índice único sobre "nombre" (global = true) también
     * se compara contra los inactivos, porque la base de datos los rechazaría de todas formas.
     */
    private static void validarNombreUnico(List<Map<String, Object>> filas, String nombre, Integer idActual,
                                           boolean quedaActivo, boolean global) {
        if (!quedaActivo && !global) return;
        for (Map<String, Object> f : filas) {
            if (idActual != null && idActual.equals(f.get("id"))) continue;
            if (!nombre.equalsIgnoreCase(String.valueOf(f.get("nombre")))) continue;
            boolean activo = !f.containsKey("estado") || Boolean.TRUE.equals(f.get("estado"));
            if (activo) {
                throw new CampoInvalidoException("nombre", "Ya existe un registro con el nombre " + nombre + ".");
            }
            if (global) {
                throw new CampoInvalidoException("nombre", "Ya existe un registro con el nombre " + nombre
                        + " (está inactivo; edítelo para volver a activarlo).");
            }
        }
    }

    private static ResponseStatusException noExiste() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "El registro no existe.");
    }

    // ======================================================================
    // Handlers
    // ======================================================================

    private abstract class Handler {
        final String titulo;
        final String singular;
        final String tabla;
        final String descripcion;
        final String icono;
        boolean puedeEliminar = true;
        boolean tieneEstado = true;

        Handler(String titulo, String singular, String tabla, String descripcion, String icono) {
            this.titulo = titulo;
            this.singular = singular;
            this.tabla = tabla;
            this.descripcion = descripcion;
            this.icono = icono;
        }

        abstract List<Map<String, Object>> columnas();
        abstract List<Map<String, Object>> campos();
        abstract List<Map<String, Object>> filas();
        abstract Map<String, Object> fila(Integer id);
        abstract Map<String, Object> crear(Map<String, Object> d, Integer idUsuario);
        abstract Map<String, Object> actualizar(Integer id, Map<String, Object> d);
        /** Eliminación lógica; devuelve la etiqueta del registro eliminado. */
        abstract String eliminar(Integer id);
    }

    // ---------------------------------------------------------------- Especialidades
    private class EspecialidadHandler extends Handler {
        EspecialidadHandler() { super("Especialidades", "Especialidad", "especialidades", "Especialidades médicas que ofrece el hospital.", "fa-stethoscope"); }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("nombre", "Nombre", "texto"),
                    columna("descripcion", "Descripción", "texto"), columna("estado", "Estado", "estado"));
        }
        List<Map<String, Object>> campos() {
            return List.of(campo("nombre", "Nombre", "text", true, 200), campo("descripcion", "Descripción", "textarea", true, 500));
        }
        List<Map<String, Object>> filas() { return especialidadRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(especialidadRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            Especialidad e = new Especialidad();
            volcar(e, d, null);
            return aFila(especialidadRepository.save(e));
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            Especialidad e = especialidadRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            volcar(e, d, id);
            return aFila(especialidadRepository.save(e));
        }
        String eliminar(Integer id) {
            Especialidad e = especialidadRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            e.setEstado(false);
            especialidadRepository.save(e);
            return e.getNombre();
        }

        private void volcar(Especialidad e, Map<String, Object> d, Integer idActual) {
            String nombre = textoObligatorio(d, "nombre", 200, "El nombre es obligatorio.");
            String desc = textoObligatorio(d, "descripcion", 500, "La descripción es obligatoria.");
            boolean estado = booleano(d, "estado", true);
            validarNombreUnico(filas(), nombre, idActual, estado, true);
            e.setNombre(nombre);
            e.setDescripcion(desc);
            e.setEstado(estado);
        }
        private Map<String, Object> aFila(Especialidad e) {
            return mapa("id", e.getIdEspecialidad(), "etiqueta", e.getNombre(), "nombre", e.getNombre(),
                    "descripcion", e.getDescripcion(), "estado", Boolean.TRUE.equals(e.getEstado()));
        }
    }

    // ---------------------------------------------------------------- Sucursales
    private class SucursalHandler extends Handler {
        SucursalHandler() { super("Sucursales", "Sucursal", "sucursales", "Sedes físicas del hospital.", "fa-hospital"); }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("nombre", "Nombre", "texto"),
                    columna("telefono", "Teléfono", "mono"), columna("direccion", "Dirección", "texto"),
                    columna("estado", "Estado", "estado"));
        }
        List<Map<String, Object>> campos() {
            Map<String, Object> tel = campo("telefono", "Teléfono", "tel", false, 8);
            tel.put("ayuda", "8 dígitos, sin guiones.");
            return List.of(campo("nombre", "Nombre", "text", true, 100), tel,
                    campo("direccion", "Dirección", "textarea", false, 500),
                    campo("descripcion", "Descripción", "textarea", false, 250));
        }
        List<Map<String, Object>> filas() { return sucursalRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(sucursalRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            Sucursal s = new Sucursal();
            volcar(s, d, null);
            return aFila(sucursalRepository.save(s));
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            Sucursal s = sucursalRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            volcar(s, d, id);
            return aFila(sucursalRepository.save(s));
        }
        String eliminar(Integer id) {
            Sucursal s = sucursalRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            s.setEstado(false);
            sucursalRepository.save(s);
            return s.getNombre();
        }

        private void volcar(Sucursal s, Map<String, Object> d, Integer idActual) {
            String nombre = textoObligatorio(d, "nombre", 100, "El nombre es obligatorio.");
            String tel = texto(d, "telefono");
            if (tel != null && !tel.matches("\\d{8}")) {
                throw new CampoInvalidoException("telefono", "El teléfono debe tener exactamente 8 dígitos.");
            }
            String dir = textoOpcional(d, "direccion", 500);
            String desc = textoOpcional(d, "descripcion", 250);
            boolean estado = booleano(d, "estado", true);
            validarNombreUnico(filas(), nombre, idActual, estado, false);
            s.setNombre(nombre);
            s.setTelefono(tel);
            s.setDireccion(dir);
            s.setDescripcion(desc);
            s.setEstado(estado);
        }
        private Map<String, Object> aFila(Sucursal s) {
            return mapa("id", s.getIdSucursal(), "etiqueta", s.getNombre(), "nombre", s.getNombre(),
                    "telefono", s.getTelefono(), "direccion", s.getDireccion(), "descripcion", s.getDescripcion(),
                    "estado", Boolean.TRUE.equals(s.getEstado()));
        }
    }

    // ---------------------------------------------------------------- Estados de cita
    private class EstadoCitaHandler extends Handler {
        EstadoCitaHandler() {
            super("Estados de Cita", "Estado de cita", "estados_cita", "Estados por los que pasa una cita.", "fa-list-check");
            this.puedeEliminar = false;
            this.tieneEstado = false;
        }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("nombre", "Nombre", "texto"), columna("descripcion", "Descripción", "texto"));
        }
        List<Map<String, Object>> campos() {
            Map<String, Object> nombre = campo("nombre", "Nombre", "text", true, 30);
            nombre.put("bloqueadoEdicion", true);
            nombre.put("ayuda", "El nombre no se puede cambiar: el sistema identifica cada estado por su nombre.");
            return List.of(nombre, campo("descripcion", "Descripción", "textarea", false, 200));
        }
        List<Map<String, Object>> filas() { return estadoCitaRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(estadoCitaRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            String nombre = textoObligatorio(d, "nombre", 30, "El nombre es obligatorio.");
            String desc = textoOpcional(d, "descripcion", 200);
            validarNombreUnico(filas(), nombre, null, true, true);
            EstadoCita e = new EstadoCita();
            e.setNombreEstado(nombre);
            e.setDescripcion(desc);
            return aFila(estadoCitaRepository.save(e));
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            EstadoCita e = estadoCitaRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            e.setDescripcion(textoOpcional(d, "descripcion", 200)); // el nombre no se modifica
            return aFila(estadoCitaRepository.save(e));
        }
        String eliminar(Integer id) { throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Este catálogo no permite eliminar registros."); }

        private Map<String, Object> aFila(EstadoCita e) {
            return mapa("id", e.getIdEstadoCita(), "etiqueta", e.getNombreEstado(), "nombre", e.getNombreEstado(),
                    "descripcion", e.getDescripcion());
        }
    }

    // ---------------------------------------------------------------- Laboratorios
    private class LaboratorioHandler extends Handler {
        LaboratorioHandler() { super("Laboratorios", "Laboratorio", "laboratorios", "Laboratorios que procesan los exámenes.", "fa-microscope"); }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("nombre", "Nombre", "texto"),
                    columna("descripcion", "Descripción", "texto"), columna("estado", "Estado", "estado"));
        }
        List<Map<String, Object>> campos() {
            return List.of(campo("nombre", "Nombre", "text", true, 200), campo("descripcion", "Descripción", "textarea", false, 500));
        }
        List<Map<String, Object>> filas() { return laboratorioRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(laboratorioRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            Laboratorio l = new Laboratorio();
            volcar(l, d, null);
            return aFila(laboratorioRepository.save(l));
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            Laboratorio l = laboratorioRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            volcar(l, d, id);
            return aFila(laboratorioRepository.save(l));
        }
        String eliminar(Integer id) {
            Laboratorio l = laboratorioRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            l.setEstado(false);
            laboratorioRepository.save(l);
            return l.getNombre();
        }

        private void volcar(Laboratorio l, Map<String, Object> d, Integer idActual) {
            String nombre = textoObligatorio(d, "nombre", 200, "El nombre es obligatorio.");
            String desc = textoOpcional(d, "descripcion", 500);
            boolean estado = booleano(d, "estado", true);
            validarNombreUnico(filas(), nombre, idActual, estado, true);
            l.setNombre(nombre);
            l.setDescripcion(desc);
            l.setEstado(estado);
        }
        private Map<String, Object> aFila(Laboratorio l) {
            return mapa("id", l.getIdLaboratorio(), "etiqueta", l.getNombre(), "nombre", l.getNombre(),
                    "descripcion", l.getDescripcion(), "estado", Boolean.TRUE.equals(l.getEstado()));
        }
    }

    // ---------------------------------------------------------------- Exámenes de laboratorio
    private class ExamenHandler extends Handler {
        ExamenHandler() { super("Exámenes de Laboratorio", "Examen", "examenes_laboratorio", "Exámenes disponibles para ordenar, con su precio base.", "fa-vial"); }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("nombre", "Nombre", "texto"),
                    columna("laboratorioNombre", "Laboratorio", "texto"), columna("precio", "Precio", "moneda"),
                    columna("unidadMedida", "Unidad", "texto"), columna("estado", "Estado", "estado"));
        }
        List<Map<String, Object>> campos() {
            Map<String, Object> lab = campo("idLaboratorio", "Laboratorio", "select", true, null);
            lab.put("opciones", laboratorioRepository.findAll().stream().filter(l -> Boolean.TRUE.equals(l.getEstado()))
                    .map(l -> mapa("id", l.getIdLaboratorio(), "nombre", l.getNombre())).toList());
            Map<String, Object> precio = campo("precio", "Precio base (Q)", "number", true, null);
            precio.put("min", "0.01");
            precio.put("step", "0.01");
            return List.of(campo("nombre", "Nombre", "text", true, 200), lab, precio,
                    campo("rangoReferencia", "Rango de referencia", "text", false, 100),
                    campo("unidadMedida", "Unidad", "text", false, 30),
                    campo("descripcion", "Descripción", "textarea", false, 500));
        }
        List<Map<String, Object>> filas() { return examenRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(examenRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            ExamenLaboratorio e = new ExamenLaboratorio();
            volcar(e, d, null);
            return aFila(examenRepository.save(e));
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            ExamenLaboratorio e = examenRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            volcar(e, d, id);
            return aFila(examenRepository.save(e));
        }
        String eliminar(Integer id) {
            ExamenLaboratorio e = examenRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            e.setEstado(false);
            examenRepository.save(e);
            return e.getNombre();
        }

        private void volcar(ExamenLaboratorio e, Map<String, Object> d, Integer idActual) {
            String nombre = textoObligatorio(d, "nombre", 200, "El nombre es obligatorio.");
            Integer idLab = entero(d, "idLaboratorio", "Debe seleccionar un laboratorio.");
            Laboratorio lab = idLab == null ? null : laboratorioRepository.findById(idLab).filter(l -> Boolean.TRUE.equals(l.getEstado())).orElse(null);
            if (lab == null) throw new CampoInvalidoException("idLaboratorio", "Debe seleccionar un laboratorio.");
            BigDecimal precio = decimal(d, "precio", "El precio base debe ser mayor a 0.");
            String rango = textoOpcional(d, "rangoReferencia", 100);
            String unidad = textoOpcional(d, "unidadMedida", 30);
            String desc = textoOpcional(d, "descripcion", 500);
            boolean estado = booleano(d, "estado", true);
            validarNombreUnico(filas(), nombre, idActual, estado, false);
            e.setNombre(nombre);
            e.setLaboratorio(lab);
            e.setPrecio(precio);
            e.setRangoReferencia(rango);
            e.setUnidadMedida(unidad);
            e.setDescripcion(desc);
            e.setEstado(estado);
        }
        private Map<String, Object> aFila(ExamenLaboratorio e) {
            return mapa("id", e.getIdExamen(), "etiqueta", e.getNombre(), "nombre", e.getNombre(),
                    "idLaboratorio", e.getLaboratorio().getIdLaboratorio(), "laboratorioNombre", e.getLaboratorio().getNombre(),
                    "precio", e.getPrecio(), "rangoReferencia", e.getRangoReferencia(), "unidadMedida", e.getUnidadMedida(),
                    "descripcion", e.getDescripcion(), "estado", Boolean.TRUE.equals(e.getEstado()));
        }
    }

    // ---------------------------------------------------------------- Medicamentos
    private class MedicamentoHandler extends Handler {
        MedicamentoHandler() { super("Medicamentos", "Medicamento", "medicamentos", "Medicamentos de farmacia, precio y stock mínimo.", "fa-pills"); }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("nombre", "Nombre", "texto"),
                    columna("unidad", "Unidad", "texto"), columna("precio", "Precio", "moneda"),
                    columna("stockMinimo", "Stock mín.", "mono"), columna("esControlado", "Controlado", "controlado"),
                    columna("estado", "Estado", "estado"));
        }
        List<Map<String, Object>> campos() {
            Map<String, Object> precio = campo("precio", "Precio (Q)", "number", true, null);
            precio.put("min", "0.01");
            precio.put("step", "0.01");
            Map<String, Object> unidad = campo("unidad", "Unidad (tableta, ml, etc.)", "text", true, 50);
            Map<String, Object> min = campo("stockMinimo", "Stock mínimo", "number", false, null);
            min.put("min", "0");
            min.put("step", "1");
            Map<String, Object> ctrl = campo("esControlado", "Medicamento controlado", "checkbox", false, null);
            ctrl.put("ayuda", "Se aplica auditoría especial en el despacho.");
            return List.of(campo("nombre", "Nombre", "text", true, 200), campo("descripcion", "Descripción", "textarea", true, 500),
                    precio, unidad, min, ctrl);
        }
        List<Map<String, Object>> filas() { return medicamentoRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(medicamentoRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            Medicamento m = new Medicamento();
            volcar(m, d, null);
            return aFila(medicamentoRepository.save(m));
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            Medicamento m = medicamentoRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            volcar(m, d, id);
            return aFila(medicamentoRepository.save(m));
        }
        String eliminar(Integer id) {
            Medicamento m = medicamentoRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            m.setEstado(false);
            medicamentoRepository.save(m);
            return m.getNombre();
        }

        private void volcar(Medicamento m, Map<String, Object> d, Integer idActual) {
            String nombre = textoObligatorio(d, "nombre", 200, "El nombre es obligatorio.");
            String desc = textoObligatorio(d, "descripcion", 500, "La descripción es obligatoria.");
            BigDecimal precio = decimal(d, "precio", "El precio debe ser mayor a 0.");
            String unidad = textoObligatorio(d, "unidad", 50, "La unidad es obligatoria.");
            Integer minimo = entero(d, "stockMinimo", "El stock mínimo debe ser un número entero mayor o igual a 0.");
            if (minimo != null && minimo < 0) {
                throw new CampoInvalidoException("stockMinimo", "El stock mínimo debe ser un número entero mayor o igual a 0.");
            }
            boolean estado = booleano(d, "estado", true);
            validarNombreUnico(filas(), nombre, idActual, estado, true);
            m.setNombre(nombre);
            m.setDescripcion(desc);
            m.setPrecio(precio);
            m.setUnidad(unidad);
            m.setStockMinimo(minimo == null ? 0 : minimo);
            m.setEsControlado(booleano(d, "esControlado", false));
            m.setEstado(estado);
        }
        private Map<String, Object> aFila(Medicamento m) {
            return mapa("id", m.getIdMedicamento(), "etiqueta", m.getNombre(), "nombre", m.getNombre(),
                    "descripcion", m.getDescripcion(), "precio", m.getPrecio(), "unidad", m.getUnidad(),
                    "stockMinimo", m.getStockMinimo(), "esControlado", Boolean.TRUE.equals(m.getEsControlado()),
                    "estado", Boolean.TRUE.equals(m.getEstado()));
        }
    }

    // ---------------------------------------------------------------- Roles
    private class RolHandler extends Handler {
        RolHandler() { super("Roles", "Rol", "roles", "Roles de acceso de los usuarios del sistema.", "fa-user-shield"); }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("nombre", "Nombre", "texto"),
                    columna("descripcion", "Descripción", "texto"), columna("estado", "Estado", "estado"));
        }
        List<Map<String, Object>> campos() {
            Map<String, Object> nombre = campo("nombre", "Nombre", "text", true, 50);
            nombre.put("bloqueadoEdicion", true);
            nombre.put("ayuda", "El nombre de un rol no se puede cambiar: el sistema lo identifica por su nombre.");
            return List.of(nombre, campo("descripcion", "Descripción", "textarea", false, 255));
        }
        List<Map<String, Object>> filas() { return rolRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(rolRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            String nombre = textoObligatorio(d, "nombre", 50, "El nombre es obligatorio.");
            String desc = textoOpcional(d, "descripcion", 255);
            boolean estado = booleano(d, "estado", true);
            validarNombreUnico(filas(), nombre, null, estado, true);
            Rol r = new Rol();
            r.setNombreRol(nombre);
            r.setDescripcion(desc);
            r.setEstado(estado);
            return aFila(rolRepository.save(r));
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            Rol r = rolRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            boolean estado = booleano(d, "estado", true);
            if (!estado) validarSinUsuarios(r);
            r.setDescripcion(textoOpcional(d, "descripcion", 255)); // el nombre no se modifica
            if (estado && !Boolean.TRUE.equals(r.getEstado())) validarNombreUnico(filas(), r.getNombreRol(), id, true, true);
            r.setEstado(estado);
            return aFila(rolRepository.save(r));
        }
        String eliminar(Integer id) {
            Rol r = rolRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            validarSinUsuarios(r);
            r.setEstado(false);
            rolRepository.save(r);
            return r.getNombreRol();
        }

        private void validarSinUsuarios(Rol r) {
            if (usuarioRepository.countByRol_IdRol(r.getIdRol()) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se puede desactivar el rol " + r.getNombreRol() + " porque tiene usuarios asignados.");
            }
        }
        private Map<String, Object> aFila(Rol r) {
            return mapa("id", r.getIdRol(), "etiqueta", r.getNombreRol(), "nombre", r.getNombreRol(),
                    "descripcion", r.getDescripcion(), "estado", Boolean.TRUE.equals(r.getEstado()));
        }
    }

    // ---------------------------------------------------------------- Inventario de medicamentos
    private class InventarioHandler extends Handler {
        InventarioHandler() { super("Inventario de Medicamentos", "Inventario", "inventario_medicamentos", "Existencias de cada medicamento por sucursal, con alerta de stock bajo.", "fa-boxes-stacked"); }

        List<Map<String, Object>> columnas() {
            return List.of(columna("id", "ID", "mono"), columna("medicamentoNombre", "Medicamento", "texto"),
                    columna("sucursalNombre", "Sucursal", "texto"), columna("stockActual", "Stock actual", "mono"),
                    columna("stockMinimo", "Stock mín.", "mono"), columna("alertaStockBajo", "Alerta", "alerta"),
                    columna("estado", "Estado", "estado"));
        }
        List<Map<String, Object>> campos() {
            Map<String, Object> med = campo("idMedicamento", "Medicamento", "select", true, null);
            med.put("opciones", medicamentoRepository.findByEstadoTrueOrderByNombreAsc().stream()
                    .map(m -> mapa("id", m.getIdMedicamento(), "nombre", m.getNombre())).toList());
            med.put("bloqueadoEdicion", true);
            Map<String, Object> suc = campo("idSucursal", "Sucursal", "select", true, null);
            suc.put("opciones", sucursalRepository.findByEstadoTrue().stream()
                    .map(s -> mapa("id", s.getIdSucursal(), "nombre", s.getNombre())).toList());
            suc.put("bloqueadoEdicion", true);
            Map<String, Object> stock = campo("stockActual", "Stock inicial", "number", true, null);
            stock.put("min", "0");
            stock.put("step", "1");
            stock.put("bloqueadoEdicion", true);
            stock.put("ayuda", "Después de crearlo, el stock solo cambia con movimientos de la Bitácora de Inventario.");
            return List.of(med, suc, stock);
        }
        List<Map<String, Object>> filas() { return inventarioRepository.findAll().stream().map(this::aFila).toList(); }
        Map<String, Object> fila(Integer id) { return aFila(inventarioRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste)); }

        Map<String, Object> crear(Map<String, Object> d, Integer idUsuario) {
            Integer idMed = entero(d, "idMedicamento", "Debe seleccionar un medicamento.");
            Medicamento med = idMed == null ? null : medicamentoRepository.findById(idMed).filter(m -> Boolean.TRUE.equals(m.getEstado())).orElse(null);
            if (med == null) throw new CampoInvalidoException("idMedicamento", "Debe seleccionar un medicamento.");
            Integer idSuc = entero(d, "idSucursal", "Debe seleccionar una sucursal.");
            Sucursal suc = idSuc == null ? null : sucursalRepository.findById(idSuc).filter(s -> Boolean.TRUE.equals(s.getEstado())).orElse(null);
            if (suc == null) throw new CampoInvalidoException("idSucursal", "Debe seleccionar una sucursal.");
            Integer stock = entero(d, "stockActual", "El stock debe ser un número entero mayor o igual a 0.");
            if (stock == null || stock < 0) throw new CampoInvalidoException("stockActual", "El stock debe ser un número entero mayor o igual a 0.");

            InventarioMedicamento inv = inventarioRepository
                    .findByMedicamento_IdMedicamentoAndSucursal_IdSucursal(med.getIdMedicamento(), suc.getIdSucursal()).orElse(null);
            if (inv != null && Boolean.TRUE.equals(inv.getEstado())) {
                throw new CampoInvalidoException("idMedicamento", "Ya existe inventario de este medicamento en la sucursal.");
            }
            int base = 0;
            if (inv == null) {
                inv = new InventarioMedicamento();
                inv.setMedicamento(med);
                inv.setSucursal(suc);
                inv.setStockActual(0);
                inv.setVersionControl(0);
            } else {
                base = inv.getStockActual(); // fila eliminada antes: se reactiva conservando su stock
            }
            inv.setEstado(true);
            inv = inventarioRepository.saveAndFlush(inv);
            if (stock > 0) {
                inventarioRepository.aumentarStock(inv.getIdInventario(), stock);
                registrarMovimientoInicial(med, suc, base, stock, idUsuario);
            }
            // lectura real de la BD: el UPDATE masivo no actualiza la entidad en memoria
            Map<String, Object> fila = aFila(inv);
            fila.put("stockActual", inventarioRepository.obtenerStock(inv.getIdInventario()));
            return fila;
        }
        Map<String, Object> actualizar(Integer id, Map<String, Object> d) {
            InventarioMedicamento inv = inventarioRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            inv.setEstado(booleano(d, "estado", true)); // medicamento, sucursal y stock no se editan aquí
            return aFila(inventarioRepository.save(inv));
        }
        String eliminar(Integer id) {
            InventarioMedicamento inv = inventarioRepository.findById(id).orElseThrow(CatalogoAdminService::noExiste);
            inv.setEstado(false);
            inventarioRepository.save(inv);
            return inv.getMedicamento().getNombre() + " — " + inv.getSucursal().getNombre();
        }

        private void registrarMovimientoInicial(Medicamento med, Sucursal suc, int base, int cantidad, Integer idUsuario) {
            // trazabilidad: el stock inicial queda en la bitácora como un Ajuste+ (si el usuario existe)
            tipoMovimientoRepository.findByNombre("Ajuste+").ifPresent(tipo -> {
                if (idUsuario == null) return;
                usuarioRepository.findById(idUsuario).ifPresent(u -> {
                    MovimientoInventario mov = new MovimientoInventario();
                    mov.setMedicamento(med);
                    mov.setSucursal(suc);
                    mov.setTipoMovimiento(tipo);
                    mov.setCantidad(cantidad);
                    mov.setStockAnterior(base);
                    mov.setStockNuevo(base + cantidad);
                    mov.setNumeroReferencia("INV-CATALOGO");
                    mov.setMotivoNotas("Stock inicial registrado desde el catálogo de inventario.");
                    mov.setUsuario(u);
                    mov.setEstado(true);
                    movimientoRepository.save(mov);
                });
            });
        }
        private Map<String, Object> aFila(InventarioMedicamento i) {
            int min = i.getMedicamento().getStockMinimo() == null ? 0 : i.getMedicamento().getStockMinimo();
            boolean bajo = min > 0 && i.getStockActual() <= min;
            return mapa("id", i.getIdInventario(), "etiqueta", i.getMedicamento().getNombre() + " — " + i.getSucursal().getNombre(),
                    "nombre", i.getMedicamento().getNombre() + " — " + i.getSucursal().getNombre(),
                    "idMedicamento", i.getMedicamento().getIdMedicamento(), "medicamentoNombre", i.getMedicamento().getNombre(),
                    "idSucursal", i.getSucursal().getIdSucursal(), "sucursalNombre", i.getSucursal().getNombre(),
                    "stockActual", i.getStockActual(), "stockMinimo", min, "alertaStockBajo", bajo,
                    "estado", Boolean.TRUE.equals(i.getEstado()));
        }
    }
}
