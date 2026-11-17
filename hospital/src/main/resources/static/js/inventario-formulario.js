// ============================================================
// CU-15: formulario "Nuevo Movimiento" (inventario/movimiento-formulario.html)
// ============================================================
const tiposPorId = {};
let infoInventario = null; // { existeInventario, stockActual, stockMinimo }

// RN-CU13-01: etiquetas dinámicas según el tipo
const CONFIG_TIPO = {
    'Compra':     { referencia: 'Factura',    motivo: 'Notas (opcional)',       motivoObligatorio: false, costo: true,  verMotivo: true },
    'Devolución': { referencia: 'Devolución', motivo: 'Motivo de Devolución *', motivoObligatorio: true,  costo: false, verMotivo: true },
    'Venta':      { referencia: 'Venta',      motivo: '',                       motivoObligatorio: false, costo: false, verMotivo: false },
    'Reclamo':    { referencia: 'Reclamo',    motivo: 'Motivo del Reclamo *',   motivoObligatorio: true,  costo: false, verMotivo: true },
    'Ajuste+':    { referencia: 'Referencia', motivo: 'Notas/Justificación *',  motivoObligatorio: true,  costo: false, verMotivo: true },
    'Ajuste-':    { referencia: 'Referencia', motivo: 'Notas/Justificación *',  motivoObligatorio: true,  costo: false, verMotivo: true }
};

document.addEventListener('DOMContentLoaded', async () => {
    await cargarCatalogos();
    document.getElementById('idMedicamento').addEventListener('change', actualizarPanel);
    document.getElementById('idSucursal').addEventListener('change', actualizarPanel);
    document.getElementById('idTipoMovimiento').addEventListener('change', () => { aplicarTipo(); calcularProyeccion(); });
    document.getElementById('cantidad').addEventListener('input', calcularProyeccion);
    document.getElementById('formMovimiento').addEventListener('submit', registrar);
});

async function cargarCatalogos() {
    try {
        const respuesta = await fetch('/api/inventario/catalogos');
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudieron cargar los catálogos.');

        document.getElementById('idMedicamento').innerHTML =
            '<option value="">-- Seleccione un medicamento --</option>' +
            datos.medicamentos.map((m) => `<option value="${m.id}">${escaparHtml(m.nombre)}</option>`).join('');

        const selSede = document.getElementById('idSucursal');
        selSede.innerHTML = '<option value="">-- Seleccione una sucursal --</option>' +
            datos.sucursales.map((s) => `<option value="${s.id}">${escaparHtml(s.nombre)}</option>`).join('');
        if (datos.sucursalFija && datos.sucursales.length === 1) {
            selSede.value = datos.sucursales[0].id;
            selSede.disabled = true; // el personal de farmacia solo opera en su sucursal
        }

        datos.tipos.forEach((t) => { tiposPorId[t.id] = t; });
        document.getElementById('idTipoMovimiento').innerHTML =
            '<option value="">-- Seleccione el tipo --</option>' +
            datos.tipos.map((t) => `<option value="${t.id}">${escaparHtml(t.nombre)}</option>`).join('');
    } catch (error) {
        mostrarError(error.message);
    }
}

function tipoActual() {
    return tiposPorId[document.getElementById('idTipoMovimiento').value] || null;
}

function aplicarTipo() {
    const tipo = tipoActual();
    const cfg = tipo ? CONFIG_TIPO[tipo.nombre] : null;

    document.getElementById('grupoCosto').classList.toggle('d-none', !(cfg && cfg.costo));
    document.getElementById('grupoReferencia').classList.toggle('d-none', !cfg);
    document.getElementById('grupoMotivo').classList.toggle('d-none', !(cfg && cfg.verMotivo));
    if (cfg) {
        document.getElementById('etiquetaReferencia').textContent = `Número de ${cfg.referencia}`.replace('Número de Referencia', 'Número de referencia');
        document.getElementById('etiquetaMotivo').textContent = cfg.motivo;
    }
}

async function actualizarPanel() {
    const idMed = document.getElementById('idMedicamento').value;
    const idSede = document.getElementById('idSucursal').value;
    const panel = document.getElementById('panelInventario');
    infoInventario = null;

    if (!idMed || !idSede) { panel.classList.add('d-none'); return; }

    try {
        const respuesta = await fetch(`/api/inventario/info?idMedicamento=${idMed}&idSucursal=${idSede}`);
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message);
        infoInventario = datos;
        panel.classList.remove('d-none');
        document.getElementById('infoStockActual').textContent = datos.stockActual;
        document.getElementById('infoStockMinimo').textContent = datos.stockMinimo;
        document.getElementById('infoSinInventario').classList.toggle('d-none', datos.existeInventario);
        calcularProyeccion();
    } catch (error) {
        panel.classList.add('d-none');
        mostrarError(error.message);
    }
}

