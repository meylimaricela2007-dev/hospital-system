// ============================================================
// CU-04: Pago en línea (pago/pago.html)
// idCita llega como variable global desde el <script th:inline> del HTML.
// ============================================================
let intervaloTemporizador = null;
let numeroReal = '';          // el número completo; el campo solo lo muestra enmascarado al perder el foco
let resumenCita = null;       // datos de la cita (médico, especialidad, sede, fecha, hora, monto)
let etiquetaPagar = '<i class="fas fa-lock me-2"></i>Pagar';

const CAMPOS = ['numeroTarjeta', 'nombreTitular', 'fechaVencimiento', 'cvv'];

document.addEventListener('DOMContentLoaded', () => {
    cargarResumenCita();

    const numero = document.getElementById('numeroTarjeta');
    numero.addEventListener('input', function () {
        numeroReal = this.value.replace(/\D/g, '').slice(0, 19);
        this.value = numeroReal;
        limpiarErrorCampo('numeroTarjeta');
    });
    // RN-CU04-01: al perder el foco solo se ven los últimos 4 dígitos
    numero.addEventListener('blur', function () {
        validarCampo('numeroTarjeta');
        if (numeroReal.length > 4) {
            this.value = '•'.repeat(numeroReal.length - 4) + numeroReal.slice(-4);
        }
    });
    numero.addEventListener('focus', function () { this.value = numeroReal; });

    // RN-CU04-02: el nombre se convierte a mayúsculas
    const titular = document.getElementById('nombreTitular');
    titular.addEventListener('input', function () {
        const pos = this.selectionStart;
        this.value = this.value.toUpperCase();
        this.setSelectionRange(pos, pos);
        limpiarErrorCampo('nombreTitular');
    });
    titular.addEventListener('blur', () => validarCampo('nombreTitular'));

    // RN-CU04-03: MM/AA auto-formateado
    const venc = document.getElementById('fechaVencimiento');
    venc.addEventListener('input', function () {
        let v = this.value.replace(/\D/g, '').slice(0, 4);
        if (v.length > 2) v = v.slice(0, 2) + '/' + v.slice(2);
        this.value = v;
        limpiarErrorCampo('fechaVencimiento');
    });
    venc.addEventListener('blur', () => validarCampo('fechaVencimiento'));

    // RN-CU04-04: CVV 3-4 dígitos
    const cvv = document.getElementById('cvv');
    cvv.addEventListener('input', function () {
        this.value = this.value.replace(/\D/g, '').slice(0, 4);
        limpiarErrorCampo('cvv');
    });
    cvv.addEventListener('blur', () => validarCampo('cvv'));

    document.getElementById('formPago').addEventListener('submit', procesarPago);
});

// ---------------------------------------------------------------
// Resumen de la cita (paso 1)
// ---------------------------------------------------------------
function cargarResumenCita() {
    fetch(`/api/citas/${idCita}/resumen`)
        .then((r) => r.json())
        .then((cita) => {
            resumenCita = cita;
            document.getElementById('resMedico').textContent = cita.nombreMedico;
            document.getElementById('resEspecialidad').textContent = cita.nombreEspecialidad;
            document.getElementById('resSede').textContent = cita.nombreSucursal;
            document.getElementById('resFecha').textContent = formatearFecha(cita.fechaCita);
            document.getElementById('resHora').textContent = String(cita.horaCita).substring(0, 5);
            const monto = 'Q' + Number(cita.monto).toFixed(2);
            document.getElementById('textoMonto').textContent = monto;
            etiquetaPagar = `<i class="fas fa-lock me-2"></i>Pagar ${monto}`;
            document.getElementById('btnPagar').innerHTML = etiquetaPagar;
            if (cita.fechaExpiracionReserva) {
                iniciarTemporizador(new Date(cita.fechaExpiracionReserva));
            }
        })
        .catch(() => mostrarError('No se pudo cargar la información de la cita.'));
}

