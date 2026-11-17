// ============================================================
// CU-10: Cobro de Laboratorio en Caja (caja/caja-laboratorio.html)
// Mismo esquema que caja.js (CU-06), pero para órdenes de laboratorio.
// ============================================================

const estadoCajaLab = { ordenActual: null, metodoSeleccionado: null };

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('input[name="tipoBusquedaLab"]').forEach((radio) => {
        radio.addEventListener('change', actualizarPlaceholderLab);
    });
    const input = document.getElementById('labInputBusqueda');
    input.addEventListener('keyup', (e) => {
        if (e.key === 'Enter') ejecutarBusquedaLab();
    });
    input.addEventListener('input', actualizarContadorDpi);
    document.getElementById('labBtnBuscar').addEventListener('click', ejecutarBusquedaLab);

    document.getElementById('montoRecibidoLab').addEventListener('input', actualizarPreviewCambioLab);
    document.getElementById('tarjetaUltimos4Lab').addEventListener('input', function () {
        this.value = this.value.replace(/\D/g, '').slice(0, 4);
        document.getElementById('errorUltimos4Lab').classList.toggle('d-none', this.value.length === 4 || this.value.length === 0);
        validarBotonPagoLab();
    });
});

function esBusquedaPorDpi() {
    return document.getElementById('labTipoDpi').checked;
}

function actualizarPlaceholderLab() {
    const porDpi = esBusquedaPorDpi();
    const input = document.getElementById('labInputBusqueda');
    input.value = '';
    input.placeholder = porDpi ? 'DPI del paciente (13 dígitos)' : 'Número de orden (ej. LAB-1A2B3C4D)';
    input.maxLength = porDpi ? 13 : 30;
    document.getElementById('labContadorDpi').classList.toggle('d-none', !porDpi);
    document.getElementById('labResultadoBusqueda').innerHTML = '';
    actualizarContadorDpi();
}

function actualizarContadorDpi() {
    if (!esBusquedaPorDpi()) return;
    const largo = document.getElementById('labInputBusqueda').value.length;
    document.getElementById('labContadorDpi').textContent = `${largo}/13 dígitos`;
}

/** RN-GLOBAL-001: devuelve el mensaje de error o null si el DPI es válido. */
function validarDpiLab(dpi) {
    if (!dpi) return 'El campo DPI es obligatorio. Por favor, ingrese su número de DPI.';
    if (!/^\d+$/.test(dpi)) return 'El DPI debe contener únicamente números. No se permiten letras ni caracteres especiales.';
    if (dpi.length !== 13) return `El DPI debe contener exactamente 13 dígitos. Usted ingresó ${dpi.length} dígitos.`;
    return null;
}

// ================================================================
// Pasos 2-3: buscar órdenes pendientes (FA01)
// ================================================================
function ejecutarBusquedaLab() {
    const tipo = document.querySelector('input[name="tipoBusquedaLab"]:checked').value;
    const valor = document.getElementById('labInputBusqueda').value.trim();
    const resultado = document.getElementById('labResultadoBusqueda');

    if (tipo === 'DPI') {
        const error = validarDpiLab(valor);
        if (error) {
            resultado.innerHTML = `<div class="alert alert-danger">${escaparHtmlCajaLab(error)}</div>`;
            return;
        }
    } else if (!valor) {
        resultado.innerHTML = '<div class="alert alert-danger">Debe ingresar un número de orden.</div>';
        return;
    }

    document.getElementById('labBtnBuscar').disabled = true;
    document.getElementById('labBtnBuscarSpinner').classList.remove('d-none');

    fetch(`/api/caja/laboratorio/buscar?tipo=${tipo}&valor=${encodeURIComponent(valor)}`)
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            if (datos.resultado === 'SIN_RESULTADOS') {
                resultado.innerHTML = `<div class="alert alert-warning">${escaparHtmlCajaLab(datos.mensaje)}</div>`;
                return;
            }
            pintarOrdenesPendientes(datos.ordenes);
        })
        .catch((error) => {
            resultado.innerHTML = `<div class="alert alert-danger">${escaparHtmlCajaLab(error.message || 'No se pudo conectar con el servidor. Intente de nuevo más tarde.')}</div>`;
        })
        .finally(() => {
            document.getElementById('labBtnBuscar').disabled = false;
            document.getElementById('labBtnBuscarSpinner').classList.add('d-none');
        });
}

