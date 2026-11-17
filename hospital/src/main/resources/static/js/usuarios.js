// ============================================================
// CU-01: pantalla "Listado de Usuarios" (admin/usuarios.html)
// ============================================================
const estadoLista = { pagina: 0, tamanoPagina: 10, totalPaginas: 1, totalElementos: 0, idAEliminar: null };

document.addEventListener('DOMContentLoaded', () => {
    buscarUsuarios();

    document.getElementById('btnBuscar').addEventListener('click', () => {
        estadoLista.pagina = 0;
        buscarUsuarios();
    });

    // FA02: limpiar búsqueda
    document.getElementById('btnLimpiar').addEventListener('click', () => {
        document.getElementById('inputTextoFiltro').value = '';
        estadoLista.pagina = 0;
        buscarUsuarios();
    });

    document.getElementById('inputTextoFiltro').addEventListener('keyup', (e) => {
        if (e.key === 'Enter') { estadoLista.pagina = 0; buscarUsuarios(); }
    });

    document.getElementById('selectTamanoPagina').addEventListener('change', (e) => {
        estadoLista.tamanoPagina = Number(e.target.value);
        estadoLista.pagina = 0;
        buscarUsuarios();
    });

    document.getElementById('btnPaginaAnterior').addEventListener('click', () => cambiarPagina(-1));
    document.getElementById('btnPaginaSiguiente').addEventListener('click', () => cambiarPagina(1));
    document.getElementById('btnConfirmarEliminar').addEventListener('click', confirmarEliminacion);
});

async function buscarUsuarios() {
    const campo = document.getElementById('selectCampoFiltro').value;
    const valor = document.getElementById('inputTextoFiltro').value.trim();
    const params = new URLSearchParams({ filtro: campo, valor, page: estadoLista.pagina, size: estadoLista.tamanoPagina });

    try {
        const respuesta = await fetch(`/api/usuarios/listar?${params}`);
        if (!respuesta.ok) throw new Error('No se pudo cargar el listado de usuarios.');
        const pagina = await respuesta.json();

        estadoLista.totalPaginas = Math.max(pagina.totalPages, 1);
        estadoLista.totalElementos = pagina.totalElements;
        pintarTabla(pagina.content);

        document.getElementById('textoPagina').textContent = `Página ${pagina.number + 1} de ${estadoLista.totalPaginas}`;
        document.getElementById('textoConteo').textContent = `· ${pagina.totalElements} registro(s)`;
        document.getElementById('btnPaginaAnterior').disabled = pagina.number === 0;
        document.getElementById('btnPaginaSiguiente').disabled = pagina.number + 1 >= estadoLista.totalPaginas;
    } catch (error) {
        mostrarError(error.message);
    }
}

function pintarTabla(filas) {
    const cuerpo = document.getElementById('cuerpoTablaUsuarios');

    // no se encontró información
    if (filas.length === 0) {
        cuerpo.innerHTML = `<tr><td colspan="7" class="text-center py-4" style="color: var(--color-danger); font-weight:600;">
            No se encontraron datos usuarios.</td></tr>`;
        return;
    }

    cuerpo.innerHTML = filas.map((u) => `
        <tr>
            <td class="ps-4 text-mono">${u.idUsuario}</td>
            <td>${escaparHtml(u.nombreCompleto)}</td>
            <td>${escaparHtml(u.correoElectronico)}</td>
            <td>${escaparHtml(u.rolNombre)}</td>
            <td class="text-mono">${escaparHtml(u.nombreUsuario)}</td>
            <td><span class="badge ${u.estadoNombre === 'Activo' ? 'badge-soft-success' : 'badge-soft-danger'}">${u.estadoNombre}</span></td>
            <td class="text-end pe-4">
                <div class="dropdown">
                    <button class="btn btn-sm btn-outline-teal" data-bs-toggle="dropdown" aria-expanded="false">
                        <i class="fas fa-ellipsis-vertical"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                        <li><a class="dropdown-item" href="/admin/usuarios/editar/${u.idUsuario}">
                            <i class="fas fa-pen me-2"></i>Editar</a></li>
                        <li><a class="dropdown-item" href="#" style="color: var(--color-accent-600);"
                               onclick="abrirModalEliminar(${u.idUsuario}, '${escaparHtml(u.nombreUsuario)}'); return false;">
                            <i class="fas fa-trash me-2"></i>Eliminar</a></li>
                    </ul>
                </div>
            </td>
        </tr>
    `).join('');
}

function cambiarPagina(delta) {
    const nueva = estadoLista.pagina + delta;
    if (nueva < 0 || nueva >= estadoLista.totalPaginas) return;
    estadoLista.pagina = nueva;
    buscarUsuarios();
}

// FA05: abrir el diálogo modal de confirmación de eliminación
function abrirModalEliminar(idUsuario, nombreUsuario) {
    estadoLista.idAEliminar = idUsuario;
    document.getElementById('nombreUsuarioAEliminar').textContent = nombreUsuario;
    new bootstrap.Modal(document.getElementById('modalConfirmarEliminacion')).show();
}

async function confirmarEliminacion() {
    const btn = document.getElementById('btnConfirmarEliminar');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Eliminando...';

    try {
        const respuesta = await fetch(`/api/usuarios/${estadoLista.idAEliminar}`, { method: 'DELETE' });
        const datos = await respuesta.json().catch(() => ({}));
        // CORREGIDO: el backend (GlobalExceptionHandler) devuelve el error
        // de negocio bajo la clave "message", no "mensaje" — con solo
        // "datos.mensaje" el mensaje real nunca se mostraba y el usuario
        // siempre veía el texto genérico de abajo.
        if (!respuesta.ok) throw new Error(datos.mensaje || datos.message || 'No se pudo eliminar el usuario.');

        bootstrap.Modal.getInstance(document.getElementById('modalConfirmarEliminacion')).hide();
        buscarUsuarios(); // el sistema actualiza la tabla automáticamente
    } catch (error) {
        mostrarError(error.message);
        bootstrap.Modal.getInstance(document.getElementById('modalConfirmarEliminacion')).hide();
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-trash me-2"></i>Eliminar';
    }
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