// FA05: alerta preventiva de stock mínimo mientras se edita el formulario
function calcularProyeccion() {
    const adv = document.getElementById('alertaAdvertencia');
    adv.classList.add('d-none');
    if (!infoInventario) return;

    const tipo = tipoActual();
    const cantidad = parseInt(document.getElementById('cantidad').value, 10);
    const elProy = document.getElementById('infoStockProyectado');
    const elOp = document.getElementById('infoOperacion');

    if (!tipo) { elProy.textContent = '—'; elOp.textContent = '—'; return; }
    elOp.innerHTML = tipo.entrada
        ? '<span class="badge badge-soft-success">Entrada</span>'
        : '<span class="badge badge-soft-warning">Salida</span>';
    if (!Number.isInteger(cantidad) || cantidad < 1) { elProy.textContent = '—'; return; }

    const proyectado = infoInventario.stockActual + (tipo.entrada ? cantidad : -cantidad);
    elProy.textContent = proyectado;
    elProy.style.color = proyectado < 0 ? 'var(--color-danger)' : '';

    if (!tipo.entrada && proyectado >= 0 && infoInventario.stockMinimo > 0 && proyectado <= infoInventario.stockMinimo) {
        const nombre = document.getElementById('idMedicamento').selectedOptions[0].textContent;
        adv.textContent = `${nombre}: Stock bajo — disponible: ${proyectado} (mínimo: ${infoInventario.stockMinimo})`;
        adv.classList.remove('d-none');
    }
}

function validar() {
    limpiarErrores();
    const errores = {};
    const tipo = tipoActual();
    const cfg = tipo ? CONFIG_TIPO[tipo.nombre] : null;
    const cantidad = document.getElementById('cantidad').value.trim();

    if (!document.getElementById('idMedicamento').value) errores.idMedicamento = 'Debe seleccionar un medicamento.';
    if (!document.getElementById('idSucursal').value) errores.idSucursal = 'Debe seleccionar una sucursal.';
    if (!tipo) errores.idTipoMovimiento = 'Debe seleccionar el tipo de movimiento.';
    if (!/^[1-9]\d*$/.test(cantidad)) errores.cantidad = 'La cantidad debe ser un número entero positivo.';

    if (cfg && cfg.costo) {
        const costo = document.getElementById('costoUnitario').value.trim();
        if (costo === '') errores.costoUnitario = 'El costo unitario es obligatorio para compras';
        else if (Number(costo) <= 0) errores.costoUnitario = 'El costo unitario debe ser mayor a 0';
        else if (!/^\d+(\.\d{1,2})?$/.test(costo)) errores.costoUnitario = 'El costo unitario debe tener máximo 2 decimales';
    }
    if (cfg && cfg.motivoObligatorio) {
        const motivo = document.getElementById('motivo').value.trim();
        if (motivo.length < 10 || motivo.length > 500) errores.motivo = 'El motivo debe contener entre 10 y 500 caracteres.';
    }
    return errores;
}

async function registrar(evento) {
    evento.preventDefault();
    ocultarMensajes();

    const errores = validar();
    if (Object.keys(errores).length > 0) { pintarErrores(errores); return; }

    const tipo = tipoActual();
    const cfg = CONFIG_TIPO[tipo.nombre];
    const cuerpo = {
        idMedicamento: Number(document.getElementById('idMedicamento').value),
        idSucursal: Number(document.getElementById('idSucursal').value),
        idTipoMovimiento: tipo ? Number(tipo.id) : null,
        cantidad: Number(document.getElementById('cantidad').value),
        costoUnitario: cfg.costo ? document.getElementById('costoUnitario').value.trim() : null,
        numeroReferencia: document.getElementById('numeroReferencia').value.trim() || null,
        motivo: cfg.verMotivo ? document.getElementById('motivo').value.trim() : null
    };

    const btn = document.getElementById('btnRegistrar');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Registrando...';

    try {
        const respuesta = await fetch('/api/inventario/movimientos', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(cuerpo)
        });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) {
            if (datos.errors) pintarErrores(datos.errors);
            throw new Error(datos.message || 'No se pudo registrar el movimiento.');
        }

        mostrarExito(datos.mensaje);
        if (datos.alerta) {
            const adv = document.getElementById('alertaAdvertencia');
            adv.textContent = datos.alerta;
            adv.classList.remove('d-none');
        }
        setTimeout(() => { window.location.href = '/inventario'; }, datos.alerta ? 3500 : 1800);
    } catch (error) {
        mostrarError(error.message);
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-check me-2"></i>Registrar Movimiento';
    }
}

function pintarErrores(errores) {
    Object.entries(errores).forEach(([campo, mensaje]) => {
        const input = document.getElementById(campo);
        const aviso = document.querySelector(`[data-error-for="${campo}"]`);
        if (input) input.classList.add('is-invalid');
        if (aviso) aviso.textContent = mensaje;
    });
}

function limpiarErrores() {
    document.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
    document.querySelectorAll('[data-error-for]').forEach((el) => { el.textContent = ''; });
}

function mostrarExito(mensaje) {
    const el = document.getElementById('alertaExito');
    el.textContent = mensaje;
    el.classList.remove('d-none');
}

function mostrarError(mensaje) {
    const el = document.getElementById('alertaError');
    el.textContent = mensaje;
    el.classList.remove('d-none');
}

function ocultarMensajes() {
    document.getElementById('alertaExito').classList.add('d-none');
    document.getElementById('alertaError').classList.add('d-none');
}

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
