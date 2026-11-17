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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CU-06: Cobro de Consulta en Caja.
 * El Empleado Interno (Cajero) cobra presencialmente una cita que quedó
 * "Pendiente de pago" (porque el paciente no pagó en línea, o porque la
 * cita se creó como walk-in desde Recepción). Ver
 * "6_CU_Cobro_de_Consulta_en_Caja.docx" para el detalle de cada flujo
 * alterno (FA01-FA04) referenciado en los comentarios de este archivo.
 */
@Service
public class CajaService {

    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EstadoCitaRepository estadoCitaRepository;
    @Autowired
    private HorarioDisponibleRepository horarioDisponibleRepository;
    @Autowired
    private MetodoPagoRepository metodoPagoRepository;
    @Autowired
    private EstadoPagoRepository estadoPagoRepository;
    @Autowired
    private PagoCitaRepository pagoCitaRepository;

    // =================================================================
    // Paso 1 del flujo normal + FA02: búsqueda de la cita a cobrar
    // =================================================================

    /** [RN-CU06-01] búsqueda por número de cita. */
    @Transactional
    public BusquedaCajaResponse buscarPorNumeroCita(Integer idCita) {
        Cita cita = citaRepository.findById(idCita).orElse(null);
        if (cita == null || !EstadoCita.PENDIENTE_DE_PAGO.equals(cita.getEstadoCita().getNombreEstado())) {
            return sinResultados();
        }
        if (estaExpirada(cita)) {
            expirarReserva(cita); // la reserva de horario ya venció; no se puede cobrar
            return sinResultados();
        }
        return new BusquedaCajaResponse("CITA_ENCONTRADA", null, convertirADTO(cita));
    }

    /** [RN-CU06-01] búsqueda por DPI del paciente. */
    @Transactional
    public BusquedaCajaResponse buscarPorDpi(String dpi) {
        Usuario paciente = usuarioRepository.findByDpi(dpi).orElse(null);
        if (paciente == null || !Rol.PACIENTE.equalsIgnoreCase(paciente.getRol().getNombreRol())) {
            return sinResultados();
        }

        List<Cita> pendientes = citaRepository.findByPaciente_IdUsuarioAndEstadoCita_NombreEstadoOrderByFechaCitaAscHoraCitaAsc(
                paciente.getIdUsuario(), EstadoCita.PENDIENTE_DE_PAGO);

        for (Cita cita : pendientes) {
            if (estaExpirada(cita)) {
                expirarReserva(cita);
                continue;
            }
            return new BusquedaCajaResponse("CITA_ENCONTRADA", null, convertirADTO(cita));
        }
        return sinResultados();
    }

    private BusquedaCajaResponse sinResultados() {
        // FA02: "No se encontraron citas pendientes de pago para el criterio ingresado."
        return new BusquedaCajaResponse("SIN_RESULTADOS",
                "No se encontraron citas pendientes de pago para el criterio ingresado.", null);
    }

    // =================================================================
    // Pasos 4-9 del flujo normal + FA01/FA04: registrar el pago
    // =================================================================

    /**
     * Cobra la cita en Efectivo o con tarjeta (FA01). idUsuarioCajero es
     * quien tiene la sesión iniciada; se guarda en el pago para poder saber
     * después qué Cajero atendió cada cobro (arqueo de caja).
     */
    // QA: noRollbackFor. El rechazo de tarjeta (402, FA04) y la reserva vencida (410) se avisan
    // con una excepción; sin esto Spring deshacía también el INSERT del pago rechazado y el
    // cambio a "Expirada", así que no quedaba evidencia del intento (ver CajaLaboratorioService).
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ComprobanteCajaResponse registrarPago(Integer idCita, RegistrarPagoCajaRequest request, Integer idUsuarioCajero) {
        // QA: bloqueo de fila para que dos cajeros no cobren la misma cita a la vez.
        Cita cita = citaRepository.buscarParaActualizar(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));

