package com.sistema.hospital.service;

import com.sistema.hospital.dto.*;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CU-10: Cobro de Laboratorio en Caja.
 * El Cajero cobra una orden de laboratorio "Pendiente". Al cobrarla, la
 * orden pasa a "En Proceso" y el laboratorio ya puede tomar las muestras
 * (CU-09). Sigue exactamente el mismo esquema que CajaService (CU-06):
 * Efectivo con cálculo de cambio, o tarjeta con los últimos 4 dígitos.
 */
@Service
public class CajaLaboratorioService {

    private static final String MENSAJE_SIN_RESULTADOS =
            "No se encontraron órdenes de laboratorio pendientes de pago. Verifique el DPI o número de orden e intente de nuevo.";

    @Autowired
    private OrdenLaboratorioRepository ordenLaboratorioRepository;
    @Autowired
    private EstadoOrdenLaboratorioRepository estadoOrdenLaboratorioRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MetodoPagoRepository metodoPagoRepository;
    @Autowired
    private EstadoPagoRepository estadoPagoRepository;
    @Autowired
    private PagoLaboratorioRepository pagoLaboratorioRepository;

    // =================================================================
    // Pasos 2-3 / FA01: buscar órdenes pendientes de pago
    // =================================================================

    @Transactional(readOnly = true)
    public BusquedaCajaLaboratorioResponse buscarPorNumeroOrden(String numeroOrden) {
        return ordenLaboratorioRepository
                .findByNumeroOrdenIgnoreCaseAndEstadoOrden_NombreEstadoAndEsExternaFalse(
                        numeroOrden, EstadoOrdenLaboratorio.PENDIENTE)
                .map(orden -> encontradas(List.of(orden)))
                .orElseGet(this::sinResultados);
    }

    @Transactional(readOnly = true)
    public BusquedaCajaLaboratorioResponse buscarPorDpi(String dpi) {
        List<OrdenLaboratorio> ordenes = ordenLaboratorioRepository
                .findByPaciente_DpiAndEstadoOrden_NombreEstadoAndEsExternaFalseOrderByFechaCreacionAsc(
                        dpi, EstadoOrdenLaboratorio.PENDIENTE);
        return ordenes.isEmpty() ? sinResultados() : encontradas(ordenes);
    }

    private BusquedaCajaLaboratorioResponse encontradas(List<OrdenLaboratorio> ordenes) {
        List<OrdenLaboratorioListaDTO> dtos = ordenes.stream().map(LaboratorioService::convertirALista).toList();
        return new BusquedaCajaLaboratorioResponse("ORDENES_ENCONTRADAS", null, dtos);
    }

    private BusquedaCajaLaboratorioResponse sinResultados() {
        return new BusquedaCajaLaboratorioResponse("SIN_RESULTADOS", MENSAJE_SIN_RESULTADOS, List.of());
    }

    // =================================================================
    // Pasos 6-10 / FA03 / FA04: registrar el pago
    // =================================================================

