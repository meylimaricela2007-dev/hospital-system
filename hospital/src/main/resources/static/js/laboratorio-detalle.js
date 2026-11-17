// ============================================================
// CU-09: detalle de una orden de laboratorio
// (laboratorio/orden-detalle.html)
//   Paso 3      -> resumen de la orden y lista de exámenes
//   Pasos 9-10  -> formulario de resultado + "Guardar Resultado"
//   Pasos 11-13 -> "Publicar resultado" (uno por uno)
//   FA01        -> orden externa (solo lectura)
//   FA02        -> badge rojo "Fuera de rango"
// ============================================================
const estadoDetalle = {
    idOrden: null,
    puedeEditar: false,
    orden: null,
};

document.addEventListener('DOMContentLoaded', () => {
    estadoDetalle.idOrden = document.getElementById('labIdOrden').value;
    estadoDetalle.puedeEditar = document.getElementById('labPuedeEditar').value === 'true';
    cargarDetalleOrden();
});

async function cargarDetalleOrden() {
    try {
        const respuesta = await fetch(`/api/laboratorio/ordenes/${estadoDetalle.idOrden}`);
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo cargar la orden de laboratorio.');
        estadoDetalle.orden = datos;
        pintarResumen();
        pintarExamenes();
    } catch (error) {
        mostrarAlertaGeneral('danger', error.message === 'Failed to fetch'
            ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.'
            : error.message);
        document.getElementById('detResumen').innerHTML = '';
    }
}

// ================================================================
// Paso 3: resumen
// ================================================================
function pintarResumen() {
    const o = estadoDetalle.orden;
    document.getElementById('detNumeroOrden').textContent = o.numeroOrden;
    document.getElementById('detMontoTotal').textContent = 'Q' + Number(o.montoTotal).toFixed(2);
    document.getElementById('detBadges').innerHTML = `
        <span class="badge ${badgeEstadoOrdenDet(o.estado)}">${escaparHtmlDet(o.estado)}</span>
        ${o.esExterna ? '<span class="badge badge-soft-info ms-1"><i class="fas fa-arrow-up-right-from-square me-1"></i>Externa</span>' : ''}
    `;

    document.getElementById('detResumen').innerHTML = `
        <div class="resumen-fila"><span class="etiqueta">Paciente</span><span class="valor">${escaparHtmlDet(o.nombrePaciente)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">DPI</span><span class="valor text-mono">${escaparHtmlDet(o.dpiPaciente || '—')}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Médico</span><span class="valor">${escaparHtmlDet(o.nombreMedico)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Fecha de creación</span><span class="valor">${formatearFechaDet(o.fechaCreacion)}</span></div>
        <div class="resumen-fila"><span class="etiqueta">Notas</span><span class="valor">${escaparHtmlDet(o.notas || '—')}</span></div>
    `;

    const aviso = document.getElementById('detAvisoEstado');
    let html = '';
    if (o.esExterna) {
        // FA01: orden externa
        html = `<div class="alert alert-info mb-0"><i class="fas fa-circle-info me-2"></i>
            <strong>Orden externa.</strong> El paciente realiza los exámenes en un laboratorio externo
            y presenta los resultados al médico en su cita de seguimiento.</div>`;
    } else if (o.estado === 'Pendiente') {
        // Pasos 4-5 / RN-CU09-01
        html = `<div class="alert alert-warning mb-0"><i class="fas fa-triangle-exclamation me-2"></i>
            <strong>El pago debe estar completado antes de proceder con la toma de muestras.</strong><br>
            Informe al paciente los exámenes ordenados y el monto total
            (<span class="text-mono">Q${Number(o.montoTotal).toFixed(2)}</span>),
            y que debe realizar el pago en caja con el número de orden <span class="text-mono">${escaparHtmlDet(o.numeroOrden)}</span>.</div>`;
    } else if (o.estado === 'Completada') {
        html = `<div class="alert alert-success mb-0"><i class="fas fa-circle-check me-2"></i>
            Todos los resultados fueron publicados. La orden está completada y disponible para consulta del médico tratante.</div>`;
    } else if (o.estado === 'En Proceso' && !estadoDetalle.puedeEditar) {
        html = `<div class="alert alert-info mb-0"><i class="fas fa-eye me-2"></i>
            Vista de solo lectura: únicamente el personal de laboratorio puede registrar y publicar resultados.</div>`;
    } else if (o.estado === 'En Proceso') {
        html = `<div class="alert alert-success mb-0"><i class="fas fa-vial me-2"></i>
            Pago confirmado. Puede proceder con la toma de muestras y registrar los resultados.</div>`;
    }
    aviso.innerHTML = html;
    aviso.classList.toggle('d-none', html === '');
}

