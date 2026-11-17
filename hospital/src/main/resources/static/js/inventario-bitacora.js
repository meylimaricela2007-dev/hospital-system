// ============================================================
// CU-15: listado de la Bitácora de Inventario (inventario/bitacora.html)
// ============================================================
const estadoLista = { pagina: 0, tamanoPagina: 10, totalPaginas: 1 };

document.addEventListener('DOMContentLoaded', async () => {
    await cargarCatalogos();
    // Selector de mes: arranca en el mes actual y recarga el resumen y las gráficas al cambiarlo
    const selectorMes = document.getElementById('resumenMes');
    const hoy = new Date();
    selectorMes.value = hoy.getFullYear() + '-' + String(hoy.getMonth() + 1).padStart(2, '0');
    selectorMes.addEventListener('change', cargarResumen);
    cargarResumen();
    buscar();

    document.getElementById('btnBuscar').addEventListener('click', () => { estadoLista.pagina = 0; buscar(); });
    document.getElementById('btnLimpiar').addEventListener('click', limpiarFiltros);
    ['filtroReferencia', 'filtroUsuario'].forEach((id) =>
        document.getElementById(id).addEventListener('keyup', (e) => { if (e.key === 'Enter') { estadoLista.pagina = 0; buscar(); } }));
    document.getElementById('selectTamanoPagina').addEventListener('change', (e) => {
        estadoLista.tamanoPagina = Number(e.target.value);
        estadoLista.pagina = 0;
        buscar();
    });
    document.getElementById('btnPaginaAnterior').addEventListener('click', () => cambiarPagina(-1));
    document.getElementById('btnPaginaSiguiente').addEventListener('click', () => cambiarPagina(1));
});

async function cargarCatalogos() {
    try {
        const respuesta = await fetch('/api/inventario/catalogos');
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudieron cargar los filtros.');

        // En el filtro se incluye también "Despacho", que genera CU-11 automáticamente
        llenarSelect('filtroTipo', datos.tiposFiltro, 'Todos');
        llenarSelect('filtroMedicamento', datos.medicamentos, 'Todos');
        llenarSelect('filtroSucursal', datos.sucursales, 'Todas');
        if (datos.sucursalFija && datos.sucursales.length === 1) {
            document.getElementById('filtroSucursal').value = datos.sucursales[0].id;
            document.getElementById('filtroSucursal').disabled = true;
        }
    } catch (error) {
        mostrarError(error.message);
    }
}

function llenarSelect(id, items, textoTodos) {
    document.getElementById(id).innerHTML = `<option value="">${textoTodos}</option>` +
        items.map((i) => `<option value="${i.id}">${escaparHtml(i.nombre)}</option>`).join('');
}

function limpiarFiltros() {
    ['filtroTipo', 'filtroMedicamento', 'filtroReferencia', 'filtroUsuario', 'filtroDesde', 'filtroHasta']
        .forEach((id) => { document.getElementById(id).value = ''; });
    const sede = document.getElementById('filtroSucursal');
    if (!sede.disabled) sede.value = '';
    estadoLista.pagina = 0;
    buscar();
}

async function cargarResumen() {
    try {
        const [anio, mes] = (document.getElementById('resumenMes').value || '').split('-');
        const url = anio && mes
            ? `/api/inventario/resumen-mensual?anio=${anio}&mes=${Number(mes)}`
            : '/api/inventario/resumen-mensual';
        const respuesta = await fetch(url);
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message);
        const nombreMes = new Date(datos.anio, datos.mes - 1, 1).toLocaleDateString('es-GT', { month: 'long', year: 'numeric' });
        document.getElementById('resumenPeriodo').textContent = `Resumen de ${nombreMes}`;
        document.getElementById('resumenTotal').textContent = datos.totalMovimientos;
        document.getElementById('resumenEntradas').textContent = datos.unidadesEntrada;
        document.getElementById('resumenSalidas').textContent = datos.unidadesSalida;
        dibujarGraficas(datos);
    } catch (error) {
        // el resumen es informativo: si falla no se interrumpe el listado
    }
}

// ---------------------------------------------------------------
// Gráficas (Chart.js). Los datos ya vienen calculados desde el Service.
// ---------------------------------------------------------------
const graficas = {};
const TIPOS_ENTRADA = ['Compra', 'Devolución', 'Ajuste+'];

function dibujarGrafica(idCanvas, configuracion) {
    if (graficas[idCanvas]) graficas[idCanvas].destroy();       // evita encimar gráficas al cambiar de mes
    graficas[idCanvas] = new Chart(document.getElementById(idCanvas), configuracion);
}

