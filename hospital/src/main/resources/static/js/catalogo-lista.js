// ============================================================
// CU-14: listado genérico de un catálogo (admin/catalogo-lista.html)
// La tabla se dibuja con las columnas que declara el servidor (/meta).
// ============================================================
const clave = document.getElementById('claveCatalogo').value;
const estadoLista = { pagina: 0, tamanoPagina: 10, totalPaginas: 1, meta: null, idAEliminar: null };

document.addEventListener('DOMContentLoaded', async () => {
    await cargarMeta();
    mostrarMensajePendiente();
    buscar();

    document.getElementById('btnBuscar').addEventListener('click', () => { estadoLista.pagina = 0; buscar(); });
    document.getElementById('btnLimpiar').addEventListener('click', () => {
        document.getElementById('inputFiltroTexto').value = '';
        document.getElementById('selectFiltroEstado').value = '';
        estadoLista.pagina = 0;
        buscar();
    });
    document.getElementById('inputFiltroTexto').addEventListener('keyup', (e) => {
        if (e.key === 'Enter') { estadoLista.pagina = 0; buscar(); }
    });
    document.getElementById('selectTamanoPagina').addEventListener('change', (e) => {
        estadoLista.tamanoPagina = Number(e.target.value);
        estadoLista.pagina = 0;
        buscar();
    });
    document.getElementById('btnPaginaAnterior').addEventListener('click', () => cambiarPagina(-1));
    document.getElementById('btnPaginaSiguiente').addEventListener('click', () => cambiarPagina(1));
    document.getElementById('btnConfirmarEliminar').addEventListener('click', confirmarEliminacion);
});

async function cargarMeta() {
    try {
        const respuesta = await fetch(`/api/admin/catalogos/${clave}/meta`);
        const meta = await respuesta.json();
        if (!respuesta.ok) throw new Error(meta.message || 'No se pudo cargar el catálogo.');
        estadoLista.meta = meta;

        document.getElementById('tituloCatalogo').textContent = meta.titulo;
        document.getElementById('btnCrear').href = `/admin/catalogos/${clave}/crear`;
        document.getElementById('grupoFiltroEstado').classList.toggle('d-none', !meta.tieneEstado);
        document.getElementById('filaEncabezado').innerHTML =
            meta.columnas.map((c, i) => `<th class="${i === 0 ? 'ps-4' : ''}">${escaparHtml(c.label)}</th>`).join('') +
            '<th class="text-end pe-4">Acciones</th>';
    } catch (error) {
        mostrarError(error.message);
    }
}

async function buscar() {
    if (!estadoLista.meta) return;
    const params = new URLSearchParams({ page: estadoLista.pagina, size: estadoLista.tamanoPagina });
    const texto = document.getElementById('inputFiltroTexto').value.trim();
    const estado = document.getElementById('selectFiltroEstado').value;
    if (texto) params.set('texto', texto);
    if (estado) params.set('estado', estado);

    try {
        const respuesta = await fetch(`/api/admin/catalogos/${clave}?${params}`);
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo cargar el listado.');

        estadoLista.totalPaginas = Math.max(datos.totalPages, 1);
        pintarTabla(datos.content);
        document.getElementById('textoPagina').textContent = `Página ${datos.number + 1} de ${estadoLista.totalPaginas}`;
        document.getElementById('textoConteo').textContent = `· ${datos.totalElements} registro(s)`;
        document.getElementById('btnPaginaAnterior').disabled = datos.number === 0;
        document.getElementById('btnPaginaSiguiente').disabled = datos.number + 1 >= estadoLista.totalPaginas;
    } catch (error) {
        mostrarError(error.message);
    }
}

