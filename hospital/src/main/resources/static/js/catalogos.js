// ============================================================
// CU-14: índice de catálogos (admin/catalogos.html)
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
    try {
        const respuesta = await fetch('/api/admin/catalogos');
        const catalogos = await respuesta.json();
        if (!respuesta.ok) throw new Error(catalogos.message || 'No se pudieron cargar los catálogos.');

        // Sucursal-Especialidad tiene su propio módulo (CU-13)
        const tarjetas = catalogos.map((c) => ({ href: `/admin/catalogos/${c.clave}`, titulo: c.titulo, descripcion: c.descripcion, icono: c.icono }));
        tarjetas.push({
            href: '/admin/sede-especialidad', titulo: 'Especialidades por Sede',
            descripcion: 'Qué especialidades ofrece cada sucursal (solo asignar y quitar).', icono: 'fa-hospital'
        });

        document.getElementById('contenedorCatalogos').innerHTML = tarjetas.map((t) => `
            <div class="col-md-6 col-lg-4">
                <div class="hh-card h-100">
                    <div class="icon-badge"><i class="fas ${t.icono}"></i></div>
                    <h3>${escaparHtml(t.titulo)}</h3>
                    <p class="mb-3">${escaparHtml(t.descripcion)}</p>
                    <a href="${t.href}" class="btn btn-teal"><i class="fas fa-arrow-right me-2"></i>Ir al catálogo</a>
                </div>
            </div>`).join('');
    } catch (error) {
        const el = document.getElementById('alertaError');
        el.textContent = error.message;
        el.classList.remove('d-none');
    }
});

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
