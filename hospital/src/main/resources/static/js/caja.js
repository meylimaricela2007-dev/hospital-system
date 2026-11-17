// ============================================================
// CU-06: Cobro de Consulta en Caja (caja/caja.html)
// ============================================================

const estadoCaja = { citaActual: null, metodoSeleccionado: null };

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('input[name="tipoBusquedaCaja"]').forEach((radio) => {
        radio.addEventListener('change', actualizarPlaceholderCaja);
    });
    document.getElementById('cajaInputBusqueda').addEventListener('keyup', (e) => {
        if (e.key === 'Enter') ejecutarBusquedaCaja();
    });
    document.getElementById('cajaBtnBuscar').addEventListener('click', ejecutarBusquedaCaja);

    document.getElementById('montoRecibido').addEventListener('input', actualizarPreviewCambio);
    document.getElementById('tarjetaUltimos4').addEventListener('input', function () {
        this.value = this.value.replace(/\D/g, '').slice(0, 4);
        validarBotonRegistrarPago();
    });
});

function actualizarPlaceholderCaja() {
    const esCita = document.getElementById('cajaTipoCita').checked;
    const input = document.getElementById('cajaInputBusqueda');
    input.value = '';
    input.placeholder = esCita ? 'Número de cita' : 'DPI del paciente (13 dígitos)';
    input.maxLength = esCita ? 10 : 13;
}

// ================================================================
// Paso 1: buscar la cita (FA02)
// ================================================================
function ejecutarBusquedaCaja() {
    const tipo = document.querySelector('input[name="tipoBusquedaCaja"]:checked').value;
    const valor = document.getElementById('cajaInputBusqueda').value.trim();
    if (!valor) return;

    document.getElementById('cajaBtnBuscar').disabled = true;
    document.getElementById('cajaBtnBuscarSpinner').classList.remove('d-none');

    fetch(`/api/caja/buscar?tipo=${tipo}&valor=${encodeURIComponent(valor)}`)
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            if (datos.resultado === 'SIN_RESULTADOS') {
                document.getElementById('cajaResultadoBusqueda').innerHTML =
                    `<div class="alert alert-warning">${escaparHtmlCaja(datos.mensaje)}</div>`;
                return;
            }
            abrirCobro(datos.cita);
        })
        .catch((error) => {
            document.getElementById('cajaResultadoBusqueda').innerHTML =
                `<div class="alert alert-danger">${escaparHtmlCaja(error.mensaje || error.message || 'No se pudo conectar con el servidor. Intente de nuevo más tarde.')}</div>`;
        })
        .finally(() => {
            document.getElementById('cajaBtnBuscar').disabled = false;
            document.getElementById('cajaBtnBuscarSpinner').classList.add('d-none');
        });
}

// ================================================================
// Paso 2 en adelante: pantalla de cobro
// ================================================================
function abrirCobro(cita) {
    estadoCaja.citaActual = cita;
    estadoCaja.metodoSeleccionado = null;

    document.getElementById('vistaBusqueda').classList.add('d-none');
    document.getElementById('cajaResultadoBusqueda').innerHTML = '';
    document.getElementById('vistaCobro').classList.remove('d-none');

    document.getElementById('cobroNoCita').textContent = cita.idCita;
    document.getElementById('cobroDetalleCita').innerHTML = `
        <div class="resumen-fila"><span class="etiqueta">Paciente</span><span class="valor">${escaparHtmlCaja(cita.nombrePaciente)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Especialidad</span><span class="valor">${escaparHtmlCaja(cita.especialidad)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Médico</span><span class="valor">${escaparHtmlCaja(cita.nombreMedico)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Sucursal</span><span class="valor">${escaparHtmlCaja(cita.sucursal)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Fecha</span><span class="valor">${cita.fechaCita} ${(cita.horaCita || '').substring(0, 5)}</span></div>
    `;
    document.getElementById('cobroMonto').textContent = 'Q' + Number(cita.montoTotal).toFixed(2);

    // Limpiar el formulario por si quedó algo de un cobro anterior
    document.querySelectorAll('#vistaCobro .selection-card').forEach((el) => el.classList.remove('selected'));
    document.getElementById('camposEfectivo').classList.add('d-none');
    document.getElementById('camposTarjeta').classList.add('d-none');
    document.getElementById('montoRecibido').value = '';
    document.getElementById('tarjetaUltimos4').value = '';
    document.getElementById('cambioPreview').textContent = '';
    ocultarAlertaCobro();
    validarBotonRegistrarPago();
}

function volverABusqueda() {
    document.getElementById('vistaCobro').classList.add('d-none');
    document.getElementById('vistaBusqueda').classList.remove('d-none');
    document.getElementById('cajaInputBusqueda').value = '';
    estadoCaja.citaActual = null;
}

// Paso 4 del flujo normal: seleccionar el método de pago
function seleccionarMetodo(elemento) {
    document.querySelectorAll('#vistaCobro .selection-card').forEach((el) => el.classList.remove('selected'));
    elemento.classList.add('selected');
    estadoCaja.metodoSeleccionado = elemento.dataset.metodo;
    ocultarAlertaCobro();

    const esEfectivo = estadoCaja.metodoSeleccionado === 'Efectivo';
    document.getElementById('camposEfectivo').classList.toggle('d-none', !esEfectivo);
    document.getElementById('camposTarjeta').classList.toggle('d-none', esEfectivo);

    validarBotonRegistrarPago();
}