// Paso 3: por cada orden -> número, paciente, DPI, cantidad de exámenes, fecha y monto
function pintarOrdenesPendientes(ordenes) {
    estadoCajaLab.ordenesEncontradas = ordenes;
    document.getElementById('labResultadoBusqueda').innerHTML = `
        <p class="small text-muted mb-2">${ordenes.length} orden(es) pendiente(s) de pago</p>
        ${ordenes.map((o, i) => `
        <div class="hh-card mb-3">
            <div class="d-flex flex-wrap justify-content-between align-items-center gap-3">
                <div>
                    <h3 class="mb-1" style="font-size:1.1rem;">
                        <span class="text-mono">${escaparHtmlCajaLab(o.numeroOrden)}</span>
                        <span class="badge badge-soft-warning ms-2">Pendiente</span>
                    </h3>
                    <div class="small">${escaparHtmlCajaLab(o.nombrePaciente)} · DPI <span class="text-mono">${escaparHtmlCajaLab(o.dpiPaciente || '—')}</span></div>
                    <div class="small text-muted">
                        ${o.cantidadExamenes} examen(es) · ${new Date(o.fechaCreacion).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' })}
                    </div>
                </div>
                <div class="text-end">
                    <div class="text-mono fw-bold" style="font-size:1.25rem; color: var(--color-primary-700);">Q${Number(o.montoTotal).toFixed(2)}</div>
                    <button class="btn btn-teal btn-sm mt-1" onclick="abrirCobroLab(${i})">
                        <i class="fas fa-cash-register me-1"></i>Cobrar
                    </button>
                </div>
            </div>
        </div>`).join('')}
    `;
}

// ================================================================
// Pasos 4-5: formulario de cobro
// ================================================================
function abrirCobroLab(indice) {
    const orden = estadoCajaLab.ordenesEncontradas[indice];
    estadoCajaLab.ordenActual = orden;
    estadoCajaLab.metodoSeleccionado = null;

    document.getElementById('vistaBusquedaLab').classList.add('d-none');
    document.getElementById('vistaCobroLab').classList.remove('d-none');

    document.getElementById('cobroLabNoOrden').textContent = orden.numeroOrden;
    document.getElementById('cobroLabDetalle').innerHTML = `
        <div class="resumen-fila"><span class="etiqueta">Paciente</span><span class="valor">${escaparHtmlCajaLab(orden.nombrePaciente)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">DPI</span><span class="valor text-mono">${escaparHtmlCajaLab(orden.dpiPaciente || '—')}</span></div>
        <div class="resumen-fila"><span class="etiqueta">No. de orden</span><span class="valor text-mono">${escaparHtmlCajaLab(orden.numeroOrden)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Cantidad de exámenes</span><span class="valor">${orden.cantidadExamenes}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Médico</span><span class="valor">${escaparHtmlCajaLab(orden.nombreMedico)}</span></div>
    `;
    const monto = 'Q' + Number(orden.montoTotal).toFixed(2);
    document.getElementById('cobroLabMonto').textContent = monto;
    document.getElementById('btnConfirmarPagoLabTexto').innerHTML =
        `<i class="fas fa-cash-register me-2"></i>Confirmar Pago ${monto}`;

    document.querySelectorAll('#vistaCobroLab .selection-card').forEach((el) => el.classList.remove('selected'));
    document.getElementById('camposEfectivoLab').classList.add('d-none');
    document.getElementById('camposTarjetaLab').classList.add('d-none');
    document.getElementById('montoRecibidoLab').value = '';
    document.getElementById('tarjetaUltimos4Lab').value = '';
    document.getElementById('errorUltimos4Lab').classList.add('d-none');
    document.getElementById('cambioPreviewLab').textContent = '';
    ocultarAlertaCobroLab();
    validarBotonPagoLab();
}

function volverABusquedaLab() {
    document.getElementById('vistaCobroLab').classList.add('d-none');
    document.getElementById('vistaBusquedaLab').classList.remove('d-none');
    estadoCajaLab.ordenActual = null;
}

// Paso 6: método de pago
function seleccionarMetodoLab(elemento) {
    document.querySelectorAll('#vistaCobroLab .selection-card').forEach((el) => el.classList.remove('selected'));
    elemento.classList.add('selected');
    estadoCajaLab.metodoSeleccionado = elemento.dataset.metodo;
    ocultarAlertaCobroLab();

    const esEfectivo = estadoCajaLab.metodoSeleccionado === 'Efectivo';
    document.getElementById('camposEfectivoLab').classList.toggle('d-none', !esEfectivo);
    document.getElementById('camposTarjetaLab').classList.toggle('d-none', esEfectivo);
    validarBotonPagoLab();
}

// Paso 7: "Cambio a Devolver" en tiempo real
function actualizarPreviewCambioLab() {
    const orden = estadoCajaLab.ordenActual;
    const preview = document.getElementById('cambioPreviewLab');
    if (!orden) return;

    const recibido = parseFloat(document.getElementById('montoRecibidoLab').value);
    const total = Number(orden.montoTotal);
    if (isNaN(recibido)) {
        preview.textContent = '';
    } else if (recibido < total) {
        preview.textContent = `El monto recibido (Q${recibido.toFixed(2)}) es menor al monto a cobrar (Q${total.toFixed(2)})`;
        preview.className = 'small text-danger mt-1';
    } else {
        preview.textContent = `Monto recibido: Q${recibido.toFixed(2)}. Cambio a Devolver: Q${(recibido - total).toFixed(2)}.`;
        preview.className = 'small text-muted mt-1';
    }
    validarBotonPagoLab();
}

function validarBotonPagoLab() {
    const orden = estadoCajaLab.ordenActual;
    let habilitado = !!estadoCajaLab.metodoSeleccionado && !!orden;
    if (habilitado && estadoCajaLab.metodoSeleccionado === 'Efectivo') {
        const recibido = parseFloat(document.getElementById('montoRecibidoLab').value);
        habilitado = !isNaN(recibido) && recibido >= Number(orden.montoTotal);
    } else if (habilitado) {
        habilitado = /^\d{4}$/.test(document.getElementById('tarjetaUltimos4Lab').value);
    }
    document.getElementById('btnConfirmarPagoLab').disabled = !habilitado;
}

// Pasos 8-10 / FA03 / FA04
function registrarPagoLab() {
    const idOrden = estadoCajaLab.ordenActual.idOrden;
    const body = { metodoPago: estadoCajaLab.metodoSeleccionado };

    if (estadoCajaLab.metodoSeleccionado === 'Efectivo') {
        body.montoRecibido = parseFloat(document.getElementById('montoRecibidoLab').value);
    } else {
        body.tarjetaUltimos4 = document.getElementById('tarjetaUltimos4Lab').value;
    }

    const btn = document.getElementById('btnConfirmarPagoLab');
    btn.disabled = true;
    document.getElementById('btnConfirmarPagoLabSpinner').classList.remove('d-none');
    ocultarAlertaCobroLab();

    fetch(`/api/caja/laboratorio/ordenes/${idOrden}/registrar-pago`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then(mostrarComprobanteLab)
        .catch((error) => {
            // FA04 (tarjeta rechazada) y "monto menor" llegan aquí
            mostrarAlertaCobroLab(error.message || 'No se pudo conectar con el servidor. Intente de nuevo más tarde.');
        })
        .finally(() => {
            document.getElementById('btnConfirmarPagoLabSpinner').classList.add('d-none');
            validarBotonPagoLab();
        });
}

function mostrarAlertaCobroLab(mensaje) {
    const alerta = document.getElementById('cobroLabAlerta');
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');
}
function ocultarAlertaCobroLab() {
    document.getElementById('cobroLabAlerta').classList.add('d-none');
}

// ================================================================
// Pasos 10-11: comprobante (PaymentReceipt) [RN-GLOBAL-005]
// ================================================================
function mostrarComprobanteLab(c) {
    document.getElementById('vistaCobroLab').classList.add('d-none');
    document.getElementById('vistaComprobanteLab').classList.remove('d-none');
    document.getElementById('comprobanteLabMensaje').textContent = c.mensaje;

    const fecha = new Date(c.fechaHora).toLocaleString('es-GT');
    let filas = `
        <div class="resumen-fila"><span class="etiqueta">No. de transacción</span><span class="valor text-mono">${escaparHtmlCajaLab(c.numeroTransaccion)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Sucursal</span><span class="valor">${escaparHtmlCajaLab(c.sucursal)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Paciente</span><span class="valor">${escaparHtmlCajaLab(c.nombrePaciente)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Detalle</span><span class="valor">${escaparHtmlCajaLab(c.detalleServicio)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Monto pagado</span><span class="valor text-mono">Q${Number(c.monto).toFixed(2)} (${escaparHtmlCajaLab(c.formaPago)})</span></div>`;

    if (c.montoRecibido != null) {
        filas += `
        <div class="resumen-fila"><span class="etiqueta">Monto recibido</span><span class="valor text-mono">Q${Number(c.montoRecibido).toFixed(2)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Cambio devuelto</span><span class="valor text-mono">Q${Number(c.cambio).toFixed(2)}</span></div>`;
    }
    filas += `
        <div class="resumen-fila"><span class="etiqueta">Fecha y hora</span><span class="valor">${fecha}</span></div>`;
    document.getElementById('detalleComprobanteLab').innerHTML = filas;
}

// Paso 11: "Nuevo Cobro"
function nuevoCobroLab() {
    estadoCajaLab.ordenActual = null;
    estadoCajaLab.metodoSeleccionado = null;
    document.getElementById('vistaComprobanteLab').classList.add('d-none');
    document.getElementById('vistaBusquedaLab').classList.remove('d-none');
    document.getElementById('labInputBusqueda').value = '';
    document.getElementById('labResultadoBusqueda').innerHTML = '';
    actualizarContadorDpi();
}

function escaparHtmlCajaLab(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