        if (!EstadoCita.PENDIENTE_DE_PAGO.equals(cita.getEstadoCita().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cita ya no está pendiente de pago.");
        }
        if (estaExpirada(cita)) {
            expirarReserva(cita);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "El tiempo de reserva de esta cita ha expirado. El horario fue liberado.");
        }

        Usuario cajero = usuarioRepository.findById(idUsuarioCajero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar."));

        PagoCita pago = new PagoCita();
        pago.setCita(cita);
        pago.setCajero(cajero);
        pago.setMetodoPago(buscarMetodoPago(request.getMetodoPago()));
        pago.setMonto(cita.getMontoTotal());
        pago.setNumeroTransaccion("CAJA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        BigDecimal montoRecibido = null;
        BigDecimal cambio = null;

        if ("Efectivo".equalsIgnoreCase(request.getMetodoPago())) {
            // Pasos 5-7 del flujo normal
            montoRecibido = registrarEfectivo(request, cita, pago);
            cambio = montoRecibido.subtract(cita.getMontoTotal());
        } else {
            // FA01: pago con tarjeta (Visa/Mastercard/Débito)
            registrarTarjeta(request, pago); // puede lanzar 402 (FA04) y ahí termina
        }

        pagoCitaRepository.save(pago);
        confirmarCita(cita);

        String mensaje = "¡Pago registrado exitosamente! Paciente: " + cita.getPaciente().getNombreCompleto()
                + ". La cita ha sido actualizada a estado Confirmada.";

        return new ComprobanteCajaResponse(
                true,
                pago.getNumeroTransaccion(),
                cita.getPaciente().getNombreCompleto(),
                pago.getMonto(),
                request.getMetodoPago(),
                pago.getFechaPago(),
                "Consulta de " + cita.getEspecialidad().getNombre() + " con " + cita.getMedico().getNombreCompleto()
                        + " el " + cita.getFechaCita() + " a las " + cita.getHoraCita(),
                cita.getSucursal().getNombre(),
                mensaje,
                montoRecibido,
                cambio
        );
    }

    /** Paso 6 del flujo normal: validar y registrar el monto recibido en efectivo. */
    private BigDecimal registrarEfectivo(RegistrarPagoCajaRequest request, Cita cita, PagoCita pago) {
        if (request.getMontoRecibido() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe ingresar el monto recibido.");
        }
        BigDecimal montoRecibido = request.getMontoRecibido();

        if (montoRecibido.compareTo(cita.getMontoTotal()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "El monto recibido (Q%.2f) es menor al monto a cobrar (Q%.2f).",
                    montoRecibido, cita.getMontoTotal()));
        }

        pago.setEstadoPago(buscarEstadoPago(EstadoPago.APROBADO));
        pago.setMontoRecibido(montoRecibido);
        pago.setCambioDevuelto(montoRecibido.subtract(cita.getMontoTotal()));
        pago.setMensajeRespuesta("Pago en efectivo recibido en caja.");
        return montoRecibido;
    }

    /**
     * FA01, pasos 2-4: tipo de tarjeta + últimos 4 dígitos como referencia.
     * FA04: el documento indica que "el sistema rechaza la transacción con
     * tarjeta", así que -igual que en el pago en línea de CU-04- se simula
     * la respuesta del banco usando los últimos dígitos como disparador de
     * prueba (0002/0003/0004). Esta lógica está duplicada intencionalmente
     * de PagoService (que la usa con el número completo de tarjeta, no solo
     * los últimos 4): si en algún momento se conecta una pasarela real,
     * conviene extraer un único "SimuladorBancoService" del que dependan
     * PagoService y CajaService.
     */
    private void registrarTarjeta(RegistrarPagoCajaRequest request, PagoCita pago) {
        String ultimos4 = request.getTarjetaUltimos4();
        if (ultimos4 == null || !ultimos4.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe ingresar los últimos 4 dígitos de la tarjeta.");
        }
        pago.setTarjetaUltimos4(ultimos4);

        String resultadoSimulado = switch (ultimos4) {
            case "0002" -> "FONDOS_INSUFICIENTES";
            case "0003" -> "TARJETA_INVALIDA";
            case "0004" -> "ERROR_COMUNICACION";
            default -> "APROBADO";
        };

        if (!"APROBADO".equals(resultadoSimulado)) {
            // FA04: "La transacción con tarjeta fue rechazada por el banco.
            // Solicite al paciente otro método de pago."
            pago.setEstadoPago(buscarEstadoPago(EstadoPago.RECHAZADO));
            pago.setMensajeRespuesta("La transacción con tarjeta fue rechazada por el banco.");
            pagoCitaRepository.save(pago);
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "La transacción con tarjeta fue rechazada por el banco. Solicite al paciente otro método de pago.");
        }

