// ============================================================
// CU-07: Toma de Signos Vitales (enfermeria/enfermeria.html)
// ============================================================

const estadoEnfermeria = { citaEnModal: null };
let intervaloColaEnfermeria = null;

// [RN-CU07-06] Mismos rangos clínicos "normales" que evalúa el backend en
// SignosVitalesService.evaluarAlertasClinicas(); si esos números cambian
// allá, hay que cambiarlos aquí también para que la alerta EN TIEMPO REAL
// (FA03, mientras Enfermería va escribiendo) coincida con lo que el
// servidor terminará guardando en el expediente.
const RANGOS_CLINICOS = {
    presionSistolica: { min: 90, max: 120, unidad: 'mmHg', etiqueta: 'Presión sistólica' },
    presionDiastolica: { min: 60, max: 80, unidad: 'mmHg', etiqueta: 'Presión diastólica' },
    temperatura: { min: 36.1, max: 37.2, unidad: '°C', etiqueta: 'Temperatura' },
    frecuenciaCardiaca: { min: 60, max: 100, unidad: 'lpm', etiqueta: 'Frecuencia cardíaca' },
};

document.addEventListener('DOMContentLoaded', () => {
    cargarCola();
    // "El sistema muestra automáticamente la lista..." -> se refresca sola,
    // sin que Enfermería tenga que recargar la página cada vez.
    intervaloColaEnfermeria = setInterval(cargarCola, 8000);

    ['presionSistolica', 'presionDiastolica', 'temperatura', 'frecuenciaCardiaca'].forEach((id) => {
        document.getElementById(id).addEventListener('input', pintarAlertasClinicas);
    });
    document.getElementById('btnRegistrarSignos').addEventListener('click', registrarSignosVitales);

    document.getElementById('modalSignos').addEventListener('hidden.bs.modal', () => {
        estadoEnfermeria.citaEnModal = null;
    });
});

// ================================================================
// Paso 1 del flujo normal: la cola
// ================================================================
function cargarCola() {
    fetch('/api/signos-vitales/cola')
        .then((r) => r.json())
        .then((datos) => {
            pintarLista('listaEspera', 'contadorEspera', datos.enEspera, 'ESPERA');
            pintarLista('listaProceso', 'contadorProceso', datos.enProceso, 'PROCESO');
        })
        .catch(() => { /* la próxima ronda del setInterval lo vuelve a intentar */ });
}

function pintarLista(idContenedor, idContador, pacientes, tipo) {
    const contenedor = document.getElementById(idContenedor);
    document.getElementById(idContador).textContent = pacientes.length;

    if (pacientes.length === 0) {
        contenedor.innerHTML = `<p class="text-muted small">Sin pacientes por ahora.</p>`;
        return;
    }

    contenedor.innerHTML = pacientes.map((p) => tarjetaPaciente(p, tipo)).join('');
}

function tarjetaPaciente(p, tipo) {
    const badgeEmergencia = p.esEmergencia ? '<span class="badge badge-emergencia ms-2">EMERGENCIA</span>' : '';
    const boton = tipo === 'ESPERA'
        ? `<button class="btn btn-accent btn-sm mt-2" onclick="llamarPaciente(${p.idCita})">
               <i class="fas fa-bullhorn me-2"></i>Llamar y Tomar Signos
           </button>`
        : `<button class="btn btn-teal btn-sm mt-2" onclick='abrirFormularioSignos(${JSON.stringify(p)})'>
               <i class="fas fa-notes-medical me-2"></i>Registrar Signos Vitales
           </button>`;

    return `
        <div class="hh-card hh-card-recepcion ${p.esEmergencia ? 'es-emergencia' : ''} mb-3">
            <div class="d-flex justify-content-between align-items-start">
                <div>
                    <h3 class="mb-1">${escaparHtmlEnf(p.nombrePaciente)}${badgeEmergencia}</h3>
                    <div class="small text-muted">${escaparHtmlEnf(p.especialidad)} · ${escaparHtmlEnf(p.nombreMedico)}</div>
                </div>
                <span class="text-mono text-muted small">No. ${p.idCita}</span>
            </div>
            ${boton}
        </div>`;
}

// ================================================================
// Paso 1: "Llamar y Tomar Signos" (anuncio TTS)
// ================================================================
function llamarPaciente(idCita) {
    fetch(`/api/signos-vitales/citas/${idCita}/llamar`, { method: 'POST' })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            anunciarPorVoz(datos.textoAnuncio);
            mostrarAlertaGeneral(datos.textoAnuncio);
            cargarCola();
        })
        .catch((error) => {
            mostrarAlertaGeneral(error.mensaje || error.message || 'No se pudo llamar al paciente.', true);
        });
}