// ---------------------------------------------------------------
// Temporizador de 5 minutos (paso 1 y FA02)
// ---------------------------------------------------------------
function iniciarTemporizador(fechaExpiracion) {
    function actualizar() {
        const segundosRestantes = Math.floor((fechaExpiracion - new Date()) / 1000);
        if (segundosRestantes <= 0) {
            clearInterval(intervaloTemporizador);
            reservaExpirada();
            return;
        }
        const minutos = Math.floor(segundosRestantes / 60);
        const segundos = segundosRestantes % 60;
        document.getElementById('textoTemporizador').textContent = `${minutos}:${String(segundos).padStart(2, '0')}`;
    }
    actualizar();
    intervaloTemporizador = setInterval(actualizar, 1000);
}

// FA02: banner de error y redirección a la reserva de citas a los 4 segundos
function reservaExpirada() {
    clearInterval(intervaloTemporizador);
    const alerta = document.getElementById('alertaTemporizador');
    alerta.className = 'alert alert-danger';
    alerta.innerHTML = '<i class="fas fa-circle-exclamation me-2"></i>El tiempo para confirmar su cita ha expirado. ' +
        'El horario seleccionado ha sido liberado. Por favor, seleccione un nuevo horario. Será redirigido en unos segundos...';
    document.querySelectorAll('#formPago input, #formPago select, #formPago button').forEach((el) => (el.disabled = true));
    setTimeout(() => { window.location.href = '/citas/agendar'; }, 4000);
}

// ---------------------------------------------------------------
// Validación de campos (paso 8 y FA01): mensaje debajo de cada campo
// ---------------------------------------------------------------
function luhnValido(n) {
    let suma = 0, alternar = false;
    for (let i = n.length - 1; i >= 0; i--) {
        let d = Number(n[i]);
        if (alternar) { d *= 2; if (d > 9) d -= 9; }
        suma += d;
        alternar = !alternar;
    }
    return suma % 10 === 0;
}

const VALIDADORES = {
    numeroTarjeta() {
        if (!numeroReal) return 'El número de tarjeta es obligatorio.';
        if (numeroReal.length < 13 || numeroReal.length > 19 || !luhnValido(numeroReal)) {
            return 'El número de tarjeta no es válido.';
        }
        return null;
    },
    nombreTitular() {
        const v = document.getElementById('nombreTitular').value.trim();
        if (!v) return 'El nombre del titular es obligatorio.';
        if (!/^[\p{L} ]{5,100}$/u.test(v)) {
            return 'El nombre del titular debe tener entre 5 y 100 caracteres, solo letras y espacios.';
        }
        return null;
    },
    fechaVencimiento() {
        const v = document.getElementById('fechaVencimiento').value;
        if (!v) return 'La fecha de vencimiento es obligatoria.';
        if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(v)) return 'Formato inválido. Use MM/AA';
        const mes = Number(v.slice(0, 2));
        const anio = 2000 + Number(v.slice(3));
        if (new Date(anio, mes, 0, 23, 59, 59) < new Date()) return 'La tarjeta está vencida.';
        return null;
    },
    cvv() {
        const v = document.getElementById('cvv').value;
        if (!v) return 'El CVV es obligatorio.';
        if (!/^\d{3,4}$/.test(v)) return 'El CVV debe contener 3 o 4 dígitos.';
        return null;
    },
};

function validarCampo(campo) {
    const mensaje = VALIDADORES[campo]();
    marcarErrorCampo(campo, mensaje);
    return mensaje === null;
}

function validarTodo() {
    let primerInvalido = null;
    CAMPOS.forEach((campo) => {
        if (!validarCampo(campo) && !primerInvalido) primerInvalido = campo;
    });
    if (primerInvalido) document.getElementById(primerInvalido).focus();
    return primerInvalido === null;
}