function dibujarGraficas(d) {
    if (typeof Chart === 'undefined') return;                   // la librería no cargó (sin internet)
    const css = getComputedStyle(document.documentElement);
    const colorEntrada = css.getPropertyValue('--color-primary-500').trim() || '#1b9089';
    const colorSalida = css.getPropertyValue('--color-accent-500').trim() || '#ff7a45';
    const ejeEntero = { beginAtZero: true, ticks: { precision: 0 } };
    const base = { responsive: true, maintainAspectRatio: false };

    document.getElementById('sinDatosGraficas').classList.toggle('d-none', d.totalMovimientos > 0);

    // 1) Unidades por tipo de movimiento (verde = entrada, naranja = salida)
    dibujarGrafica('graficaTipos', {
        type: 'bar',
        data: {
            labels: d.porTipo.map((p) => p.etiqueta),
            datasets: [{
                label: 'Unidades',
                data: d.porTipo.map((p) => p.valor),
                backgroundColor: d.porTipo.map((p) => (TIPOS_ENTRADA.includes(p.etiqueta) ? colorEntrada : colorSalida)),
                borderRadius: 6,
            }],
        },
        options: { ...base, plugins: { legend: { display: false } }, scales: { y: ejeEntero } },
    });

    // 2) Entradas vs salidas por día del mes
    dibujarGrafica('graficaDias', {
        type: 'line',
        data: {
            labels: d.porDia.map((p) => p.dia),
            datasets: [
                { label: 'Entradas', data: d.porDia.map((p) => p.entradas), borderColor: colorEntrada, backgroundColor: colorEntrada + '33', fill: true, tension: 0.3 },
                { label: 'Salidas', data: d.porDia.map((p) => p.salidas), borderColor: colorSalida, backgroundColor: colorSalida + '33', fill: true, tension: 0.3 },
            ],
        },
        options: { ...base, scales: { y: ejeEntero, x: { title: { display: true, text: 'Día del mes' } } } },
    });

    // 3) Top 5 de medicamentos con más salidas
    dibujarGrafica('graficaTop', {
        type: 'bar',
        data: {
            labels: d.topSalidas.map((p) => p.etiqueta),
            datasets: [{ label: 'Unidades de salida', data: d.topSalidas.map((p) => p.valor), backgroundColor: colorSalida, borderRadius: 6 }],
        },
        options: { ...base, indexAxis: 'y', plugins: { legend: { display: false } }, scales: { x: ejeEntero } },
    });
}

async function buscar() {
    const params = new URLSearchParams({ page: estadoLista.pagina, size: estadoLista.tamanoPagina });
    const filtros = {
        idTipo: 'filtroTipo', idMedicamento: 'filtroMedicamento', idSucursal: 'filtroSucursal',
        referencia: 'filtroReferencia', usuario: 'filtroUsuario', desde: 'filtroDesde', hasta: 'filtroHasta'
    };
    Object.entries(filtros).forEach(([parametro, idInput]) => {
        const valor = document.getElementById(idInput).value.trim();
        if (valor) params.set(parametro, valor);
    });

    try {
        const respuesta = await fetch(`/api/inventario/movimientos?${params}`);
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

    // FA01
    if (filas.length === 0) {
        cuerpo.innerHTML = `<tr><td colspan="11" class="text-center py-4 text-muted">No se encontraron resultados.</td></tr>`;
        return;
    }

    cuerpo.innerHTML = filas.map((m) => `
        <tr class="${m.estado ? '' : 'text-muted'}">
            <td class="ps-4">
                <span class="badge ${m.entrada ? 'badge-soft-success' : (m.tipo === 'Despacho' ? 'badge-soft-info' : 'badge-soft-warning')}">${escaparHtml(m.tipo)}</span>
                ${m.estado ? '' : '<span class="badge badge-soft-danger ms-1">Inactivo</span>'}
            </td>
            <td>${escaparHtml(m.medicamento)}</td>
            <td>${escaparHtml(m.sucursal)}</td>
            <td class="text-end text-mono">${m.entrada ? '+' : '−'}${m.cantidad}</td>
            <td class="text-end text-mono">${m.stockAnterior}</td>
            <td class="text-end text-mono">${m.stockNuevo}</td>
            <td class="text-end text-mono">${m.costoUnitario != null ? 'Q' + Number(m.costoUnitario).toFixed(2) : '—'}</td>
            <td class="text-mono">${escaparHtml(m.numeroReferencia || '—')}</td>
            <td>${escaparHtml(m.usuario)}</td>
            <td class="small">${formatearFecha(m.fechaHora)}</td>
            <td class="text-end pe-4">
                <div class="dropdown">
                    <button class="btn btn-sm btn-outline-teal" data-bs-toggle="dropdown" aria-expanded="false">
                        <i class="fas fa-ellipsis-vertical"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                        <li><a class="dropdown-item" href="/inventario/movimiento/${m.idMovimiento}">
                            <i class="fas fa-eye me-2"></i>Ver</a></li>
                        <li><a class="dropdown-item" href="#" onclick="cambiarEstado(${m.idMovimiento}); return false;">
                            <i class="fas ${m.estado ? 'fa-toggle-off' : 'fa-toggle-on'} me-2"></i>${m.estado ? 'Desactivar' : 'Activar'}</a></li>
                    </ul>
                </div>
            </td>
        </tr>
    `).join('');
}

async function cambiarEstado(id) {
    try {
        const respuesta = await fetch(`/api/inventario/movimientos/${id}/estado`, { method: 'PATCH' });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo cambiar el estado.');
        mostrarExito(datos.mensaje);
        buscar();
        cargarResumen();
    } catch (error) {
        mostrarError(error.message);
    }
}

function cambiarPagina(delta) {
    const nueva = estadoLista.pagina + delta;
    if (nueva < 0 || nueva >= estadoLista.totalPaginas) return;
    estadoLista.pagina = nueva;
    buscar();
}

function formatearFecha(iso) {
    if (!iso) return '—';
    const f = new Date(iso);
    return f.toLocaleDateString('es-GT') + ' ' + f.toLocaleTimeString('es-GT', { hour: '2-digit', minute: '2-digit' });
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