function pintarTabla(filas) {
    const meta = estadoLista.meta;
    const cuerpo = document.getElementById('cuerpoTabla');
    const totalColumnas = meta.columnas.length + 1;

    // FA01
    if (filas.length === 0) {
        cuerpo.innerHTML = `<tr><td colspan="${totalColumnas}" class="text-center py-4 text-muted">No se encontraron resultados.</td></tr>`;
        return;
    }

    cuerpo.innerHTML = filas.map((fila) => {
        const celdas = meta.columnas.map((c, i) => `<td class="${i === 0 ? 'ps-4' : ''}">${formatearCelda(c, fila)}</td>`).join('');
        const activo = !meta.tieneEstado || fila.estado;
        const puedeEliminar = meta.puedeEliminar && activo;
        return `
        <tr>
            ${celdas}
            <td class="text-end pe-4">
                <div class="dropdown">
                    <button class="btn btn-sm btn-outline-teal" data-bs-toggle="dropdown" aria-expanded="false">
                        <i class="fas fa-ellipsis-vertical"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                        <li><a class="dropdown-item" href="/admin/catalogos/${clave}/editar/${fila.id}">
                            <i class="fas fa-pen me-2"></i>Editar</a></li>
                        ${puedeEliminar ? `
                        <li><a class="dropdown-item" href="#" style="color: var(--color-accent-600);"
                               onclick="abrirModalEliminar(${fila.id}, this.dataset.etiqueta); return false;"
                               data-etiqueta="${escaparAtributo(fila.etiqueta)}">
                            <i class="fas fa-trash me-2"></i>Eliminar</a></li>` : ''}
                    </ul>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function formatearCelda(columna, fila) {
    const valor = fila[columna.key];
    switch (columna.tipo) {
        case 'mono':
            return `<span class="text-mono">${escaparHtml(valor ?? '—')}</span>`;
        case 'moneda':
            return `<span class="text-mono">Q${Number(valor).toFixed(2)}</span>`;
        case 'estado':
            return `<span class="badge ${valor ? 'badge-soft-success' : 'badge-soft-danger'}">${valor ? 'Activo' : 'Inactivo'}</span>`;
        case 'controlado':
            return valor ? '<span class="badge badge-soft-warning"><i class="fas fa-shield-halved me-1"></i>Controlado</span>' : '<span class="text-muted">—</span>';
        case 'alerta':
            return valor ? '<span class="badge badge-soft-danger"><i class="fas fa-triangle-exclamation me-1"></i>Stock bajo</span>' : '<span class="text-muted">—</span>';
        default:
            return escaparHtml(valor ?? '—');
    }
}

function cambiarPagina(delta) {
    const nueva = estadoLista.pagina + delta;
    if (nueva < 0 || nueva >= estadoLista.totalPaginas) return;
    estadoLista.pagina = nueva;
    buscar();
}

function abrirModalEliminar(id, etiqueta) {
    estadoLista.idAEliminar = id;
    document.getElementById('textoConfirmacion').textContent =
        `¿Está seguro que desea eliminar el registro ${etiqueta}? Esta acción no se puede deshacer.`;
    new bootstrap.Modal(document.getElementById('modalConfirmarEliminacion')).show();
}

async function confirmarEliminacion() {
    const btn = document.getElementById('btnConfirmarEliminar');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Eliminando...';
    const modal = bootstrap.Modal.getInstance(document.getElementById('modalConfirmarEliminacion'));

    try {
        const respuesta = await fetch(`/api/admin/catalogos/${clave}/${estadoLista.idAEliminar}`, { method: 'DELETE' });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo eliminar el registro.');
        modal.hide();
        mostrarExito(datos.mensaje);
        buscar();
    } catch (error) {
        modal.hide();
        mostrarError(error.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-trash me-2"></i>Eliminar';
    }
}

// Mensaje que deja el formulario al guardar (?ok=...)
function mostrarMensajePendiente() {
    const params = new URLSearchParams(window.location.search);
    const ok = params.get('ok');
    if (ok) {
        mostrarExito(ok);
        window.history.replaceState({}, '', window.location.pathname);
    }
}

function mostrarExito(mensaje) {
    const el = document.getElementById('alertaExito');
    el.textContent = mensaje;
    el.classList.remove('d-none');
    setTimeout(() => el.classList.add('d-none'), 6000);
}

function mostrarError(mensaje) {
    const el = document.getElementById('alertaError');
    el.textContent = mensaje;
    el.classList.remove('d-none');
    setTimeout(() => el.classList.add('d-none'), 6000);
}

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}

function escaparAtributo(texto) {
    return escaparHtml(texto).replace(/"/g, '&quot;');
}
