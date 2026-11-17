// ============================================================
// CU-13: listado "Especialidades por Sede" (admin/sede-especialidad.html)
// ============================================================
const estadoLista = { pagina: 0, tamanoPagina: 10, totalPaginas: 1, idAEliminar: null };

document.addEventListener('DOMContentLoaded', () => {
    buscar();

    document.getElementById('btnBuscar').addEventListener('click', () => { estadoLista.pagina = 0; buscar(); });
    document.getElementById('btnLimpiar').addEventListener('click', () => {
        document.getElementById('inputFiltroId').value = '';
        estadoLista.pagina = 0;
        buscar();
    });
    document.getElementById('inputFiltroId').addEventListener('keyup', (e) => {
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

async function buscar() {
    const id = document.getElementById('inputFiltroId').value.trim();
    const params = new URLSearchParams({ page: estadoLista.pagina, size: estadoLista.tamanoPagina });
    if (id) params.set('id', id);

    try {
        const respuesta = await fetch(`/api/sede-especialidad?${params}`);
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
    const cuerpo = document.getElementById('cuerpoTabla');

    // FA01: no hay asignaciones
    if (filas.length === 0) {
        cuerpo.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">No se encontraron resultados.</td></tr>`;
        return;
    }

    cuerpo.innerHTML = filas.map((f) => `
        <tr>
            <td class="ps-4 text-mono">${f.idSedeEspecialidad}</td>
            <td>${escaparHtml(f.sedeNombre)}</td>
            <td>${escaparHtml(f.especialidadNombre)}</td>
            <td><span class="badge ${f.estado ? 'badge-soft-success' : 'badge-soft-danger'}">${f.estadoNombre}</span></td>
            <td class="text-end pe-4">
                ${f.estado ? `
                <div class="dropdown">
                    <button class="btn btn-sm btn-outline-teal" data-bs-toggle="dropdown" aria-expanded="false">
                        <i class="fas fa-ellipsis-vertical"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                        <li><a class="dropdown-item" href="#" style="color: var(--color-accent-600);"
                               onclick="abrirModalEliminar(${f.idSedeEspecialidad}, '${escaparHtml(f.especialidadNombre)}', '${escaparHtml(f.sedeNombre)}'); return false;">
                            <i class="fas fa-trash me-2"></i>Eliminar</a></li>
                    </ul>
                </div>` : '<span class="text-muted small">—</span>'}
            </td>
        </tr>
    `).join('');
}

function cambiarPagina(delta) {
    const nueva = estadoLista.pagina + delta;
    if (nueva < 0 || nueva >= estadoLista.totalPaginas) return;
    estadoLista.pagina = nueva;
    buscar();
}

function abrirModalEliminar(id, especialidad, sede) {
    estadoLista.idAEliminar = id;
    document.getElementById('textoAsignacionAEliminar').textContent = `${especialidad} — ${sede}`;
    new bootstrap.Modal(document.getElementById('modalConfirmarEliminacion')).show();
}

async function confirmarEliminacion() {
    const btn = document.getElementById('btnConfirmarEliminar');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Eliminando...';
    const modal = bootstrap.Modal.getInstance(document.getElementById('modalConfirmarEliminacion'));

    try {
        const respuesta = await fetch(`/api/sede-especialidad/${estadoLista.idAEliminar}`, { method: 'DELETE' });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo eliminar la asignación.');

        modal.hide();
        mostrarExito(datos.mensaje);
        buscar(); // la tabla se refresca automáticamente
    } catch (error) {
        modal.hide();
        mostrarError(error.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-trash me-2"></i>Eliminar';
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
