// ============================================================
// CU-09: tabla "Órdenes de Laboratorio" (laboratorio/ordenes.html)
// ============================================================
const estadoLab = { pagina: 0, tamanoPagina: 10, totalPaginas: 1 };

document.addEventListener('DOMContentLoaded', () => {
    cargarOrdenes();

    document.getElementById('labBtnBuscar').addEventListener('click', () => {
        estadoLab.pagina = 0;
        cargarOrdenes();
    });
    document.getElementById('labBtnLimpiar').addEventListener('click', () => {
        document.getElementById('labFiltroEstado').value = '';
        document.getElementById('labFiltroPaciente').value = '';
        document.getElementById('labFiltroMedico').value = '';
        estadoLab.pagina = 0;
        cargarOrdenes();
    });
    document.getElementById('labFiltroEstado').addEventListener('change', () => {
        estadoLab.pagina = 0;
        cargarOrdenes();
    });
    ['labFiltroPaciente', 'labFiltroMedico'].forEach((id) => {
        document.getElementById(id).addEventListener('keyup', (e) => {
            if (e.key === 'Enter') { estadoLab.pagina = 0; cargarOrdenes(); }
        });
    });
    document.getElementById('labTamanoPagina').addEventListener('change', (e) => {
        estadoLab.tamanoPagina = Number(e.target.value);
        estadoLab.pagina = 0;
        cargarOrdenes();
    });
    document.getElementById('labPaginaAnterior').addEventListener('click', () => cambiarPaginaLab(-1));
    document.getElementById('labPaginaSiguiente').addEventListener('click', () => cambiarPaginaLab(1));
});

async function cargarOrdenes() {
    const params = new URLSearchParams({
        estado: document.getElementById('labFiltroEstado').value,
        paciente: document.getElementById('labFiltroPaciente').value.trim(),
        medico: document.getElementById('labFiltroMedico').value.trim(),
        page: estadoLab.pagina,
        size: estadoLab.tamanoPagina,
    });

    ocultarErrorLab();
    try {
        const respuesta = await fetch(`/api/laboratorio/ordenes?${params}`);
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo cargar el listado de órdenes.');

        estadoLab.totalPaginas = Math.max(datos.totalPages, 1);
        pintarTablaOrdenes(datos.content);

        document.getElementById('labTextoPagina').textContent = `Página ${datos.number + 1} de ${estadoLab.totalPaginas}`;
        document.getElementById('labTextoConteo').textContent = `· ${datos.totalElements} registro(s)`;
        document.getElementById('labPaginaAnterior').disabled = datos.number === 0;
        document.getElementById('labPaginaSiguiente').disabled = datos.number + 1 >= estadoLab.totalPaginas;
    } catch (error) {
        document.getElementById('labCuerpoTabla').innerHTML = '';
        mostrarErrorLab(error.message === 'Failed to fetch'
            ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.'
            : error.message);
    }
}

function pintarTablaOrdenes(filas) {
    const cuerpo = document.getElementById('labCuerpoTabla');

    if (filas.length === 0) {
        cuerpo.innerHTML = `<tr><td colspan="8" class="text-center py-4" style="color: var(--color-danger); font-weight:600;">
            No se encontraron resultados.</td></tr>`;
        return;
    }

    cuerpo.innerHTML = filas.map((o) => `
        <tr>
            <td class="ps-4 text-mono">${escaparHtmlLab(o.numeroOrden)}</td>
            <td>
                ${escaparHtmlLab(o.nombrePaciente)}
                <div class="small text-muted text-mono">${escaparHtmlLab(o.dpiPaciente || '')}</div>
            </td>
            <td>${escaparHtmlLab(o.nombreMedico)}</td>
            <td class="text-center text-mono">${o.examenesPublicados}/${o.cantidadExamenes}</td>
            <td class="text-end text-mono">Q${Number(o.montoTotal).toFixed(2)}</td>
            <td class="small">${formatearFechaLab(o.fechaCreacion)}</td>
            <td>
                <span class="badge ${badgeEstadoOrden(o.estado)}">${escaparHtmlLab(o.estado)}</span>
                ${o.esExterna ? '<span class="badge badge-soft-info ms-1">Externa</span>' : ''}
            </td>
            <td class="text-end pe-4">
                <a href="/laboratorio/orden/${o.idOrden}" class="btn btn-sm btn-outline-teal">
                    <i class="fas fa-eye me-1"></i>Ver
                </a>
            </td>
        </tr>
    `).join('');
}

function cambiarPaginaLab(delta) {
    const nueva = estadoLab.pagina + delta;
    if (nueva < 0 || nueva >= estadoLab.totalPaginas) return;
    estadoLab.pagina = nueva;
    cargarOrdenes();
}

function badgeEstadoOrden(estado) {
    if (estado === 'Completada') return 'badge-soft-success';
    if (estado === 'Pendiente') return 'badge-soft-warning';
    if (estado === 'Cancelada') return 'badge-soft-danger';
    return 'badge-soft-info'; // En Proceso
}

function formatearFechaLab(fecha) {
    if (!fecha) return '';
    return new Date(fecha).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' });
}

function mostrarErrorLab(mensaje) {
    const alerta = document.getElementById('labAlertaError');
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');
}
function ocultarErrorLab() {
    document.getElementById('labAlertaError').classList.add('d-none');
}

function escaparHtmlLab(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