// ================================================================
// Pasos 9-13: exámenes
// ================================================================
function ordenEditable() {
    const o = estadoDetalle.orden;
    return estadoDetalle.puedeEditar && !o.esExterna && o.estado === 'En Proceso';
}

function pintarExamenes() {
    const examenes = estadoDetalle.orden.examenes;
    const publicados = examenes.filter((e) => e.publicado).length;
    document.getElementById('detProgreso').textContent = `${publicados}/${examenes.length} publicados`;
    document.getElementById('detListaExamenes').innerHTML = examenes.map(tarjetaExamen).join('');

    // Enlazar eventos de cada formulario editable
    examenes.forEach((e) => {
        const form = document.getElementById(`formExamen${e.idDetalleOrden}`);
        if (!form) return;
        // Si el usuario cambia algo después de guardar, debe volver a guardar antes de publicar.
        form.querySelectorAll('input, textarea').forEach((campo) => {
            campo.addEventListener('input', () => marcarComoModificado(e.idDetalleOrden));
            campo.addEventListener('change', () => marcarComoModificado(e.idDetalleOrden));
        });
    });
}

function tarjetaExamen(e) {
    const id = e.idDetalleOrden;
    const rango = e.rangoReferencia ? `Rango de referencia: <span class="text-mono">${escaparHtmlDet(e.rangoReferencia)}</span>` : 'Sin rango de referencia configurado';

    // FA02: badge rojo "Fuera de rango (rango)"
    const badgeFueraRango = (e.valorResultado && e.fueraDeRango)
        ? `<span class="badge badge-soft-danger ms-1"><i class="fas fa-triangle-exclamation me-1"></i>Fuera de rango${e.rangoReferencia ? ' (' + escaparHtmlDet(e.rangoReferencia) + ')' : ''}</span>`
        : '';
    const badgePublicado = e.publicado
        ? '<span class="badge badge-soft-success ms-1"><i class="fas fa-check me-1"></i>Publicado</span>'
        : (e.valorResultado ? '<span class="badge badge-soft-warning ms-1">Guardado · sin publicar</span>' : '');

    const encabezado = `
        <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
            <div>
                <h3 class="mb-1" style="font-size:1.15rem;">${escaparHtmlDet(e.nombreExamen)}${badgePublicado}${badgeFueraRango}</h3>
                <div class="small text-muted">${rango}</div>
            </div>
            <span class="text-mono fw-semibold">Q${Number(e.monto).toFixed(2)}</span>
        </div>`;

    // Solo lectura: publicado, sin permisos, orden pendiente/externa/completada
    if (e.publicado || !ordenEditable()) {
        const cuerpo = e.valorResultado ? `
            <div class="resumen-fila"><span class="etiqueta">Resultado</span><span class="valor text-mono">${escaparHtmlDet(e.valorResultado)} ${escaparHtmlDet(e.unidadResultado || '')}</span></div>
            <div class="resumen-fila"><span class="etiqueta">Fecha del resultado</span><span class="valor">${formatearFechaDet(e.fechaResultado)}</span></div>
            <div class="resumen-fila"><span class="etiqueta">Notas</span><span class="valor">${escaparHtmlDet(e.notasResultado || '—')}</span></div>`
            : '<p class="text-muted small mb-0"><i class="fas fa-hourglass-half me-1"></i>Resultado aún no registrado.</p>';
        return `<div class="hh-card mb-3" id="tarjetaExamen${id}">${encabezado}${cuerpo}</div>`;
    }

    // Paso 9: formulario de resultado
    const fecha = e.fechaResultado ? e.fechaResultado.substring(0, 16) : fechaLocalAhora();
    const unidad = e.unidadResultado ?? e.unidadMedida ?? '';
    const guardado = !!e.valorResultado;

    return `
    <div class="hh-card mb-3" id="tarjetaExamen${id}">
        ${encabezado}
        <div id="alertaExamen${id}" class="alert d-none"></div>
        <form id="formExamen${id}" onsubmit="return false;" novalidate>
            <div class="row g-3">
                <div class="col-md-5">
                    <label class="form-label" for="valor${id}">Valor del Resultado</label>
                    <input type="text" class="form-control text-mono" id="valor${id}" maxlength="100"
                           value="${escaparHtmlDet(e.valorResultado || '')}" placeholder="Ej. 95">
                    <div class="invalid-feedback">El valor del resultado es obligatorio.</div>
                </div>
                <div class="col-md-3">
                    <label class="form-label" for="unidad${id}">Unidad</label>
                    <input type="text" class="form-control" id="unidad${id}" maxlength="30"
                           value="${escaparHtmlDet(unidad)}" placeholder="mg/dL">
                </div>
                <div class="col-md-4">
                    <label class="form-label" for="fecha${id}">Fecha del Resultado</label>
                    <input type="datetime-local" class="form-control" id="fecha${id}" value="${fecha}" max="${fechaLocalAhora()}">
                    <div class="invalid-feedback">La fecha del resultado es obligatoria.</div>
                </div>
                <div class="col-12">
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" id="fuera${id}" ${e.fueraDeRango ? 'checked' : ''}>
                        <label class="form-check-label" for="fuera${id}">
                            Fuera de Rango <span class="small text-muted">(marcar manualmente si el valor está fuera del rango de referencia)</span>
                        </label>
                    </div>
                </div>
                <div class="col-12">
                    <label class="form-label" for="notas${id}">Notas del Resultado</label>
                    <textarea class="form-control" id="notas${id}" rows="2" maxlength="2000">${escaparHtmlDet(e.notasResultado || '')}</textarea>
                </div>
            </div>
            <div class="d-flex flex-column flex-sm-row gap-2 mt-3">
                <button type="button" class="btn btn-teal flex-fill" id="btnGuardar${id}" onclick="guardarResultado(${id})">
                    <i class="fas fa-floppy-disk me-2"></i>Guardar Resultado
                    <span class="spinner-border spinner-border-sm ms-2 d-none" id="spinGuardar${id}"></span>
                </button>
                <button type="button" class="btn btn-accent flex-fill" id="btnPublicar${id}" onclick="publicarResultado(${id})"
                        ${guardado ? '' : 'disabled'} title="${guardado ? '' : 'Guarde el resultado antes de publicarlo'}">
                    <i class="fas fa-bullhorn me-2"></i>Publicar resultado
                    <span class="spinner-border spinner-border-sm ms-2 d-none" id="spinPublicar${id}"></span>
                </button>
            </div>
        </form>
    </div>`;
}