    /**
     * noRollbackFor: si la tarjeta es rechazada (FA04) se lanza una excepción
     * para avisar al Cajero, pero el intento rechazado SÍ debe quedar
     * guardado en pagos_laboratorio como evidencia. Sin esto, la excepción
     * desharía también ese INSERT.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ComprobanteCajaResponse registrarPago(Integer idOrden, RegistrarPagoCajaRequest request, Integer idUsuarioCajero) {
        OrdenLaboratorio orden = ordenLaboratorioRepository.findById(idOrden)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La orden de laboratorio no existe."));

        if (Boolean.TRUE.equals(orden.getEsExterna())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta es una orden externa: no se cobra en la caja del hospital.");
        }
        if (!EstadoOrdenLaboratorio.PENDIENTE.equals(orden.getEstadoOrden().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta orden ya no está pendiente de pago.");
        }

        Usuario cajero = usuarioRepository.findById(idUsuarioCajero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar."));

        PagoLaboratorio pago = new PagoLaboratorio();
        pago.setOrden(orden);
        pago.setCajero(cajero);
        pago.setMetodoPago(buscarMetodoPago(request.getMetodoPago()));
        pago.setMonto(orden.getMontoTotal());
        pago.setNumeroTransaccion("LABC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        BigDecimal montoRecibido = null;
        BigDecimal cambio = null;

        if ("Efectivo".equalsIgnoreCase(request.getMetodoPago())) {
            // Paso 7: monto recibido y cambio a devolver
            montoRecibido = validarEfectivo(request, orden);
            cambio = montoRecibido.subtract(orden.getMontoTotal());
            pago.setMontoRecibido(montoRecibido);
            pago.setCambioDevuelto(cambio);
            pago.setEstadoPago(buscarEstadoPago(EstadoPago.APROBADO));
        } else {
            // FA03: tarjeta (Visa / Mastercard / Débito)
            procesarTarjeta(request, pago); // FA04: puede lanzar 402
        }

        pagoLaboratorioRepository.save(pago);

        // Paso 9: la orden pasa a "En Proceso" -> ya se pueden tomar muestras (CU-09)
        orden.setEstadoOrden(buscarEstadoOrden(EstadoOrdenLaboratorio.EN_PROCESO));
        ordenLaboratorioRepository.save(orden);

        String examenes = orden.getDetalles().stream()
                .map(d -> d.getExamen().getNombre())
                .collect(Collectors.joining(", "));

        String mensaje = "¡Pago de laboratorio registrado exitosamente! Paciente: "
                + orden.getPaciente().getNombreCompleto()
                + ". La orden ha sido actualizada a estado 'En proceso'.";

        // Paso 10: comprobante (PaymentReceipt) con sucursal "Laboratorio" [RN-GLOBAL-005]
        return new ComprobanteCajaResponse(
                true,
                pago.getNumeroTransaccion(),
                orden.getPaciente().getNombreCompleto(),
                pago.getMonto(),
                request.getMetodoPago(),
                pago.getFechaPago(),
                "Orden de laboratorio " + orden.getNumeroOrden() + " (" + orden.getDetalles().size()
                        + " examen(es)): " + examenes,
                "Laboratorio",
                mensaje,
                montoRecibido,
                cambio
        );
    }

    private BigDecimal validarEfectivo(RegistrarPagoCajaRequest request, OrdenLaboratorio orden) {
        if (request.getMontoRecibido() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe ingresar el monto recibido.");
        }
        BigDecimal montoRecibido = request.getMontoRecibido();
        if (montoRecibido.compareTo(orden.getMontoTotal()) < 0) {
            // Mensaje EXACTO del documento (paso 7 del flujo normal)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "El monto recibido (Q%.2f) es menor al monto a cobrar (Q%.2f).",
                    montoRecibido, orden.getMontoTotal()));
        }
        return montoRecibido;
    }

    /**
     * FA03 / FA04. Igual que en CajaService (CU-06), la respuesta del banco
     * se simula con los últimos 4 dígitos: 0002, 0003 y 0004 = rechazo.
     */
    private void procesarTarjeta(RegistrarPagoCajaRequest request, PagoLaboratorio pago) {
        String ultimos4 = request.getTarjetaUltimos4();
        if (ultimos4 == null || !ultimos4.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingrese los últimos 4 dígitos de la tarjeta.");
        }
        pago.setTarjetaUltimos4(ultimos4);

        boolean rechazada = ultimos4.equals("0002") || ultimos4.equals("0003") || ultimos4.equals("0004");
        if (rechazada) {
            pago.setEstadoPago(buscarEstadoPago(EstadoPago.RECHAZADO));
            pagoLaboratorioRepository.save(pago); // se guarda el intento rechazado (ver noRollbackFor)
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "La transacción con tarjeta fue rechazada por el banco. Solicite al paciente otro método de pago.");
        }
        pago.setEstadoPago(buscarEstadoPago(EstadoPago.APROBADO));
    }

    private MetodoPago buscarMetodoPago(String nombre) {
        return metodoPagoRepository.findByNombre(nombre)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El método de pago seleccionado no está disponible. Los métodos aceptados son: efectivo (Quetzales), tarjeta de crédito (Visa/Mastercard) o tarjeta de débito."));
    }

    private EstadoPago buscarEstadoPago(String nombre) {
        return estadoPagoRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de pago '" + nombre + "' no existe en el catálogo."));
    }

    private EstadoOrdenLaboratorio buscarEstadoOrden(String nombre) {
        return estadoOrdenLaboratorioRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de orden '" + nombre + "' no existe en el catálogo."));
    }
}