// "El sistema anuncia por altavoz mediante síntesis de voz (TTS)": usamos
// la Web Speech API del navegador, sin depender de ningún servicio externo.
function anunciarPorVoz(texto) {
    if (!('speechSynthesis' in window)) return;
    const utterance = new SpeechSynthesisUtterance(texto);
    utterance.lang = 'es-GT';
    window.speechSynthesis.cancel(); // no acumular anuncios si llaman varios seguidos
    window.speechSynthesis.speak(utterance);
}

function mostrarAlertaGeneral(mensaje, esError) {
    const alerta = document.getElementById('alertaGeneral');
    alerta.className = esError ? 'alert alert-danger' : 'alert alert-success';
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');
    setTimeout(() => alerta.classList.add('d-none'), 6000);
}

// ================================================================
// Pasos 2-9 del flujo normal: formulario de signos vitales
// ================================================================
function abrirFormularioSignos(paciente) {
    estadoEnfermeria.citaEnModal = paciente;

    document.getElementById('signosNombrePaciente').textContent = paciente.nombrePaciente;
    document.getElementById('signosNoCita').textContent = paciente.idCita;

    document.getElementById('formSignos').reset();
    document.getElementById('signosAlertaError').classList.add('d-none');
    document.getElementById('signosAlertasClinicas').innerHTML = '';

    new bootstrap.Modal(document.getElementById('modalSignos')).show();
}

// FA03: alertas visuales en tiempo real (componente VitalSignAlertsDisplay)
function pintarAlertasClinicas() {
    const contenedor = document.getElementById('signosAlertasClinicas');
    const alertas = [];

    Object.entries(RANGOS_CLINICOS).forEach(([id, rango]) => {
        const valor = parseFloat(document.getElementById(id).value);
        if (isNaN(valor)) return;
        if (valor < rango.min || valor > rango.max) {
            alertas.push(`${rango.etiqueta} fuera de rango normal: ${valor} ${rango.unidad} (normal: ${rango.min}-${rango.max} ${rango.unidad}).`);
        }
    });

    if (alertas.length === 0) {
        contenedor.innerHTML = '';
        return;
    }
    contenedor.innerHTML = `
        <div class="alert alert-warning">
            <div class="fw-semibold mb-1"><i class="fas fa-triangle-exclamation me-1"></i>Valores fuera de rango clínico normal</div>
            <ul class="mb-0 small">${alertas.map((a) => `<li>${escaparHtmlEnf(a)}</li>`).join('')}</ul>
        </div>`;
}

// Paso 9-12 del flujo normal / FA01 / FA02
function registrarSignosVitales() {
    const cita = estadoEnfermeria.citaEnModal;
    if (!cita) return;

    const body = {
        presionSistolica: numeroONull('presionSistolica'),
        presionDiastolica: numeroONull('presionDiastolica'),
        temperatura: numeroONull('temperatura'),
        peso: numeroONull('peso'),
        talla: numeroONull('talla'),
        frecuenciaCardiaca: numeroONull('frecuenciaCardiaca'),
        esEmergencia: document.getElementById('esEmergenciaSignos').checked,
    };

    const btn = document.getElementById('btnRegistrarSignos');
    btn.disabled = true;
    document.getElementById('btnRegistrarSignosSpinner').classList.remove('d-none');
    document.getElementById('signosAlertaError').classList.add('d-none');

    fetch(`/api/signos-vitales/citas/${cita.idCita}/registrar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            bootstrap.Modal.getInstance(document.getElementById('modalSignos')).hide();
            mostrarAlertaGeneral(datos.mensaje);
            cargarCola();
        })
        .catch((error) => {
            // FA02: "el sistema muestra el mensaje de error específico del campo que falló"
            const alerta = document.getElementById('signosAlertaError');
            alerta.textContent = error.mensaje || error.message || 'No se pudieron registrar los signos vitales.';
            alerta.classList.remove('d-none');
        })
        .finally(() => {
            btn.disabled = false;
            document.getElementById('btnRegistrarSignosSpinner').classList.add('d-none');
        });
}

function numeroONull(id) {
    const valor = document.getElementById(id).value;
    return valor === '' ? null : Number(valor);
}

function escaparHtmlEnf(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
