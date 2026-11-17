package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CU-11: Despacho de medicamentos (Farmacia).
 *
 * Reglas clave:
 *  - [RN-CU10-01] La receta solo es válida para despacho hasta 7 días después
 *    de su emisión (se cuenta por días calendario: una receta emitida hace
 *    7 días todavía es válida; a partir de 8 está vencida).
 *  - [RN-CU10-03] Se alerta cuando el stock llega o está por debajo del mínimo.
 *  - Al confirmar: se crea el despacho + su detalle, se descuenta el
 *    inventario de la sucursal DEL FARMACÉUTICO y se registra un movimiento
 *    de tipo "Despacho" por cada medicamento entregado.
 *  - El cobro físico queda fuera del sistema: solo se guarda el monto.
 */
@Service
public class FarmaciaService {

    private static final int DIAS_VIGENCIA = 7;
    private static final int MAX_RESULTADOS = 50;

    @Autowired
    private RecetaMedicaRepository recetaMedicaRepository;
    @Autowired
    private InventarioMedicamentoRepository inventarioRepository;
    @Autowired
    private DespachoMedicamentoRepository despachoRepository;
    @Autowired
    private MovimientoInventarioRepository movimientoRepository;
    @Autowired
    private TipoMovimientoInventarioRepository tipoMovimientoRepository;
    @Autowired
    private MedicamentoRepository medicamentoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private NotificacionService notificacionService;

    // =================================================================
    // Panel: despachos recientes de la sucursal del farmacéutico
    // =================================================================
    @Transactional(readOnly = true)
    public List<DespachoRecienteDTO> despachosRecientes(Integer idUsuario) {
        Usuario farmaceutico = obtenerFarmaceutico(idUsuario);
        return despachoRepository
                .findTop10BySucursal_IdSucursalOrderByFechaDespachoDesc(farmaceutico.getSucursal().getIdSucursal())
                .stream()
                .map(d -> new DespachoRecienteDTO(
                        d.getIdDespacho(),
                        d.getReceta().getIdReceta(),
                        nombrePaciente(d.getReceta()),
                        d.getMontoTotal(),
                        d.getEstado(),
                        d.getFechaDespacho()))
                .toList();
    }

    // =================================================================
    // Paso 2: búsqueda de recetas activas por ID de Receta / ID de Consulta
    // =================================================================
    @Transactional(readOnly = true)
    public List<RecetaFarmaciaDTO> buscarRecetas(Integer idReceta, Integer idConsulta) {
        Pageable limite = PageRequest.of(0, MAX_RESULTADOS);
        List<RecetaMedica> recetas;
        if (idReceta != null && idConsulta != null) {
            recetas = recetaMedicaRepository.findByActivaTrueAndIdRecetaAndConsulta_IdConsultaOrderByFechaEmisionDesc(idReceta, idConsulta, limite);
        } else if (idReceta != null) {
            recetas = recetaMedicaRepository.findByActivaTrueAndIdRecetaOrderByFechaEmisionDesc(idReceta, limite);
        } else if (idConsulta != null) {
            recetas = recetaMedicaRepository.findByActivaTrueAndConsulta_IdConsultaOrderByFechaEmisionDesc(idConsulta, limite);
        } else {
            recetas = recetaMedicaRepository.findByActivaTrueOrderByFechaEmisionDesc(limite);
        }

        return recetas.stream().map(r -> {
            long dias = diasTranscurridos(r);
            return new RecetaFarmaciaDTO(r.getIdReceta(), r.getConsulta().getIdConsulta(), nombrePaciente(r),
                    r.getFechaEmision(), dias, dias <= DIAS_VIGENCIA, r.getNotas());
        }).toList();
    }

