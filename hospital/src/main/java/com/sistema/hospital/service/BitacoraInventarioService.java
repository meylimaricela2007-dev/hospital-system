package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU-15: Bitácora de Movimientos de Inventario.
 *
 * Movimientos manuales: Compra, Devolución, Ajuste+ (entradas) y Venta,
 * Reclamo, Ajuste- (salidas). "Despacho" lo genera CU-11 automáticamente y
 * no se ofrece en el formulario. Los tipos se buscan por NOMBRE (no por id),
 * igual que en CU-11, porque los ids de la tabla no empiezan en 0.
 *
 * El stock se modifica con UPDATE atómicos (ver InventarioMedicamentoRepository),
 * así que dos movimientos simultáneos no pueden dejar el stock en negativo
 * (RN-CU13-02 / RNF-025).
 */
@Service
public class BitacoraInventarioService {

    private static final Set<String> ENTRADAS = Set.of("Compra", "Devolución", "Ajuste+");
    private static final Set<String> SALIDAS = Set.of("Venta", "Reclamo", "Ajuste-");
    private static final Set<String> MOTIVO_OBLIGATORIO = Set.of("Ajuste+", "Ajuste-", "Devolución", "Reclamo");

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;
    @Autowired
    private InventarioMedicamentoRepository inventarioRepository;
    @Autowired
    private TipoMovimientoInventarioRepository tipoRepository;
    @Autowired
    private MedicamentoRepository medicamentoRepository;
    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private AuditoriaService auditoriaService;

    // ---------------------------------------------------------------- listado

