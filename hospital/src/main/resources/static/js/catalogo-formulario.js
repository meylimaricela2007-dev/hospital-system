// ============================================================
// CU-14: formulario genérico de catálogo (admin/catalogo-formulario.html)
// Los campos se dibujan con lo que declara el servidor (/meta).
// ============================================================
const clave = document.getElementById('claveCatalogo').value;
const idRegistro = document.getElementById('idRegistro').value; // vacío al crear
const esEdicion = idRegistro !== '';
let meta = null;

document.addEventListener('DOMContentLoaded', async () => {
    const urlListado = `/admin/catalogos/${clave}`;
    document.getElementById('enlaceVolver').href = urlListado;
    document.getElementById('btnCancelar').href = urlListado; // FA04: cancelar vuelve al listado
    document.getElementById('formCatalogo').addEventListener('submit', guardar);
    await cargar();
});

async function cargar() {
    try {
        const respuestaMeta = await fetch(`/api/admin/catalogos/${clave}/meta`);
        meta = await respuestaMeta.json();
        if (!respuestaMeta.ok) throw new Error(meta.message || 'No se pudo cargar el catálogo.');

        let registro = null;
        if (esEdicion) {
            const respuesta = await fetch(`/api/admin/catalogos/${clave}/${idRegistro}`);
            registro = await respuesta.json();
            if (!respuesta.ok) throw new Error(registro.message || 'No se pudo cargar el registro.');
        }

        document.getElementById('tituloFormulario').textContent =
            (esEdicion ? 'Editar ' : 'Crear ') + meta.singular.toLowerCase();
        document.getElementById('textoGuardar').textContent = esEdicion ? 'Actualizar' : 'Crear';
        dibujarCampos(registro);
    } catch (error) {
        mostrarError(error.message);
    }
}

function dibujarCampos(registro) {
    const html = meta.campos.map((c) => {
        const valor = registro ? registro[c.key] : '';
        const bloqueado = esEdicion && c.bloqueadoEdicion;
        const etiqueta = c.label + (c.requerido ? ' *' : '');
        const comunes = `id="campo_${c.key}" ${bloqueado ? 'disabled' : ''}`;
        let control;

        switch (c.tipo) {
            case 'textarea':
                control = `<textarea class="form-control" ${comunes} rows="3" ${c.maxLength ? `maxlength="${c.maxLength}"` : ''}>${escaparHtml(valor ?? '')}</textarea>`;
                break;
            case 'select':
                control = `<select class="form-select" ${comunes}><option value="">-- Seleccione --</option>` +
                    c.opciones.map((o) => `<option value="${o.id}" ${String(o.id) === String(valor) ? 'selected' : ''}>${escaparHtml(o.nombre)}</option>`).join('') +
                    '</select>';
                break;
            case 'number':
                control = `<input type="number" class="form-control text-mono" ${comunes} min="${c.min ?? ''}" step="${c.step ?? '1'}" value="${valor ?? ''}">`;
                break;
            case 'checkbox':
                return `
                <div class="mb-3">
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" ${comunes} ${valor ? 'checked' : ''}>
                        <label class="form-check-label" for="campo_${c.key}">${escaparHtml(c.label)}</label>
                    </div>
                    ${c.ayuda ? `<div class="form-text">${escaparHtml(c.ayuda)}</div>` : ''}
                </div>`;
            default: // text, tel
                control = `<input type="${c.tipo === 'tel' ? 'tel' : 'text'}" class="form-control" ${comunes} ${c.maxLength ? `maxlength="${c.maxLength}"` : ''} value="${escaparAtributo(valor ?? '')}">`;
        }
        return `
        <div class="mb-3">
            <label class="form-label" for="campo_${c.key}">${escaparHtml(etiqueta)}</label>
            ${control}
            <div class="invalid-feedback" data-error-for="${c.key}"></div>
            ${c.ayuda ? `<div class="form-text">${escaparHtml(c.ayuda)}</div>` : ''}
        </div>`;
    }).join('');

    // RN-CU15-01: Estado obligatorio, valores Activo / Inactivo, por defecto Activo
    const estado = meta.tieneEstado ? `
        <div class="mb-3">
            <label class="form-label" for="campo_estado">Estado *</label>
            <select class="form-select" id="campo_estado">
                <option value="true" ${!registro || registro.estado ? 'selected' : ''}>Activo</option>
                <option value="false" ${registro && !registro.estado ? 'selected' : ''}>Inactivo</option>
            </select>
        </div>` : '';

    document.getElementById('contenedorCampos').innerHTML = html + estado;
}

function recogerDatos() {
    const datos = {};
    meta.campos.forEach((c) => {
        if (esEdicion && c.bloqueadoEdicion) return; // no se envían los campos bloqueados
        const el = document.getElementById(`campo_${c.key}`);
        if (c.tipo === 'checkbox') datos[c.key] = el.checked;
        else datos[c.key] = el.value.trim() === '' ? null : el.value.trim();
    });
    if (meta.tieneEstado) datos.estado = document.getElementById('campo_estado').value === 'true';
    return datos;
}

async function guardar(evento) {
    evento.preventDefault();
    ocultarMensajes();
    limpiarErrores();

    const btn = document.getElementById('btnGuardar');
    btn.disabled = true;
    const textoOriginal = btn.innerHTML;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

    try {
        const respuesta = await fetch(esEdicion ? `/api/admin/catalogos/${clave}/${idRegistro}` : `/api/admin/catalogos/${clave}`, {
            method: esEdicion ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(recogerDatos())
        });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) {
            if (datos.errors) pintarErrores(datos.errors); // FA05: campos en rojo
            throw new Error(datos.message || 'No se pudo guardar el registro.');
        }

        mostrarExito(datos.mensaje);
        setTimeout(() => { window.location.href = `/admin/catalogos/${clave}?ok=${encodeURIComponent(datos.mensaje)}`; }, 1200);
    } catch (error) {
        mostrarError(error.message);
        btn.disabled = false;
        btn.innerHTML = textoOriginal;
    }
}

function pintarErrores(errores) {
    Object.entries(errores).forEach(([campo, mensaje]) => {
        const input = document.getElementById(`campo_${campo}`);
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

function escaparAtributo(texto) {
    return escaparHtml(texto).replace(/"/g, '&quot;');
}
