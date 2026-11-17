package com.sistema.hospital.service;

import com.sistema.hospital.model.Cita;
import com.sistema.hospital.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificacionService {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JavaMailSender mailSender;

    // CORREGIDO: usaba @Value("saravia43210@gmail.com") -un valor literal en
    // vez de ${...}-, así que aunque cambiaras app.correo.remitente en
    // application.properties, el código seguía usando siempre ese correo
    // fijo. Ahora sí lee la propiedad real.
    @Value("${app.correo.remitente}")
    private String correoRemitente;

    public void registrarNotificacion(Usuario usuario, String tipo, String asunto, String cuerpo) {
        boolean enviado = enviarCorreoReal(usuario, asunto, cuerpo);
        dejarConstanciaEnBaseDeDatos(usuario, tipo, asunto, cuerpo, enviado ? "Enviado" : "Fallido");
    }

    /** QA: ahora devuelve si el envío funcionó; antes la constancia siempre decía "Enviado" aunque fallara el SMTP. */
    private boolean enviarCorreoReal(Usuario usuario, String asunto, String cuerpo) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(correoRemitente);
            mensaje.setTo(usuario.getCorreoElectronico());
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            mailSender.send(mensaje);
            return true;
        } catch (Exception ex) {
            // RN-GLOBAL-006
            System.err.println("[NotificacionService] Error al enviar notificación por correo electrónico al paciente "
                    + usuario.getNombreCompleto() + " (" + usuario.getCorreoElectronico() + "). Se reintentará automáticamente. Detalle: "
                    + ex.getMessage());
            return false;
        }
    }

    private void dejarConstanciaEnBaseDeDatos(Usuario usuario, String tipo, String asunto, String cuerpo, String estadoEnvio) {
        try {
            entityManager.createNativeQuery("""
                    INSERT INTO his.notificaciones_correo
                        (id_usuario, tipo, asunto, cuerpo, estado_envio, fecha_envio)
                    VALUES (:idUsuario, :tipo, :asunto, :cuerpo, :estadoEnvio, :fecha)
                    """)
                    .setParameter("idUsuario", usuario.getIdUsuario())
                    .setParameter("tipo", tipo)
                    .setParameter("asunto", asunto)
                    .setParameter("cuerpo", cuerpo)
                    .setParameter("estadoEnvio", estadoEnvio)
                    .setParameter("fecha", LocalDateTime.now())
                    .executeUpdate();
        } catch (Exception ex) {
            System.err.println("[NotificacionService] No se pudo registrar la notificación: " + ex.getMessage());
        }
    }

    /**
     * Correo de bienvenida
     */
    public void enviarBienvenida(Usuario usuario) {
        String nombre = usuario.getNombreCompleto();

        String asunto = "Bienvenido al Sistema de Citas - Hospital HIS";
        String cuerpo = "Estimado(a) " + nombre + ", su registro ha sido completado exitosamente. "
                + "Ya puede agendar sus citas médicas a través de nuestro portal.";

        registrarNotificacion(usuario, "Bienvenida", asunto, cuerpo);
    }

    /**
     * CU-04, postcondición: "Se envía notificación de confirmación al
     * correo del paciente". Antes PagoService nunca llamaba a este
     * servicio, así que después de pagar la cita el paciente no recibía
     * absolutamente ningún correo (ni de éxito ni de error).
     */
    public void enviarConfirmacionPago(Usuario paciente, String detalleServicio, String numeroTransaccion,
                                        java.math.BigDecimal monto, String sucursal) {
        String asunto = "Pago confirmado - Cita médica Hospital HIS";
        String cuerpo = "Estimado(a) " + paciente.getNombreCompleto() + ", su pago ha sido procesado exitosamente.\n\n"
                + "Número de transacción: " + numeroTransaccion + "\n"
                + "Monto pagado: Q" + monto + "\n"
                + "Detalle: " + detalleServicio + "\n"
                + "Sucursal: " + sucursal + "\n\n"
                + "Su cita ha quedado CONFIRMADA. Preséntese unos minutos antes de su hora de cita.";

        registrarNotificacion(paciente, "Confirmación de pago", asunto, cuerpo);
    }

    // ------------------------------------------------------------------
    // CU-12: citas de seguimiento
    // ------------------------------------------------------------------
    public static final String TIPO_SEGUIMIENTO = "Cita de seguimiento";
    public static final String TIPO_RECORDATORIO = "Recordatorio de seguimiento";

    /** CU-12, RN-CU11-04: correo al paciente cuando el médico le agenda un seguimiento. */
    public void enviarCitaSeguimiento(Cita cita) {
        String asunto = "Cita de seguimiento agendada - Hospital HIS (Cita #" + cita.getIdCita() + ")";
        String cuerpo = "Estimado(a) " + cita.getPaciente().getNombreCompleto() + ", su médico le ha agendado una cita de seguimiento.\n\n"
                + detalleCita(cita)
                + "\nRecibirá un recordatorio antes de su cita. Recuerde realizar el pago de su consulta para confirmarla.";
        registrarNotificacion(cita.getPaciente(), TIPO_SEGUIMIENTO, asunto, cuerpo);
    }

    /** CU-12, RN-CU11-05: recordatorio automático previo a la cita de seguimiento. */
    public void enviarRecordatorioSeguimiento(Cita cita) {
        String asunto = "Recordatorio de su cita de seguimiento - Hospital HIS (Cita #" + cita.getIdCita() + ")";
        String cuerpo = "Estimado(a) " + cita.getPaciente().getNombreCompleto() + ", le recordamos su próxima cita de seguimiento.\n\n"
                + detalleCita(cita)
                + "\nPreséntese unos minutos antes de su hora de cita.";
        registrarNotificacion(cita.getPaciente(), TIPO_RECORDATORIO, asunto, cuerpo);
    }

    /** Evita enviar dos veces el mismo recordatorio: busca una notificación previa de esa cita. */
    public boolean yaSeEnvioRecordatorio(Integer idCita) {
        Number total = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM his.notificaciones_correo WHERE tipo = :tipo AND asunto LIKE :patron")
                .setParameter("tipo", TIPO_RECORDATORIO)
                .setParameter("patron", "%(Cita #" + idCita + ")%")
                .getSingleResult();
        return total.longValue() > 0;
    }

    private String detalleCita(Cita cita) {
        String tipo = cita.getTipoSeguimiento() != null ? cita.getTipoSeguimiento().getNombre() : "Seguimiento";
        String prioridad = cita.getPrioridad() != null ? cita.getPrioridad().getNombre() : "Normal";
        return "Tipo de seguimiento: " + tipo + "\n"
                + "Médico: " + cita.getMedico().getNombreCompleto() + "\n"
                + "Especialidad: " + cita.getEspecialidad().getNombre() + "\n"
                + "Sede: " + cita.getSucursal().getNombre() + "\n"
                + "Fecha: " + cita.getFechaCita() + "\n"
                + "Hora: " + cita.getHoraCita() + "\n"
                + "Prioridad: " + prioridad + "\n"
                + "Motivo: " + cita.getMotivoConsulta() + "\n";
    }
}