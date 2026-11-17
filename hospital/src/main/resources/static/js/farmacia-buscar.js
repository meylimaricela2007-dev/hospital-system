// ============================================================
// CU-11, paso 2-3: búsqueda de recetas (farmacia/buscar.html)
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
    buscarRecetas();
    document.getElementById('btnBuscar').addEventListener('click', buscarRecetas);
    document.getElementById('btnLimpiar').addEventListener('click', () => {
        document.getElementById('filtroIdReceta').value = '';
        document.getElementById('filtroIdConsulta').value = '';
        buscarRecetas();
    });
    ['filtroIdReceta', 'filtroIdConsulta'].forEach((id) => {
        const input = document.getElementById(id);
        input.addEventListener('input', () => { input.value = input.value.replace(/\D/g, ''); });
        input.addEventListener('keyup', (e) => { if (e.key === 'Enter') buscarRecetas(); });
    });
});

async function buscarRecetas() {
    const params = new URLSearchParams();
    const idReceta = document.getElementById('filtroIdReceta').value.trim();
    const idConsulta = document.getElementById('filtroIdConsulta').value.trim();
    if (idReceta) params.set('idReceta', idReceta);
    if (idConsulta) params.set('idConsulta', idConsulta);

    const cuerpo = document.getElementById('cuerpoRecetas');
    try {
        const respuesta = await fetch(`/api/farmacia/recetas?${params}`);
        const datos = await respuesta.json().catch(() => ([]));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo buscar la receta.');

        if (datos.length === 0) {
            cuerpo.innerHTML = '<tr><td colspan="7" class="text-center py-4" style="color: var(--color-danger); font-weight:600;">No se encontraron recetas activas con ese criterio.</td></tr>';
            return;
        }
        cuerpo.innerHTML = datos.map((r) => `
            <tr>
                <td class="ps-4 text-mono">#${r.idReceta}</td>
                <td class="text-mono">#${r.idConsulta}</td>
                <td>${escaparHtml(r.paciente)}</td>
                <td class="small">${new Date(r.fechaEmision).toLocaleDateString('es-GT')}</td>
                <td>${r.vigente
                    ? `<span class="badge badge-soft-success">Vigente</span> <span class="small text-muted">hace ${r.diasTranscurridos} día(s)</span>`
                    : `<span class="badge badge-soft-danger">Vencida</span> <span class="small text-muted">hace ${r.diasTranscurridos} día(s)</span>`}</td>
                <td class="small text-muted">${escaparHtml(r.notas || '—')}</td>
                <td class="text-end pe-4">
                    ${r.vigente
                        ? `<a href="/farmacia/despacho/${r.idReceta}" class="btn btn-sm btn-teal"><i class="fas fa-prescription-bottle-medical me-1"></i>Despachar</a>`
                        : `<button class="btn btn-sm btn-outline-danger" disabled title="Receta vencida">Vencida</button>`}
                </td>
            </tr>`).join('');
    } catch (error) {
        const el = document.getElementById('alertaError');
        el.textContent = error.message === 'Failed to fetch' ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.' : error.message;
        el.classList.remove('d-none');
        setTimeout(() => el.classList.add('d-none'), 6000);
    }
}

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
