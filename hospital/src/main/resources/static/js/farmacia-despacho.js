// ============================================================
// CU-11: despacho de una receta (farmacia/despacho.html)
//   Pasos 4-5  -> detalle de la receta + disponibilidad
//   Paso 6     -> ajustar cantidades / FA02 sustitución
//   Paso 7-8   -> total del despacho
//   Paso 9     -> Confirmar Despacho
//   FA03       -> Cancelar despacho
// ============================================================
const despacho = { idReceta: null, datos: null, medicamentos: [] };

document.addEventListener('DOMContentLoaded', async () => {
    despacho.idReceta = document.getElementById('despIdReceta').value;
    document.getElementById('despTituloReceta').textContent = '#' + despacho.idReceta;

    document.getElementById('btnConfirmarDespacho').addEventListener('click', confirmarDespacho);
    document.getElementById('btnCancelarDespacho').addEventListener('click', () =>
        new bootstrap.Modal(document.getElementById('modalCancelarDespacho')).show());
    document.getElementById('btnConfirmarCancelacion').addEventListener('click', cancelarDespacho);

    await cargarDetalle();
});

async function cargarDetalle() {
    try {
        const [resDetalle, resMeds] = await Promise.all([
            fetch(`/api/farmacia/recetas/${despacho.idReceta}`),
            fetch('/api/catalogos/medicamentos')
        ]);
        const datos = await resDetalle.json().catch(() => ({}));
        // Paso 4: receta vencida -> el backend devuelve el mensaje exacto del documento
        if (!resDetalle.ok) throw new Error(datos.message || 'No se pudo cargar la receta.');
        despacho.datos = datos;
        despacho.medicamentos = resMeds.ok ? await resMeds.json() : [];
        pintarFormulario();
    } catch (error) {
        mostrarAlerta('danger', error.message === 'Failed to fetch'
            ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.' : error.message);
    }
}

function pintarFormulario() {
    const d = despacho.datos;
    document.getElementById('despPaciente').textContent = d.paciente;
    document.getElementById('despFecha').textContent = new Date(d.fechaEmision).toLocaleDateString('es-GT')
        + ` (hace ${d.diasTranscurridos} día(s))`;
    document.getElementById('despSucursal').textContent = d.sucursal;
    document.getElementById('despNotas').textContent = d.notas || '—';

    // FA01: alertas de stock bajo
    const cont = document.getElementById('despAlertasStock');
    if (d.alertas.length > 0) {
        cont.innerHTML = d.alertas.map((a) =>
            `<div class="alert alert-warning mb-2"><i class="fas fa-triangle-exclamation me-2"></i>${escaparHtml(a)}</div>`).join('');
        cont.classList.remove('d-none');
    }

    document.getElementById('cuerpoItems').innerHTML = d.items.map((it, i) => filaItem(it, i)).join('');
    document.querySelectorAll('.inp-cantidad').forEach((el) => el.addEventListener('input', recalcularTotal));
    document.querySelectorAll('.chk-sustituir').forEach((el) => el.addEventListener('change', (e) => {
        const idx = e.target.dataset.idx;
        document.getElementById('sustit-' + idx).classList.toggle('d-none', !e.target.checked);
        recalcularTotal();
    }));
    document.querySelectorAll('.sel-alternativo').forEach((el) => el.addEventListener('change', recalcularTotal));

    document.getElementById('despFormulario').classList.remove('d-none');
    recalcularTotal();
}

function filaItem(it, i) {
    let disponibilidad;
    if (it.sinInventario) {
        disponibilidad = '<span class="badge badge-soft-danger">Sin inventario registrado</span>';
    } else if (it.stockBajo) {
        disponibilidad = `<span class="badge badge-soft-warning">Stock bajo: ${it.stockDisponible}</span>`;
    } else {
        disponibilidad = `<span class="badge badge-soft-success">Disponible: ${it.stockDisponible}</span>`;
    }
    const maxima = it.sinInventario ? 0 : Math.min(it.cantidadRecetada, it.stockDisponible);
    const opciones = despacho.medicamentos
        .filter((m) => m.idMedicamento !== it.idMedicamento)
        .map((m) => `<option value="${m.idMedicamento}" data-precio="${m.precio}">${escaparHtml(m.nombre)} (Q${Number(m.precio).toFixed(2)})</option>`)
        .join('');

    return `
        <tr data-idx="${i}">
            <td class="ps-4">
                <div class="fw-semibold">${escaparHtml(it.medicamento)}
                    ${it.esControlado ? '<span class="badge badge-soft-danger ms-1" title="Medicamento controlado">Controlado</span>' : ''}</div>
                <div class="small text-muted">${escaparHtml(it.unidad)}</div>
            </td>
            <td class="small">${escaparHtml(it.dosis)}</td>
            <td class="text-center text-mono">${it.cantidadRecetada}</td>
            <td>${disponibilidad}</td>
            <td>
                <input type="number" class="form-control form-control-sm text-mono inp-cantidad" id="cant-${i}"
                       min="0" max="${it.cantidadRecetada}" value="${maxima}" data-idx="${i}">
            </td>
            <td class="text-end text-mono" id="precio-${i}">Q${Number(it.precioUnitario).toFixed(2)}</td>
            <td class="text-end pe-4 text-mono fw-semibold" id="sub-${i}">Q0.00</td>
        </tr>
        <tr class="border-0">
            <td colspan="7" class="pt-0 ps-4 pb-3 border-0">
                <div class="form-check">
                    <input class="form-check-input chk-sustituir" type="checkbox" id="chk-${i}" data-idx="${i}">
                    <label class="form-check-label small" for="chk-${i}">Sustituir este medicamento</label>
                </div>
                <div class="d-none mt-2 row g-2" id="sustit-${i}">
                    <div class="col-md-5">
                        <label class="form-label small mb-1">Medicamento alternativo *</label>
                        <select class="form-select form-select-sm sel-alternativo" id="alt-${i}" data-idx="${i}">
                            <option value="">-- Seleccione --</option>${opciones}
                        </select>
                    </div>
                    <div class="col-md-7">
                        <label class="form-label small mb-1">Razón de sustitución *</label>
                        <input type="text" class="form-control form-control-sm" id="razon-${i}" maxlength="500"
                               placeholder="Ej. Sin existencias del medicamento recetado">
                    </div>
                </div>
            </td>
        </tr>`;
}

function precioEfectivo(i) {
    const it = despacho.datos.items[i];
    if (document.getElementById('chk-' + i).checked) {
        const opt = document.getElementById('alt-' + i).selectedOptions[0];
        if (opt && opt.dataset.precio) return Number(opt.dataset.precio);
    }
    return Number(it.precioUnitario);
}

// Paso 7: total del despacho
function recalcularTotal() {
    let total = 0;
    despacho.datos.items.forEach((it, i) => {
        const cantidad = Math.max(0, Number(document.getElementById('cant-' + i).value) || 0);
        const precio = precioEfectivo(i);
        document.getElementById('precio-' + i).textContent = 'Q' + precio.toFixed(2);
        const sub = cantidad * precio;
        document.getElementById('sub-' + i).textContent = 'Q' + sub.toFixed(2);
        total += sub;
    });
    document.getElementById('despTotal').textContent = 'Total del Despacho: Q' + total.toFixed(2) + '.';
}

async function confirmarDespacho() {
    const items = [];
    for (let i = 0; i < despacho.datos.items.length; i++) {
        const it = despacho.datos.items[i];
        const cantidad = Number(document.getElementById('cant-' + i).value) || 0;
        const sustituido = document.getElementById('chk-' + i).checked;
        if (cantidad > it.cantidadRecetada) {
            return mostrarAlerta('danger', `La cantidad de ${it.medicamento} no puede exceder lo recetado (${it.cantidadRecetada}).`);
        }
        if (sustituido && cantidad > 0) {
            // FA02: razón obligatoria y medicamento alternativo
            if (!document.getElementById('alt-' + i).value) {
                return mostrarAlerta('danger', `Seleccione el medicamento alternativo para ${it.medicamento}.`);
            }
            if (!document.getElementById('razon-' + i).value.trim()) {
                return mostrarAlerta('danger', `La razón de sustitución de ${it.medicamento} es obligatoria.`);
            }
        }
        items.push({
            idDetalleReceta: it.idDetalleReceta,
            cantidad,
            sustituido: sustituido && cantidad > 0,
            razonSustitucion: sustituido ? document.getElementById('razon-' + i).value.trim() : null,
            idMedicamentoAlternativo: sustituido && document.getElementById('alt-' + i).value
                ? Number(document.getElementById('alt-' + i).value) : null
        });
    }
    if (items.every((x) => x.cantidad === 0)) {
        return mostrarAlerta('danger', 'Debe despachar al menos un medicamento.');
    }

    const btn = document.getElementById('btnConfirmarDespacho');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Registrando...';

    try {
        const respuesta = await fetch(`/api/farmacia/recetas/${despacho.idReceta}/despachar`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ items })
        });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo registrar el despacho.');
        pintarResumen(datos);
    } catch (error) {
        mostrarAlerta('danger', error.message === 'Failed to fetch'
            ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.' : error.message);
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-check me-2"></i>Confirmar Despacho';
    }
}