function marcarErrorCampo(campo, mensaje) {
    const input = document.getElementById(campo);
    const feedback = document.querySelector(`[data-error-for="${campo}"]`);
    input.classList.toggle('is-invalid', mensaje !== null);
    if (feedback) feedback.textContent = mensaje || '';
}
function limpiarErrorCampo(campo) { marcarErrorCampo(campo, null); }

// ---------------------------------------------------------------
// Pago (pasos 7-12 y FA03)
// ---------------------------------------------------------------
function procesarPago(e) {
    e.preventDefault();
    ocultarError();

    // Pasos 7-8: si hay errores se muestran bajo cada campo y no se envía nada (FA01)
    if (!validarTodo()) return;

    // Paso 9: botón en "Procesando pago..." y deshabilitado (evita doble envío)
    const btn = document.getElementById('btnPagar');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Procesando pago...';

    // El CVV solo se valida en el navegador: nunca se envía ni se guarda.
    const datosTarjeta = {
        tipoTarjeta: document.getElementById('tipoTarjeta').value,
        numeroTarjeta: numeroReal,
        nombreTitular: document.getElementById('nombreTitular').value.trim(),
        fechaVencimiento: document.getElementById('fechaVencimiento').value,
    };

    fetch(`/api/pagos/citas/${idCita}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(datosTarjeta),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((comprobante) => {
            clearInterval(intervaloTemporizador);
            mostrarComprobante(comprobante);
        })
        .catch((error) => {
            // La reserva venció mientras se llenaba el formulario (FA02)
            if (error.status === 410) { reservaExpirada(); return; }

            // Errores de validación del servidor, campo por campo (FA01)
            if (error.errors) {
                Object.entries(error.errors).forEach(([campo, msg]) => {
                    if (CAMPOS.includes(campo)) marcarErrorCampo(campo, msg);
                });
            }
            // FA03: el formulario sigue activo y el temporizador sigue corriendo
            mostrarError(error.mensaje || error.message || 'No se pudo procesar el pago. Intente de nuevo.');
            btn.disabled = false;
            btn.innerHTML = etiquetaPagar;
        });
}

// ---------------------------------------------------------------
// Pantalla "¡Pago Exitoso!" (pasos 13-14)
// ---------------------------------------------------------------
function mostrarComprobante(c) {
    document.getElementById('vistaFormulario').style.display = 'none';
    document.getElementById('vistaComprobante').style.display = 'block';

    document.getElementById('mensajeExito').textContent =
        `¡Pago realizado exitosamente! Número de transacción: ${c.numeroTransaccion}. Su cita ha sido confirmada.`;

    const r = resumenCita || {};
    const fila = (etiqueta, valor, extra = '') =>
        `<div class="resumen-fila"><span class="etiqueta">${etiqueta}</span><span class="valor ${extra}">${escapar(valor)}</span></div>`;

    document.getElementById('detalleComprobante').innerHTML =
        fila('No. de transacción', c.numeroTransaccion, 'text-mono') +
        fila('Médico', r.nombreMedico || '-') +
        fila('Especialidad', r.nombreEspecialidad || '-') +
        fila('Sucursal', c.sucursal || r.nombreSucursal || '-') +
        fila('Fecha', r.fechaCita ? formatearFecha(r.fechaCita) : '-') +
        fila('Hora', r.horaCita ? String(r.horaCita).substring(0, 5) : '-') +
        fila('Monto pagado', `Q${Number(c.monto).toFixed(2)} (${c.formaPago})`, 'text-mono');
}

// ---------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------
function formatearFecha(fechaISO) {
    return new Date(fechaISO + 'T00:00:00')
        .toLocaleDateString('es-GT', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}
function escapar(texto) {
    return String(texto).replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}
function mostrarError(mensaje) {
    const el = document.getElementById('alertaError');
    el.textContent = mensaje;
    el.classList.remove('d-none');
}
function ocultarError() {
    document.getElementById('alertaError').classList.add('d-none');
}
