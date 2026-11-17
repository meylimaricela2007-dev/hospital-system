package com.sistema.hospital.service;

import com.sistema.hospital.dto.ComprobantePagoResponse;
import com.sistema.hospital.dto.PagoTarjetaRequest;
import com.sistema.hospital.model.*;
import com.sistema.hospital.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class PagoService {

    @Autowired
    private CitaRepository citaRepository;
    @Autowired
    private HorarioDisponibleRepository horarioDisponibleRepository;
    @Autowired
    private MetodoPagoRepository metodoPagoRepository;
    @Autowired
    private EstadoPagoRepository estadoPagoRepository;
    @Autowired
    private EstadoCitaRepository estadoCitaRepository;
    @Autowired
    private PagoCitaRepository pagoCitaRepository;
    @Autowired
    private NotificacionService notificacionService;

    /**
     * QA: noRollbackFor. Este método avisa de un rechazo del banco (402) o de una reserva
     * vencida (410) lanzando ResponseStatusException, que es una RuntimeException. Con el
     * @Transactional normal, Spring deshacía TODO lo hecho antes de la excepción: el intento
     * de pago rechazado nunca quedaba guardado en pagos_citas y la cita vencida nunca pasaba
     * a "Expirada". CajaLaboratorioService ya lo hacía bien; aquí faltaba.
     * Todas las validaciones que lanzan excepción ocurren ANTES de escribir algo, así que
     * no hay riesgo de dejar datos a medias.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ComprobantePagoResponse pagar(Integer idCita, PagoTarjetaRequest request, Integer idPacienteSesion) {
        // QA: bloqueo de la fila para que un doble clic / dos pestañas no generen dos pagos.
        Cita cita = citaRepository.buscarParaActualizar(idCita)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cita no existe."));

        // Nadie puede pagar la cita de otro paciente con solo cambiar el id en la URL.
        if (!cita.getPaciente().getIdUsuario().equals(idPacienteSesion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta cita no pertenece a su cuenta.");
        }

        if (!EstadoCita.PENDIENTE_DE_PAGO.equals(cita.getEstadoCita().getNombreEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cita ya no está pendiente de pago.");
        }

        // ¿Se venció el temporizador de reserva (5 minutos) mientras el paciente llenaba la tarjeta?
        if (cita.getFechaExpiracionReserva() != null && cita.getFechaExpiracionReserva().isBefore(LocalDateTime.now())) {
            expirarReserva(cita);
            // Mensaje de CU-03 FA03 / CU-04 FA02
            throw new ResponseStatusException(HttpStatus.GONE,
                    "El tiempo para confirmar su cita ha expirado. El horario seleccionado ha sido liberado. Por favor, seleccione un nuevo horario.");
        }

        validarFechaVencimiento(request.getFechaVencimiento()); // RN-CU04-03
        validarLuhn(request.getNumeroTarjeta());

        // "Banco" simulado: para poder probar los distintos mensajes de error sin
        // depender de una pasarela de pago real, usamos los últimos dígitos de la
        // tarjeta como disparadores de prueba (ver pago.html para las instrucciones).
        String resultadoSimulado = simularRespuestaBanco(request.getNumeroTarjeta());

        PagoCita pago = new PagoCita();
        pago.setCita(cita);
        pago.setMetodoPago(buscarMetodoPago(request.getTipoTarjeta()));
        pago.setMonto(cita.getMontoTotal());
        pago.setTarjetaUltimos4(request.getNumeroTarjeta().substring(request.getNumeroTarjeta().length() - 4));
        pago.setNombreTitular(request.getNombreTitular());
        pago.setNumeroTransaccion("TRX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        if (!"APROBADO".equals(resultadoSimulado)) {
            pago.setEstadoPago(buscarEstadoPago(EstadoPago.RECHAZADO));
            pago.setMensajeRespuesta(mensajeParaResultado(resultadoSimulado));
            pagoCitaRepository.save(pago);
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, mensajeParaResultado(resultadoSimulado));
        }

        pago.setEstadoPago(buscarEstadoPago(EstadoPago.APROBADO));
        pago.setMensajeRespuesta("Transacción aprobada.");
        pagoCitaRepository.save(pago);

        // Pago aprobado: la cita queda Confirmada y el horario, Ocupado en firme.
        cita.setEstadoCita(buscarEstadoCita(EstadoCita.CONFIRMADA));
        cita.setFechaExpiracionReserva(null);
        citaRepository.save(cita);

        if (cita.getHorario() != null) {
            cita.getHorario().setEstado(HorarioDisponible.OCUPADO);
            cita.getHorario().setReservadoHasta(null);
            horarioDisponibleRepository.save(cita.getHorario());
        }

        String detalleServicio = "Consulta de " + cita.getEspecialidad().getNombre() + " con " + cita.getMedico().getNombreCompleto()
                + " el " + cita.getFechaCita() + " a las " + cita.getHoraCita();
        notificacionService.enviarConfirmacionPago(
                cita.getPaciente(), detalleServicio, pago.getNumeroTransaccion(), pago.getMonto(), cita.getSucursal().getNombre());

        return new ComprobantePagoResponse(
                true,
                pago.getNumeroTransaccion(),
                cita.getPaciente().getNombreCompleto(),
                pago.getMonto(),
                request.getTipoTarjeta(),
                pago.getFechaPago(),
                detalleServicio,
                cita.getSucursal().getNombre(),
                "¡Pago realizado exitosamente! Número de transacción: " + pago.getNumeroTransaccion() + ". Su cita ha sido confirmada."
        );
    }

    private void expirarReserva(Cita cita) {
        cita.setEstadoCita(buscarEstadoCita(EstadoCita.EXPIRADA));
        citaRepository.save(cita);
        // QA: solo se libera el horario si ninguna otra cita lo está usando. Antes, si esta
        // cita vencida se "expiraba" DESPUÉS de que otro paciente ya hubiera tomado el mismo
        // horario, se le liberaba el horario al otro paciente (doble reserva posible).
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

    /** RN-CU04-03: formato MM/AA y tarjeta no vencida (vale hasta el último día del mes indicado). */
    private void validarFechaVencimiento(String mmAa) {
        final String mensaje = "La fecha de vencimiento debe estar en formato MM/AA y la tarjeta no debe estar vencida.";
        if (mmAa == null || !mmAa.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
        int mes = Integer.parseInt(mmAa.substring(0, 2));
        int anio = 2000 + Integer.parseInt(mmAa.substring(3, 5));
        if (YearMonth.of(anio, mes).atEndOfMonth().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
    }

    /** Algoritmo de Luhn: valida que el número de tarjeta sea matemáticamente consistente. */
    private void validarLuhn(String numero) {
        int suma = 0;
        boolean alternar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int digito = numero.charAt(i) - '0';
            if (alternar) {
                digito *= 2;
                if (digito > 9) digito -= 9;
            }
            suma += digito;
            alternar = !alternar;
        }
        if (suma % 10 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de tarjeta debe contener entre 13 y 19 dígitos y ser válido.");
        }
    }

    /**
     * Simulación de la pasarela de pago (no hay banco real conectado en esta etapa).
     * Los últimos 4 dígitos de la tarjeta deciden el resultado, para que puedas
     * probar cada mensaje de error a propósito.
     */
    private String simularRespuestaBanco(String numeroTarjeta) {
        String ultimos4 = numeroTarjeta.substring(numeroTarjeta.length() - 4);
        return switch (ultimos4) {
            case "0002" -> "FONDOS_INSUFICIENTES";
            case "0003" -> "TARJETA_INVALIDA";
            case "0004" -> "ERROR_COMUNICACION";
            case "0005" -> "ERROR_PROCESAMIENTO";
            default -> "APROBADO";
        };
    }

    private String mensajeParaResultado(String resultado) {
        return switch (resultado) {
            // Textos exactos de CU-04, FA03
            case "FONDOS_INSUFICIENTES", "TARJETA_INVALIDA" ->
                    "La transacción con tarjeta fue rechazada por el banco. Por favor, verifique los datos de su tarjeta o intente con una tarjeta diferente.";
            case "ERROR_COMUNICACION" ->
                    "Error de comunicación con la pasarela de pago. Intente nuevamente en unos minutos.";
            default -> "El pago no pudo ser procesado. Por favor, intente nuevamente o utilice otra tarjeta.";
        };
    }

    private MetodoPago buscarMetodoPago(String nombre) {
        return metodoPagoRepository.findByNombre(nombre)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de tarjeta no válido."));
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