function marcarComoModificado(id) {
    const btn = document.getElementById(`btnPublicar${id}`);
    if (btn) {
        btn.disabled = true;
        btn.title = 'Guarde los cambios antes de publicar';
    }
}

// Pasos 9-10: guardar resultado
async function guardarResultado(id) {
    const valor = document.getElementById(`valor${id}`);
    const fecha = document.getElementById(`fecha${id}`);
    valor.classList.toggle('is-invalid', valor.value.trim() === '');
    fecha.classList.toggle('is-invalid', fecha.value === '');
    if (valor.value.trim() === '' || fecha.value === '') return;

    const body = {
        valorResultado: valor.value.trim(),
        unidadResultado: document.getElementById(`unidad${id}`).value.trim(),
        fechaResultado: fecha.value,
        fueraDeRango: document.getElementById(`fuera${id}`).checked,
        notasResultado: document.getElementById(`notas${id}`).value.trim(),
    };

    await enviarAccionExamen(id, `/api/laboratorio/ordenes/${estadoDetalle.idOrden}/examenes/${id}/resultado`, body, 'Guardar');
}

// Pasos 11-12: publicar resultado (individual)
async function publicarResultado(id) {
    await enviarAccionExamen(id, `/api/laboratorio/ordenes/${estadoDetalle.idOrden}/examenes/${id}/publicar`, null, 'Publicar');
}

