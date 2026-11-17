// ============================================================
// CU-11: panel de farmacia (farmacia/panel.html)
// ============================================================
document.addEventListener('DOMContentLoaded', cargarRecientes);

async function cargarRecientes() {
    const cuerpo = document.getElementById('cuerpoRecientes');
    try {
        const respuesta = await fetch('/api/farmacia/despachos-recientes');
        const datos = await respuesta.json().catch(() => ([]));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo cargar el panel de farmacia.');

        if (datos.length === 0) {
            cuerpo.innerHTML = '<tr><td colspan="6" class="text-center py-4 text-muted">Todavía no hay despachos en esta sucursal.</td></tr>';
            return;
        }
        cuerpo.innerHTML = datos.map((d) => `
            <tr>
                <td class="ps-4 text-mono">#${d.idDespacho}</td>
                <td class="text-mono">#${d.idReceta}</td>
                <td>${escaparHtml(d.paciente)}</td>
                <td class="text-mono">Q${Number(d.montoTotal).toFixed(2)}</td>
                <td><span class="badge ${d.estado === 'Completado' ? 'badge-soft-success' : 'badge-soft-warning'}">${escaparHtml(d.estado)}</span></td>
                <td class="pe-4 small text-muted">${formatearFecha(d.fechaDespacho)}</td>
            </tr>`).join('');
    } catch (error) {
        const el = document.getElementById('alertaError');
        el.textContent = error.message === 'Failed to fetch' ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.' : error.message;
        el.classList.remove('d-none');
    }
}

function formatearFecha(iso) {
    return iso ? new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' }) : '—';
}

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