    // =================================================================
    // Pasos 4-5: validar vigencia y mostrar medicamentos + disponibilidad
    // =================================================================
    @Transactional(readOnly = true)
    public DespachoPreviewDTO obtenerDetalle(Integer idReceta, Integer idUsuario) {
        Usuario farmaceutico = obtenerFarmaceutico(idUsuario);
        RecetaMedica receta = obtenerRecetaDespachable(idReceta);
        Integer idSucursal = farmaceutico.getSucursal().getIdSucursal();

        List<ItemDespachoDTO> items = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        for (DetalleReceta d : ordenar(receta)) {
            Medicamento m = d.getMedicamento();
            InventarioMedicamento inv = inventarioRepository
                    .findByMedicamento_IdMedicamentoAndSucursal_IdSucursalAndEstadoTrue(m.getIdMedicamento(), idSucursal)
                    .orElse(null);
            Integer minimo = m.getStockMinimo() == null ? 0 : m.getStockMinimo();
            boolean sinInventario = inv == null;
            Integer stock = sinInventario ? null : inv.getStockActual();
            boolean bajo = !sinInventario && stock <= minimo;
            if (bajo) {
                alertas.add(m.getNombre() + ": Stock bajo — disponible: " + stock + " (mínimo: " + minimo + ").");
            }
            items.add(new ItemDespachoDTO(d.getIdDetalleReceta(), m.getIdMedicamento(), m.getNombre(), m.getUnidad(),
                    d.getDosis(), d.getCantidad(), d.getPrecioUnitario(), stock, minimo,
                    Boolean.TRUE.equals(m.getEsControlado()), sinInventario, bajo));
        }

        return new DespachoPreviewDTO(receta.getIdReceta(), receta.getConsulta().getIdConsulta(), nombrePaciente(receta),
                receta.getFechaEmision(), diasTranscurridos(receta), receta.getNotas(),
                farmaceutico.getSucursal().getNombre(), items, alertas);
    }