async function enviarAccionExamen(id, url, body, accion) {
    const btnGuardar = document.getElementById(`btnGuardar${id}`);
    const btnPublicar = document.getElementById(`btnPublicar${id}`);
    const spinner = document.getElementById(`spin${accion}${id}`);
    btnGuardar.disabled = true;
    btnPublicar.disabled = true;
    spinner.classList.remove('d-none');
    ocultarAlertaExamen(id);

    try {
        const opciones = { method: 'POST' };
        if (body) {
            opciones.headers = { 'Content-Type': 'application/json' };
            opciones.body = JSON.stringify(body);
        }
        const respuesta = await fetch(url, opciones);
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo completar la operación.');

        // Actualizar el examen en memoria y repintar
        const examenes = estadoDetalle.orden.examenes;
        const indice = examenes.findIndex((e) => e.idDetalleOrden === id);
        examenes[indice] = datos.examen;
        estadoDetalle.orden.estado = datos.estadoOrden;

        pintarResumen();
        pintarExamenes();
        mostrarAlertaExamen(id, 'success', datos.mensaje);
        if (datos.ordenCompletada) {
            mostrarAlertaGeneral('success', datos.mensaje);
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    } catch (error) {
        mostrarAlertaExamen(id, 'danger', error.message === 'Failed to fetch'
            ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.'
            : error.message);
        btnGuardar.disabled = false;
        btnPublicar.disabled = !estadoDetalle.orden.examenes.find((e) => e.idDetalleOrden === id)?.valorResultado;
        spinner.classList.add('d-none');
    }
}

// ================================================================
// Helpers
// ================================================================
function mostrarAlertaExamen(id, tipo, mensaje) {
    const alerta = document.getElementById(`alertaExamen${id}`);
    if (!alerta) {
        // La tarjeta quedó en solo lectura (publicada): mostrar arriba
        mostrarAlertaGeneral(tipo, mensaje);
        return;
    }
    alerta.className = `alert alert-${tipo}`;
    alerta.textContent = mensaje;
}
function ocultarAlertaExamen(id) {
    const alerta = document.getElementById(`alertaExamen${id}`);
    if (alerta) alerta.className = 'alert d-none';
}

function mostrarAlertaGeneral(tipo, mensaje) {
    const alerta = document.getElementById('detAlertaGeneral');
    alerta.className = `alert alert-${tipo}`;
    alerta.textContent = mensaje;
}

function badgeEstadoOrdenDet(estado) {
    if (estado === 'Completada') return 'badge-soft-success';
    if (estado === 'Pendiente') return 'badge-soft-warning';
    if (estado === 'Cancelada') return 'badge-soft-danger';
    return 'badge-soft-info';
}

function fechaLocalAhora() {
    const ahora = new Date();
    ahora.setMinutes(ahora.getMinutes() - ahora.getTimezoneOffset());
    return ahora.toISOString().substring(0, 16);
}

function formatearFechaDet(fecha) {
    if (!fecha) return '—';
    return new Date(fecha).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' });
}

// También escapa comillas porque se usa dentro de atributos value="...".
function escaparHtmlDet(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