// Pasos 11-13: resumen del despacho
function pintarResumen(r) {
    document.getElementById('despFormulario').classList.add('d-none');
    document.getElementById('resIdDespacho').textContent = '#' + r.idDespacho;
    document.getElementById('resPaciente').textContent = `${r.paciente} — Receta #${r.idReceta}`;
    document.getElementById('resLineas').innerHTML = r.lineas.map((l) => `
        <div class="resumen-fila">
            <span class="etiqueta">${escaparHtml(l.medicamento)} <span class="small">(${escaparHtml(l.unidad)})</span>
                ${l.sustituyeA ? `<span class="badge badge-soft-info ms-1">Sustituye a ${escaparHtml(l.sustituyeA)}</span>` : ''}</span>
            <span class="valor text-mono">${l.cantidad} × Q${Number(l.precioUnitario).toFixed(2)} = Q${Number(l.subtotal).toFixed(2)}</span>
        </div>`).join('');
    document.getElementById('resTotal').textContent = 'Q' + Number(r.montoTotal).toFixed(2);

    document.getElementById('resSustituciones').innerHTML = r.sustituciones
        .map((s) => `<div class="alert alert-info mb-2"><i class="fas fa-right-left me-2"></i>${escaparHtml(s)}</div>`).join('');
    // FA04: alertas de stock mínimo alcanzado
    document.getElementById('resAlertasMinimo').innerHTML = r.alertasStockMinimo
        .map((a) => `<div class="alert alert-warning mb-2"><i class="fas fa-triangle-exclamation me-2"></i>${escaparHtml(a)}</div>`).join('');

    mostrarAlerta('success', r.mensaje);
    document.getElementById('despResumen').classList.remove('d-none');
}

// FA03
async function cancelarDespacho() {
    const btn = document.getElementById('btnConfirmarCancelacion');
    btn.disabled = true;
    const modal = bootstrap.Modal.getInstance(document.getElementById('modalCancelarDespacho'));
    try {
        const respuesta = await fetch(`/api/farmacia/recetas/${despacho.idReceta}/cancelar`, { method: 'POST' });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo cancelar el despacho.');
        modal.hide();
        document.getElementById('despFormulario').classList.add('d-none');
        mostrarAlerta('info', datos.mensaje);
        setTimeout(() => { window.location.href = '/farmacia'; }, 3500);
    } catch (error) {
        modal.hide();
        mostrarAlerta('danger', error.message);
    } finally {
        btn.disabled = false;
    }
}

function mostrarAlerta(tipo, mensaje) {
    const el = document.getElementById('despAlertaGeneral');
    el.className = `alert alert-${tipo}`;
    el.textContent = mensaje;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