        pago.setEstadoPago(buscarEstadoPago(EstadoPago.APROBADO));
        pago.setMensajeRespuesta("Transacción aprobada.");
    }

    /** Paso 8 del flujo normal: la cita queda Confirmada y el horario, Ocupado en firme. */
    private void confirmarCita(Cita cita) {
        cita.setEstadoCita(buscarEstadoCita(EstadoCita.CONFIRMADA));
        cita.setFechaExpiracionReserva(null);
        citaRepository.save(cita);

        if (cita.getHorario() != null) {
            cita.getHorario().setEstado(HorarioDisponible.OCUPADO);
            cita.getHorario().setReservadoHasta(null);
            horarioDisponibleRepository.save(cita.getHorario());
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    /**
     * Una cita "Pendiente de pago" puede tener una fecha límite de reserva
     * (las que agenda el propio paciente en línea, CU-03) o no tenerla (los
     * walk-in que crea personal interno, ver CU-06 FA03 y el comentario en
     * CitaService.crearCita). Solo las primeras pueden vencerse.
     */
    private boolean estaExpirada(Cita cita) {
        return cita.getFechaExpiracionReserva() != null
                && cita.getFechaExpiracionReserva().isBefore(LocalDateTime.now());
    }

    private void expirarReserva(Cita cita) {
        cita.setEstadoCita(buscarEstadoCita(EstadoCita.EXPIRADA));
        citaRepository.save(cita);
        // QA: no liberar el horario si otra cita ya lo está usando (ver PagoService.expirarReserva).
        boolean otraCitaLoUsa = cita.getHorario() != null
                && citaRepository.existsByHorario_IdHorarioAndIdCitaNotAndEstadoCita_NombreEstadoNotIn(
                        cita.getHorario().getIdHorario(), cita.getIdCita(),
                        List.of(EstadoCita.EXPIRADA, EstadoCita.CANCELADA));
        if (cita.getHorario() != null && !otraCitaLoUsa) {
            cita.getHorario().setEstado(HorarioDisponible.DISPONIBLE);
            cita.getHorario().setReservadoHasta(null);
            horarioDisponibleRepository.save(cita.getHorario());
        }
    }

    private CitaCajaDTO convertirADTO(Cita cita) {
        return new CitaCajaDTO(
                cita.getIdCita(),
                cita.getPaciente().getNombreCompleto(),
                cita.getEspecialidad().getNombre(),
                cita.getMedico().getNombreCompleto(),
                cita.getSucursal().getNombre(),
                cita.getFechaCita(),
                cita.getHoraCita(),
                cita.getMontoTotal()
        );
    }

    private MetodoPago buscarMetodoPago(String nombre) {
        return metodoPagoRepository.findByNombre(nombre)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Método de pago no válido."));
    }

    private EstadoPago buscarEstadoPago(String nombre) {
        return estadoPagoRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de pago '" + nombre + "' no existe en el catálogo."));
    }

    private EstadoCita buscarEstadoCita(String nombre) {
        return estadoCitaRepository.findByNombreEstado(nombre)
                .orElseThrow(() -> new IllegalStateException("El estado de cita '" + nombre + "' no existe en el catálogo."));
    }
}