    // =================================================================
    // Pasos 6-13: confirmar el despacho
    // =================================================================
    @Transactional
    public DespachoResponse despachar(Integer idReceta, DespacharRequest request, Integer idUsuario) {
        Usuario farmaceutico = obtenerFarmaceutico(idUsuario);
        // QA: la receta se lee bloqueada (SELECT ... FOR UPDATE): si dos farmacéuticos la
        // despachaban a la vez, los dos pasaban la validación "receta activa" y se
        // descontaba el inventario dos veces.
        recetaMedicaRepository.buscarParaDespachar(idReceta);
        RecetaMedica receta = obtenerRecetaDespachable(idReceta);
        Sucursal sucursal = farmaceutico.getSucursal();

        // QA: el mismo medicamento recetado no puede venir dos veces en el despacho. Antes,
        // repetir el renglón permitía entregar el doble (o más) de lo recetado, porque cada
        // renglón se validaba por separado contra la cantidad recetada.
        java.util.Set<Integer> yaVistos = new java.util.HashSet<>();
        for (DespacharRequest.Item item : request.getItems()) {
            if (!yaVistos.add(item.getIdDetalleReceta())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un medicamento de la receta aparece repetido en el despacho. Cada renglón debe aparecer una sola vez.");
            }
        }

        Map<Integer, DetalleReceta> detallesPorId = receta.getDetalles().stream()
                .collect(Collectors.toMap(DetalleReceta::getIdDetalleReceta, Function.identity()));
        TipoMovimientoInventario tipoDespacho = tipoMovimientoRepository.findByNombre(TipoMovimientoInventario.DESPACHO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No existe el tipo de movimiento 'Despacho' en el catálogo."));

        // 1) Validar y preparar las líneas (sin tocar la BD todavía)
        List<LineaPreparada> lineas = new ArrayList<>();
        for (DespacharRequest.Item item : request.getItems()) {
            DetalleReceta detalle = detallesPorId.get(item.getIdDetalleReceta());
            if (detalle == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un medicamento no pertenece a la receta #" + idReceta + ".");
            }
            int cantidad = item.getCantidad();
            if (cantidad == 0) continue; // el personal puede dejar un renglón sin despachar
            if (cantidad > detalle.getCantidad()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de " + detalle.getMedicamento().getNombre()
                        + " no puede exceder lo recetado (" + detalle.getCantidad() + ").");
            }

            Medicamento aEntregar = detalle.getMedicamento();
            BigDecimal precio = detalle.getPrecioUnitario();
            boolean sustituido = Boolean.TRUE.equals(item.getSustituido());
            String razon = null;
            if (sustituido) {
                razon = item.getRazonSustitucion() == null ? "" : item.getRazonSustitucion().trim();
                if (razon.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La razón de sustitución de " + detalle.getMedicamento().getNombre() + " es obligatoria.");
                }
                if (item.getIdMedicamentoAlternativo() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Seleccione el medicamento alternativo para " + detalle.getMedicamento().getNombre() + ".");
                }
                if (item.getIdMedicamentoAlternativo().equals(detalle.getMedicamento().getIdMedicamento())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El medicamento alternativo debe ser distinto al recetado.");
                }
                aEntregar = medicamentoRepository.findById(item.getIdMedicamentoAlternativo())
                        .filter(m -> Boolean.TRUE.equals(m.getEstado()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El medicamento alternativo no existe."));
                precio = aEntregar.getPrecio();
            }
            lineas.add(new LineaPreparada(detalle, aEntregar, cantidad, precio, sustituido, razon));
        }
        if (lineas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe despachar al menos un medicamento.");
        }

        // 2) Crear el despacho
        BigDecimal total = lineas.stream()
                .map(l -> l.precio.multiply(BigDecimal.valueOf(l.cantidad)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        DespachoMedicamento despacho = new DespachoMedicamento();
        despacho.setReceta(receta);
        despacho.setFarmaceutico(farmaceutico);
        despacho.setSucursal(sucursal);
        despacho.setMontoTotal(total);
        despacho.setEstado(DespachoMedicamento.COMPLETADO);
        despacho = despachoRepository.saveAndFlush(despacho);

        // 3) Detalle + inventario + movimiento por cada línea
        List<DespachoResponse.Linea> respuestaLineas = new ArrayList<>();
        List<String> sustituciones = new ArrayList<>();
        List<String> alertasMinimo = new ArrayList<>();
        List<String> controlados = new ArrayList<>();

        for (LineaPreparada l : lineas) {
            InventarioMedicamento inv = inventarioRepository
                    .findByMedicamento_IdMedicamentoAndSucursal_IdSucursalAndEstadoTrue(l.aEntregar.getIdMedicamento(), sucursal.getIdSucursal())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No hay inventario registrado de " + l.aEntregar.getNombre() + " en esta sucursal."));

            if (inventarioRepository.descontarStock(inv.getIdInventario(), l.cantidad) == 0) {
                Integer disponible = inventarioRepository.obtenerStock(inv.getIdInventario());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente de " + l.aEntregar.getNombre()
                        + ". Disponible: " + disponible + ", solicitado: " + l.cantidad + ".");
            }
            int stockNuevo = inventarioRepository.obtenerStock(inv.getIdInventario());

            DetalleDespacho dd = new DetalleDespacho();
            dd.setDespacho(despacho);
            dd.setDetalleReceta(l.detalle);
            dd.setCantidadDespachada(l.cantidad);
            dd.setPrecioUnitario(l.precio);
            despacho.getDetalles().add(dd);

            MovimientoInventario mov = new MovimientoInventario();
            mov.setMedicamento(l.aEntregar);
            mov.setSucursal(sucursal);
            mov.setTipoMovimiento(tipoDespacho);
            mov.setCantidad(l.cantidad);
            mov.setStockAnterior(stockNuevo + l.cantidad);
            mov.setStockNuevo(stockNuevo);
            mov.setNumeroReferencia("DESP-" + despacho.getIdDespacho());
            mov.setMotivoNotas("Despacho de la receta #" + receta.getIdReceta());
            mov.setUsuario(farmaceutico);
            mov.setEstado(true);
            movimientoRepository.save(mov);

            String sustituyeA = null;
            if (l.sustituido) {
                l.detalle.setFueSustituido(true);
                l.detalle.setRazonSustitucion(l.razon);
                l.detalle.setMedicamentoAlternativo(l.aEntregar.getNombre());
                sustituyeA = l.detalle.getMedicamento().getNombre();
                // FA02: mensaje EXACTO del documento
                sustituciones.add("Medicamento " + sustituyeA + " sustituido por " + l.aEntregar.getNombre()
                        + ". El médico tratante será notificado de la sustitución.");
                notificarSustitucion(receta, l);
            }

            int minimo = l.aEntregar.getStockMinimo() == null ? 0 : l.aEntregar.getStockMinimo();
            if (stockNuevo <= minimo) {
                // FA04: mensaje EXACTO del documento
                alertasMinimo.add("ALERTA: El medicamento " + l.aEntregar.getNombre() + " ha alcanzado el nivel de stock mínimo ("
                        + stockNuevo + " unidades restantes). Se recomienda generar orden de reabastecimiento.");
            }
            if (Boolean.TRUE.equals(l.aEntregar.getEsControlado())) {
                controlados.add(l.aEntregar.getNombre() + " x" + l.cantidad);
            }

            respuestaLineas.add(new DespachoResponse.Linea(l.aEntregar.getNombre(), l.aEntregar.getUnidad(), l.cantidad,
                    l.precio, l.precio.multiply(BigDecimal.valueOf(l.cantidad)).setScale(2, RoundingMode.HALF_UP), sustituyeA));
        }
        despachoRepository.save(despacho);

        // La receta ya fue atendida: sale de la lista de recetas activas
        receta.setActiva(false);
        recetaMedicaRepository.save(receta);

        // Trazabilidad. [RNF-017] Los medicamentos controlados quedan marcados en la auditoría.
        String detalleAuditoria = "Despacho #" + despacho.getIdDespacho() + " de la receta #" + receta.getIdReceta()
                + " en " + sucursal.getNombre() + ". Total: Q" + total.toPlainString() + ".";
        if (!controlados.isEmpty()) {
            detalleAuditoria += " MEDICAMENTO CONTROLADO despachado: " + String.join(", ", controlados) + ".";
        }
        auditoriaService.registrar(idUsuario, "DESPACHAR_MEDICAMENTOS", "despachos_medicamentos", despacho.getIdDespacho(), detalleAuditoria);

        // Paso 13: mensaje EXACTO del documento
        String mensaje = "Despacho registrado exitosamente. " + respuestaLineas.size() + " medicamento(s) despachado(s). Total: Q"
                + total.toPlainString() + ".";

        return new DespachoResponse(mensaje, despacho.getIdDespacho(), receta.getIdReceta(), nombrePaciente(receta),
                total, respuestaLineas, sustituciones, alertasMinimo);
    }

    // =================================================================
    // FA03: el paciente no desea adquirir los medicamentos
    // =================================================================
    @Transactional
    public String cancelar(Integer idReceta, Integer idUsuario) {
        Usuario farmaceutico = obtenerFarmaceutico(idUsuario);
        RecetaMedica receta = recetaMedicaRepository.findById(idReceta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La receta no existe."));
        if (!Boolean.TRUE.equals(receta.getActiva())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La receta #" + idReceta + " ya no está activa.");
        }

        DespachoMedicamento despacho = new DespachoMedicamento();
        despacho.setReceta(receta);
        despacho.setFarmaceutico(farmaceutico);
        despacho.setSucursal(farmaceutico.getSucursal());
        despacho.setMontoTotal(BigDecimal.ZERO.setScale(2));
        despacho.setEstado(DespachoMedicamento.CANCELADO);
        despachoRepository.save(despacho);

        String paciente = nombrePaciente(receta);
        // Mensaje EXACTO del documento. La receta queda activa: el paciente aún puede volver mientras esté vigente.
        String mensaje = "Se ha registrado que el paciente " + paciente
                + " no adquirió los medicamentos recetados en farmacia interna. Receta: " + idReceta + ".";
        auditoriaService.registrar(idUsuario, "CANCELAR_DESPACHO", "despachos_medicamentos", despacho.getIdDespacho(), mensaje);
        return mensaje;
    }

    // =================================================================
    // Auxiliares
    // =================================================================
    private Usuario obtenerFarmaceutico(Integer idUsuario) {
        Usuario u = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar."));
        if (u.getSucursal() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Su usuario no tiene una sucursal asignada. Pida al administrador que se la asigne.");
        }
        return u;
    }

    /** Receta activa y dentro de su vigencia de 7 días [RN-CU10-01]. */
    private RecetaMedica obtenerRecetaDespachable(Integer idReceta) {
        RecetaMedica receta = recetaMedicaRepository.findById(idReceta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La receta no existe."));
        if (!Boolean.TRUE.equals(receta.getActiva())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La receta #" + idReceta + " ya no está activa.");
        }
        long dias = diasTranscurridos(receta);
        if (dias > DIAS_VIGENCIA) {
            // Paso 4: mensaje EXACTO del documento
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Receta Vencida. La receta #" + idReceta
                    + " fue emitida hace " + dias + " días y ya no es válida para despacho.");
        }
        return receta;
    }

    private long diasTranscurridos(RecetaMedica receta) {
        return ChronoUnit.DAYS.between(receta.getFechaEmision().toLocalDate(), LocalDate.now());
    }

    private String nombrePaciente(RecetaMedica receta) {
        return receta.getConsulta().getCita().getPaciente().getNombreCompleto();
    }

    private List<DetalleReceta> ordenar(RecetaMedica receta) {
        return receta.getDetalles().stream()
                .sorted(Comparator.comparing(DetalleReceta::getIdDetalleReceta))
                .toList();
    }

    private void notificarSustitucion(RecetaMedica receta, LineaPreparada l) {
        Usuario medico = receta.getConsulta().getMedico();
        String asunto = "Sustitución de medicamento - Receta #" + receta.getIdReceta();
        String cuerpo = "Estimado(a) " + medico.getNombreCompleto() + ", en farmacia se sustituyó " + l.detalle.getMedicamento().getNombre()
                + " por " + l.aEntregar.getNombre() + " en la receta #" + receta.getIdReceta()
                + " del paciente " + nombrePaciente(receta) + ".\nRazón: " + l.razon;
        notificacionService.registrarNotificacion(medico, "Sustitución de medicamento", asunto, cuerpo);
    }

    private record LineaPreparada(DetalleReceta detalle, Medicamento aEntregar, int cantidad, BigDecimal precio,
                                  boolean sustituido, String razon) {
    }
}
