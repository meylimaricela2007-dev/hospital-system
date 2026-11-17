// ============================================================
// Gráficas del dashboard del Administrador (admin/dashboard.html)
// Los datos salen de la BASE DE DATOS (DashboardGraficasService). Cada pocos segundos se vuelven a pedir:
// si alguien cambia un registro en la base, la gráfica se redibuja sola, sin recargar la página.
// ============================================================
const INTERVALO_MS = 4000;
const COLOR_PRINCIPAL = '#1b9089';
const COLOR_DESTACADO = '#ff7a45';
const PALETA = ['#1b9089', '#ff7a45', '#147770', '#ffb089', '#0a3d3a', '#e8632f', '#5bb5ad', '#f2a65a'];

const graficas = {};          // gráficas ya creadas, por id de canvas
let ultimoJson = null;        // para detectar si la base cambió entre una lectura y otra

// Dibuja el número de cada barra
const pluginValores = {
    id: 'valores',
    afterDatasetsDraw(chart) {
        if (chart.config.type !== 'bar') return;
        const ctx = chart.ctx;
        ctx.save();
        ctx.font = '600 12px sans-serif';
        ctx.fillStyle = '#2c3e3d';
        chart.getDatasetMeta(0).data.forEach((barra, i) => {
            const valor = chart.data.datasets[0].data[i];
            if (chart.options.indexAxis === 'y') {
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';
                ctx.fillText(valor, barra.x + 6, barra.y);
            } else {
                ctx.textAlign = 'center';
                ctx.textBaseline = 'bottom';
                ctx.fillText(valor, barra.x, barra.y - 4);
            }
        });
        ctx.restore();
    },
};

document.addEventListener('DOMContentLoaded', () => {
    if (!document.getElementById('graficaMedicos')) return;   // la página solo muestra las gráficas al Administrador
    actualizar();
    setInterval(actualizar, INTERVALO_MS);
    const boton = document.getElementById('btnActualizarGraficas');
    if (boton) boton.addEventListener('click', actualizar);
});

async function actualizar() {
    const estado = document.getElementById('estadoGraficas');
    try {
        const respuesta = await fetch('/api/dashboard/graficas', { cache: 'no-store' });
        if (respuesta.status === 401) { window.location.href = '/login-admin'; return; }
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw datos;

        const json = JSON.stringify([datos.topMedicos, datos.diasSemana, datos.fechasConMasCitas, datos.medicamentos, datos.metodosPago]);
        const cambio = ultimoJson !== null && json !== ultimoJson;
        ultimoJson = json;

        pintarTodas(datos);
        estado.textContent = `Datos leídos de la base de datos a las ${datos.actualizado}`;
        estado.classList.remove('text-danger');
        if (cambio) avisarCambio();
    } catch (error) {
        estado.textContent = error.mensaje || error.message || 'No se pudo leer la base de datos.';
        estado.classList.add('text-danger');
    }
}

function avisarCambio() {
    const aviso = document.getElementById('avisoCambioGraficas');
    aviso.classList.remove('d-none');
    clearTimeout(avisarCambio.temporizador);
    avisarCambio.temporizador = setTimeout(() => aviso.classList.add('d-none'), 4000);
}

function pintarTodas(d) {
    if (typeof Chart === 'undefined') return;     // la librería no cargó (sin internet)

    // 1) Top de médicos: la barra del primero se resalta
    dibujar('graficaMedicos', 'bar', d.topMedicos.etiquetas, d.topMedicos.valores, { horizontal: true, destacarPrimero: true });
    // 2) Días de la semana con más demanda: se resalta el día mayor
    dibujar('graficaDias', 'bar', d.diasSemana.etiquetas, d.diasSemana.valores, { destacarMayor: true });
    // 3) Fechas con más consultas médicas
    dibujar('graficaFechas', 'bar', d.fechasConMasCitas.etiquetas, d.fechasConMasCitas.valores, { destacarPrimero: true });
    // 4) Medicamento más despachado: la barra del primero se resalta
    dibujar('graficaMedicamentos', 'bar', d.medicamentos.etiquetas, d.medicamentos.valores, { horizontal: true, destacarPrimero: true });
    // 5) Métodos de pago: dona con el número en cada etiqueta
    const etiquetasPago = d.metodosPago.etiquetas.map((e, i) => `${e}: ${d.metodosPago.valores[i]}`);
    dibujar('graficaMetodos', 'doughnut', etiquetasPago, d.metodosPago.valores, {});
    document.getElementById('totalMetodos').textContent = d.metodosPago.valores.reduce((a, b) => a + b, 0);
}

function dibujar(idCanvas, tipo, etiquetas, valores, opciones) {
    const circular = tipo === 'doughnut';
    let colores = PALETA.slice(0, Math.max(etiquetas.length, 1));
    if (!circular) {
        const mayor = Math.max(...valores, 0);
        colores = valores.map((v, i) => {
            if (opciones.destacarPrimero && i === 0 && v > 0) return COLOR_DESTACADO;
            if (opciones.destacarMayor && v === mayor && v > 0) return COLOR_DESTACADO;
            return COLOR_PRINCIPAL;
        });
    }

    const existente = graficas[idCanvas];
    if (existente) {                                   // se actualiza en el lugar: la barra "crece" o "baja" con animación
        existente.data.labels = etiquetas;
        existente.data.datasets[0].data = valores;
        existente.data.datasets[0].backgroundColor = colores;
        existente.update();
        return;
    }
    graficas[idCanvas] = new Chart(document.getElementById(idCanvas), {
        type: tipo,
        data: {
            labels: etiquetas,
            datasets: [{ data: valores, backgroundColor: colores, borderRadius: circular ? 0 : 6, borderColor: '#ffffff', borderWidth: circular ? 2 : 0 }],
        },
        options: {
            indexAxis: opciones.horizontal ? 'y' : 'x',
            responsive: true,
            maintainAspectRatio: false,
            layout: { padding: { right: opciones.horizontal ? 36 : 0, top: opciones.horizontal ? 0 : 18 } },
            plugins: { legend: { display: circular, position: 'right' } },
            scales: circular ? {} : { [opciones.horizontal ? 'x' : 'y']: { beginAtZero: true, ticks: { precision: 0 } } },
        },
        plugins: [pluginValores],
    });
}