    @Transactional(readOnly = true)
    public Page<MovimientoInventarioDTO> listar(Integer idTipo, Integer idMedicamento, Integer idSucursal,
                                                String referencia, String usuario, LocalDate desde, LocalDate hasta,
                                                int page, int size, Integer idUsuarioSesion) {
        int tamano = (size == 10 || size == 25 || size == 50) ? size : 10;
        Integer sedeFija = sucursalFija(idUsuarioSesion);
        Integer sedeFiltro = sedeFija != null ? sedeFija : idSucursal;

        Specification<MovimientoInventario> spec = filtros(idTipo, idMedicamento, sedeFiltro, referencia, usuario, desde, hasta);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamano,
                Sort.by(Sort.Direction.DESC, "fechaHora").and(Sort.by(Sort.Direction.DESC, "idMovimiento")));
        return movimientoRepository.findAll(spec, pageable).map(BitacoraInventarioService::convertir);
    }

    @Transactional(readOnly = true)
    public MovimientoInventarioDTO obtener(Integer id, Integer idUsuarioSesion) {
        MovimientoInventario m = movimientoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El movimiento no existe."));
        validarSucursalPermitida(m.getSucursal().getIdSucursal(), idUsuarioSesion);
        return convertir(m);
    }

    @Transactional(readOnly = true)
    public ResumenMensualInventarioDTO resumenMensual(Integer anio, Integer mes, Integer idUsuarioSesion) {
        LocalDate hoy = LocalDate.now();
        int a = anio != null ? anio : hoy.getYear();
        int m = mes != null ? mes : hoy.getMonthValue();
        if (m < 1 || m > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mes debe estar entre 1 y 12.");
        }
        LocalDate primero = LocalDate.of(a, m, 1);
        LocalDate ultimo = primero.withDayOfMonth(primero.lengthOfMonth());

        Specification<MovimientoInventario> spec = filtros(null, null, sucursalFija(idUsuarioSesion), null, null, primero, ultimo)
                .and((root, q, cb) -> cb.isTrue(root.get("estado")));
        long total = 0, entradas = 0, salidas = 0;
        int diasDelMes = ultimo.getDayOfMonth();
        long[] entradasPorDia = new long[diasDelMes + 1];
        long[] salidasPorDia = new long[diasDelMes + 1];
        Map<String, Long> unidadesPorTipo = new LinkedHashMap<>();
        for (String t : List.of("Compra", "Devolución", "Ajuste+", "Venta", "Reclamo", "Ajuste-", "Despacho")) {
            unidadesPorTipo.put(t, 0L);                       // todos los tipos salen en la gráfica, aunque sea con 0
        }
        Map<String, Long> salidasPorMedicamento = new LinkedHashMap<>();

        for (MovimientoInventario mov : movimientoRepository.findAll(spec)) {
            total++;
            String tipo = mov.getTipoMovimiento().getNombre();
            int dia = mov.getFechaHora().getDayOfMonth();
            unidadesPorTipo.merge(tipo, (long) mov.getCantidad(), Long::sum);
            if (ENTRADAS.contains(tipo)) {
                entradas += mov.getCantidad();
                entradasPorDia[dia] += mov.getCantidad();
            } else {
                salidas += mov.getCantidad(); // Venta, Reclamo, Ajuste- y Despacho
                salidasPorDia[dia] += mov.getCantidad();
                salidasPorMedicamento.merge(mov.getMedicamento().getNombre(), (long) mov.getCantidad(), Long::sum);
            }
        }

        // Datos para las gráficas (el cálculo vive en el Service; el JavaScript solo dibuja)
        List<ResumenMensualInventarioDTO.Punto> porTipo = unidadesPorTipo.entrySet().stream()
                .map(e -> new ResumenMensualInventarioDTO.Punto(e.getKey(), e.getValue()))
                .toList();
        List<ResumenMensualInventarioDTO.PuntoDia> porDia = new ArrayList<>();
        for (int d = 1; d <= diasDelMes; d++) {
            porDia.add(new ResumenMensualInventarioDTO.PuntoDia(d, entradasPorDia[d], salidasPorDia[d]));
        }
        List<ResumenMensualInventarioDTO.Punto> topSalidas = salidasPorMedicamento.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new ResumenMensualInventarioDTO.Punto(e.getKey(), e.getValue()))
                .toList();

        return new ResumenMensualInventarioDTO(a, m, total, entradas, salidas, porTipo, porDia, topSalidas);
    }

    // ---------------------------------------------------------------- catálogos / panel

    @Transactional(readOnly = true)
    public MovimientoCatalogosDTO catalogos(Integer idUsuarioSesion) {
        Integer sedeFija = sucursalFija(idUsuarioSesion);
        List<CatalogoItemDTO> medicamentos = medicamentoRepository.findByEstadoTrueOrderByNombreAsc().stream()
                .map(m -> new CatalogoItemDTO(m.getIdMedicamento(), m.getNombre())).toList();
        List<CatalogoItemDTO> sucursales = sucursalRepository.findByEstadoTrue().stream()
                .filter(s -> sedeFija == null || s.getIdSucursal().equals(sedeFija))
                .map(s -> new CatalogoItemDTO(s.getIdSucursal(), s.getNombre())).toList();

        List<MovimientoCatalogosDTO.TipoMovimientoOpcion> tipos = new ArrayList<>();
        for (String nombre : List.of("Compra", "Devolución", "Venta", "Reclamo", "Ajuste+", "Ajuste-")) {
            tipoRepository.findByNombre(nombre).ifPresent(t ->
                    tipos.add(new MovimientoCatalogosDTO.TipoMovimientoOpcion(t.getIdTipoMovimiento(), t.getNombre(), ENTRADAS.contains(nombre))));
        }
        List<MovimientoCatalogosDTO.TipoMovimientoOpcion> todos = tipoRepository.findAll().stream()
                .map(t -> new MovimientoCatalogosDTO.TipoMovimientoOpcion(t.getIdTipoMovimiento(), t.getNombre(), ENTRADAS.contains(t.getNombre())))
                .toList();
        return new MovimientoCatalogosDTO(medicamentos, sucursales, tipos, sedeFija != null, todos);
    }

    @Transactional(readOnly = true)
    public InventarioInfoDTO info(Integer idMedicamento, Integer idSucursal, Integer idUsuarioSesion) {
        validarSucursalPermitida(idSucursal, idUsuarioSesion);
        Medicamento med = medicamentoRepository.findById(idMedicamento)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un medicamento."));
        int minimo = med.getStockMinimo() == null ? 0 : med.getStockMinimo();
        return inventarioRepository.findByMedicamento_IdMedicamentoAndSucursal_IdSucursal(idMedicamento, idSucursal)
                .map(i -> new InventarioInfoDTO(true, i.getStockActual(), minimo))
                .orElseGet(() -> new InventarioInfoDTO(false, 0, minimo));
    }

    // ---------------------------------------------------------------- registrar

    @Transactional
    public Map<String, Object> registrar(RegistrarMovimientoRequest r, Integer idUsuarioSesion) {
        Medicamento medicamento = medicamentoRepository.findById(r.getIdMedicamento())
                .filter(m -> Boolean.TRUE.equals(m.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un medicamento."));
        Sucursal sucursal = sucursalRepository.findById(r.getIdSucursal())
                .filter(s -> Boolean.TRUE.equals(s.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar una sucursal."));
        validarSucursalPermitida(sucursal.getIdSucursal(), idUsuarioSesion);

        TipoMovimientoInventario tipo = tipoRepository.findById(r.getIdTipoMovimiento())
                .filter(t -> ENTRADAS.contains(t.getNombre()) || SALIDAS.contains(t.getNombre()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar el tipo de movimiento."));
        String nombreTipo = tipo.getNombre();
        boolean esEntrada = ENTRADAS.contains(nombreTipo);

        BigDecimal costo = validarCosto(nombreTipo, r.getCostoUnitario());
        String motivo = validarMotivo(nombreTipo, r.getMotivo());
        String referencia = r.getNumeroReferencia() == null || r.getNumeroReferencia().isBlank() ? null : r.getNumeroReferencia().trim();

        Usuario usuario = usuarioRepository.findById(idUsuarioSesion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar."));

        int cantidad = r.getCantidad();
        InventarioMedicamento inv = inventarioRepository
                .findByMedicamento_IdMedicamentoAndSucursal_IdSucursal(medicamento.getIdMedicamento(), sucursal.getIdSucursal())
                .orElse(null);

        int stockNuevo;
        if (esEntrada) {
            if (inv == null) {
                inv = new InventarioMedicamento();
                inv.setMedicamento(medicamento);
                inv.setSucursal(sucursal);
                inv.setStockActual(0);
                inv.setEstado(true);
                inv.setVersionControl(0);
                inv = inventarioRepository.saveAndFlush(inv);
            }
            inventarioRepository.aumentarStock(inv.getIdInventario(), cantidad);
            stockNuevo = inventarioRepository.obtenerStock(inv.getIdInventario());
        } else {
            // FA02 / RN-CU13-02: salida solo si alcanza el stock
            if (inv == null) {
                throw stockInsuficiente(0, cantidad);
            }
            if (inventarioRepository.descontarStock(inv.getIdInventario(), cantidad) == 0) {
                throw stockInsuficiente(inventarioRepository.obtenerStock(inv.getIdInventario()), cantidad);
            }
            stockNuevo = inventarioRepository.obtenerStock(inv.getIdInventario());
        }
        int stockAnterior = esEntrada ? stockNuevo - cantidad : stockNuevo + cantidad;

        MovimientoInventario mov = new MovimientoInventario();
        mov.setMedicamento(medicamento);
        mov.setSucursal(sucursal);
        mov.setTipoMovimiento(tipo);
        mov.setCantidad(cantidad);
        mov.setStockAnterior(stockAnterior);
        mov.setStockNuevo(stockNuevo);
        mov.setCostoUnitario(costo);
        mov.setNumeroReferencia(referencia);
        mov.setMotivoNotas(motivo);
        mov.setUsuario(usuario);
        mov.setEstado(true);
        mov = movimientoRepository.save(mov);

        auditoriaService.registrar(idUsuarioSesion, "REGISTRAR_MOVIMIENTO_INVENTARIO", "movimientos_inventario",
                mov.getIdMovimiento(), nombreTipo + " de " + cantidad + " u. de '" + medicamento.getNombre()
                        + "' en '" + sucursal.getNombre() + "' (stock " + stockAnterior + " → " + stockNuevo + ").");

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("success", true);
        respuesta.put("idMovimiento", mov.getIdMovimiento());
        respuesta.put("mensaje", "Movimiento registrado exitosamente. Medicamento: " + medicamento.getNombre()
                + ". Tipo: " + nombreTipo + ". Cantidad: " + cantidad + ". Stock actualizado: " + stockNuevo + ".");

        // FA05 / RN-CU10-03: alerta de stock mínimo
        int minimo = medicamento.getStockMinimo() == null ? 0 : medicamento.getStockMinimo();
        if (minimo > 0 && stockNuevo <= minimo) {
            respuesta.put("alerta", medicamento.getNombre() + ": Stock bajo — disponible: " + stockNuevo + " (mínimo: " + minimo + ")");
        }
        return respuesta;
    }

    // ---------------------------------------------------------------- activar / desactivar

    /** Toggle del registro de bitácora. NO revierte el stock: solo marca si el registro se considera vigente. */
    @Transactional
    public String cambiarEstado(Integer id, Integer idUsuarioSesion) {
        MovimientoInventario m = movimientoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El movimiento no existe."));
        validarSucursalPermitida(m.getSucursal().getIdSucursal(), idUsuarioSesion);
        boolean nuevo = !Boolean.TRUE.equals(m.getEstado());
        m.setEstado(nuevo);
        movimientoRepository.save(m);
        auditoriaService.registrar(idUsuarioSesion, nuevo ? "ACTIVAR_MOVIMIENTO_INVENTARIO" : "DESACTIVAR_MOVIMIENTO_INVENTARIO",
                "movimientos_inventario", id, "Movimiento #" + id + (nuevo ? " activado." : " desactivado."));
        return nuevo ? "Movimiento activado correctamente." : "Movimiento desactivado correctamente.";
    }

    // ---------------------------------------------------------------- auxiliares

    private static ResponseStatusException stockInsuficiente(int actual, int solicitado) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente. Stock actual: " + actual
                + ". No se puede registrar una salida de " + solicitado + " unidades.");
    }

    private static BigDecimal validarCosto(String tipo, BigDecimal costo) {
        if (!"Compra".equals(tipo)) {
            return null; // el costo solo aplica a compras
        }
        if (costo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo unitario es obligatorio para compras");
        }
        if (costo.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo unitario debe ser mayor a 0");
        }
        if (costo.stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo unitario debe tener máximo 2 decimales");
        }
        return costo.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    private static String validarMotivo(String tipo, String motivo) {
        if ("Venta".equals(tipo)) {
            return null; // el campo no se muestra para ventas
        }
        String limpio = motivo == null ? "" : motivo.trim();
        if (MOTIVO_OBLIGATORIO.contains(tipo)) {
            if (limpio.length() < 10 || limpio.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo debe contener entre 10 y 500 caracteres.");
            }
        } else if (limpio.length() > 500) { // Compra: opcional
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo debe contener entre 10 y 500 caracteres.");
        }
        return limpio.isEmpty() ? null : limpio;
    }

    /** Sucursal del usuario si es personal de farmacia con sede asignada; null para el Administrador. */
    private Integer sucursalFija(Integer idUsuarioSesion) {
        Usuario u = usuarioRepository.findById(idUsuarioSesion).orElse(null);
        if (u == null || u.getRol() == null || !Rol.FARMACEUTICO.equals(u.getRol().getNombreRol())) {
            return null;
        }
        return u.getSucursal() == null ? null : u.getSucursal().getIdSucursal();
    }

    private void validarSucursalPermitida(Integer idSucursal, Integer idUsuarioSesion) {
        Integer fija = sucursalFija(idUsuarioSesion);
        if (fija != null && !fija.equals(idSucursal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede operar sobre el inventario de su sucursal.");
        }
    }

    private static Specification<MovimientoInventario> filtros(Integer idTipo, Integer idMedicamento, Integer idSucursal,
                                                               String referencia, String usuario, LocalDate desde, LocalDate hasta) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (idTipo != null) p.add(cb.equal(root.get("tipoMovimiento").get("idTipoMovimiento"), idTipo));
            if (idMedicamento != null) p.add(cb.equal(root.get("medicamento").get("idMedicamento"), idMedicamento));
            if (idSucursal != null) p.add(cb.equal(root.get("sucursal").get("idSucursal"), idSucursal));
            if (referencia != null && !referencia.isBlank()) {
                p.add(cb.like(cb.lower(root.get("numeroReferencia")), "%" + referencia.trim().toLowerCase() + "%"));
            }
            if (usuario != null && !usuario.isBlank()) {
                Join<MovimientoInventario, Usuario> u = root.join("usuario");
                String patron = "%" + usuario.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(u.get("nombreCompleto")), patron), cb.like(cb.lower(u.get("nombreUsuario")), patron)));
            }
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), desde.atStartOfDay()));
            if (hasta != null) p.add(cb.lessThan(root.get("fechaHora"), hasta.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static MovimientoInventarioDTO convertir(MovimientoInventario m) {
        String tipo = m.getTipoMovimiento().getNombre();
        return new MovimientoInventarioDTO(
                m.getIdMovimiento(),
                m.getTipoMovimiento().getIdTipoMovimiento(),
                tipo,
                ENTRADAS.contains(tipo),
                m.getMedicamento().getIdMedicamento(),
                m.getMedicamento().getNombre(),
                m.getSucursal().getIdSucursal(),
                m.getSucursal().getNombre(),
                m.getCantidad(),
                m.getStockAnterior(),
                m.getStockNuevo(),
                m.getCostoUnitario(),
                m.getNumeroReferencia(),
                m.getMotivoNotas(),
                m.getUsuario().getNombreCompleto(),
                m.getFechaHora(),
                Boolean.TRUE.equals(m.getEstado()));
    }
}