// Paso 7 del flujo normal: "Monto recibido: Q[monto]. Cambio a devolver: Q[cambio]."
function actualizarPreviewCambio() {
    const cita = estadoCaja.citaActual;
    const preview = document.getElementById('cambioPreview');
    if (!cita) return;

    const recibido = parseFloat(document.getElementById('montoRecibido').value);
    if (isNaN(recibido)) {
        preview.textContent = '';
    } else if (recibido < cita.montoTotal) {
        preview.textContent = `El monto recibido (Q${recibido.toFixed(2)}) es menor al monto a cobrar (Q${Number(cita.montoTotal).toFixed(2)}).`;
        preview.className = 'small text-danger mt-1';
    } else {
        const cambio = recibido - cita.montoTotal;
        preview.textContent = `Monto recibido: Q${recibido.toFixed(2)}. Cambio a devolver: Q${cambio.toFixed(2)}.`;
        preview.className = 'small text-muted mt-1';
    }
    validarBotonRegistrarPago();
}

function validarBotonRegistrarPago() {
    let habilitado = !!estadoCaja.metodoSeleccionado;
    if (estadoCaja.metodoSeleccionado === 'Efectivo') {
        habilitado = habilitado && document.getElementById('montoRecibido').value !== '';
    } else if (estadoCaja.metodoSeleccionado) {
        habilitado = habilitado && /^\d{4}$/.test(document.getElementById('tarjetaUltimos4').value);
    }
    document.getElementById('btnRegistrarPago').disabled = !habilitado;
}

// Paso 6-9 del flujo normal / FA01 / FA04
function registrarPago() {
    const idCita = estadoCaja.citaActual.idCita;
    const body = { metodoPago: estadoCaja.metodoSeleccionado };

    if (estadoCaja.metodoSeleccionado === 'Efectivo') {
        body.montoRecibido = parseFloat(document.getElementById('montoRecibido').value);
    } else {
        body.tarjetaUltimos4 = document.getElementById('tarjetaUltimos4').value;
    }

    const btn = document.getElementById('btnRegistrarPago');
    btn.disabled = true;
    document.getElementById('btnRegistrarPagoSpinner').classList.remove('d-none');
    ocultarAlertaCobro();

    fetch(`/api/caja/citas/${idCita}/registrar-pago`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then(mostrarComprobanteCaja)
        .catch((error) => {
            // FA04 (tarjeta rechazada) y el mensaje de "monto menor" caen aquí
            mostrarAlertaCobro(error.mensaje || error.message || 'No se pudo registrar el pago.');
        })
        .finally(() => {
            btn.disabled = false;
            document.getElementById('btnRegistrarPagoSpinner').classList.add('d-none');
            validarBotonRegistrarPago();
        });
}

function mostrarAlertaCobro(mensaje) {
    const alerta = document.getElementById('cobroAlerta');
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');
}
function ocultarAlertaCobro() {
    document.getElementById('cobroAlerta').classList.add('d-none');
}

// ================================================================
// Paso 9-10: comprobante (componente PaymentReceipt)
// ================================================================
function mostrarComprobanteCaja(c) {
    document.getElementById('vistaCobro').classList.add('d-none');
    document.getElementById('vistaComprobante').classList.remove('d-none');
    document.getElementById('comprobanteMensaje').textContent = c.mensaje;

    const fecha = new Date(c.fechaHora).toLocaleString('es-GT');
    let filas = `
        <div class="resumen-fila"><span class="etiqueta">No. de transacción</span><span class="valor text-mono">${c.numeroTransaccion}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Paciente</span><span class="valor">${escaparHtmlCaja(c.nombrePaciente)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Monto pagado</span><span class="valor text-mono">Q${Number(c.monto).toFixed(2)} (${c.formaPago})</span></div>`;

    if (c.montoRecibido != null) {
        filas += `
        <div class="resumen-fila"><span class="etiqueta">Monto recibido</span><span class="valor text-mono">Q${Number(c.montoRecibido).toFixed(2)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Cambio devuelto</span><span class="valor text-mono">Q${Number(c.cambio).toFixed(2)}</span></div>`;
    }

    filas += `
        <div class="resumen-fila"><span class="etiqueta">Fecha</span><span class="valor">${fecha}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Detalle</span><span class="valor">${escaparHtmlCaja(c.detalleServicio)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Sucursal</span><span class="valor">${escaparHtmlCaja(c.sucursal)}</span></div>
    `;
    document.getElementById('detalleComprobanteCaja').innerHTML = filas;
}

// Paso 10 del flujo normal: "iniciar un nuevo cobro con el botón 'Nuevo Cobro'"
function nuevoCobro() {
    estadoCaja.citaActual = null;
    estadoCaja.metodoSeleccionado = null;
    document.getElementById('vistaComprobante').classList.add('d-none');
    document.getElementById('vistaBusqueda').classList.remove('d-none');
    document.getElementById('cajaInputBusqueda').value = '';
    document.getElementById('cajaResultadoBusqueda').innerHTML = '';
}

function escaparHtmlCaja(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
