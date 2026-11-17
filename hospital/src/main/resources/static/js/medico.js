// ============================================================
// CU-08: Consulta Médica (medico/panel.html)
// ============================================================

const estadoMedico = { citaActual: null, examenesCatalogo: [], medicamentosCatalogo: [] };
let intervaloPanelMedico = null;
let debounceCie10 = null;

document.addEventListener('DOMContentLoaded', () => {
    // FA02, paso 5: si venimos de crear una cita de seguimiento, citas.js
    // nos manda de vuelta con el mensaje ya armado por el backend.
    const params = new URLSearchParams(window.location.search);
    const seguimientoCreado = params.get('seguimientoCreado');
    if (seguimientoCreado) {
        mostrarAlertaGeneral(seguimientoCreado);
        window.history.replaceState({}, '', '/medico');
    }

    cargarPanel();
    intervaloPanelMedico = setInterval(cargarPanel, 8000);

    document.getElementById('codigoCie10').addEventListener('input', buscarCie10Debounced);
    document.getElementById('btnGuardarEnCurso').addEventListener('click', () => guardarConsulta('En curso'));
    document.getElementById('btnFinalizarConsulta').addEventListener('click', () => guardarConsulta('Finalizada'));
    document.getElementById('btnGenerarOrdenLab').addEventListener('click', generarOrdenLaboratorio);
    document.getElementById('btnAgregarMedicamento').addEventListener('click', () => agregarFilaMedicamento());
    document.getElementById('btnGenerarReceta').addEventListener('click', generarReceta);

    document.addEventListener('click', (e) => {
        if (!e.target.closest('#codigoCie10') && !e.target.closest('#cie10Sugerencias')) {
            document.getElementById('cie10Sugerencias').innerHTML = '';
        }
    });
});

// ================================================================
// Paso 1: el panel (3 secciones)
// ================================================================
function cargarPanel() {
    fetch('/api/medico/panel')
        .then((r) => r.json())
        .then((datos) => {
            pintarLista('listaEspera', 'contadorEspera', datos.enEspera, 'ESPERA');
            pintarLista('listaConsulta', 'contadorConsulta', datos.enConsulta, 'CONSULTA');
            pintarLista('listaEvaluados', 'contadorEvaluados', datos.evaluados, 'EVALUADO');
        })
        .catch(() => { /* el próximo setInterval lo reintenta */ });
}

function pintarLista(idContenedor, idContador, citas, tipo) {
    const contenedor = document.getElementById(idContenedor);
    document.getElementById(idContador).textContent = citas.length;

    if (citas.length === 0) {
        contenedor.innerHTML = '<p class="text-muted small">Sin pacientes por ahora.</p>';
        return;
    }
    contenedor.innerHTML = citas.map((c) => tarjetaCitaMedico(c, tipo)).join('');
}

function tarjetaCitaMedico(c, tipo) {
    const badgeEmergencia = c.esEmergencia ? '<span class="badge badge-emergencia ms-2">EMERGENCIA</span>' : '';
    let botones = '';

    if (tipo === 'ESPERA') {
        botones = `
            <div class="d-flex flex-wrap gap-2 mt-2">
                <button class="btn btn-accent btn-sm" onclick="iniciarConsulta(${c.idCita})">
                    <i class="fas fa-play me-2"></i>Iniciar Consulta
                </button>
                <button class="btn btn-outline-danger btn-sm" onclick="marcarNoAsistio(${c.idCita})">
                    <i class="fas fa-user-xmark me-2"></i>No Asistió
                </button>
            </div>`;
    } else if (tipo === 'CONSULTA') {
        botones = `
            <button class="btn btn-teal btn-sm mt-2"
                    onclick='abrirModalConsulta(${c.idCita}, ${jsEnAtributo(c.nombrePaciente)}, ${c.esEmergencia ? 'true' : 'false'})'>
                <i class="fas fa-notes-medical me-2"></i>Ver/Completar Consulta
            </button>`;
    } else { // EVALUADO
        botones = `
            <div class="d-flex flex-wrap gap-2 mt-2">
                <button class="btn btn-outline-teal btn-sm" onclick="abrirModalLaboratorio(${c.idCita})">
                    <i class="fas fa-flask me-2"></i>Orden Lab.
                </button>
                <button class="btn btn-outline-teal btn-sm" onclick="abrirModalReceta(${c.idCita})">
                    <i class="fas fa-prescription me-2"></i>Receta
                </button>
                <button class="btn btn-outline-teal btn-sm" onclick="irASeguimiento(${c.idPaciente}, ${jsEnAtributo(c.nombrePaciente)}, ${c.idConsulta})">
                    <i class="fas fa-calendar-plus me-2"></i>Seguimiento
                </button>
                <button class="btn btn-accent btn-sm" onclick="finalizarAtencion(${c.idCita})">
                    <i class="fas fa-check-double me-2"></i>Finalizar Atención
                </button>
            </div>`;
    }

    return `
        <div class="hh-card hh-card-recepcion ${c.esEmergencia ? 'es-emergencia' : ''} mb-3">
            <div class="d-flex justify-content-between align-items-start">
                <div>
                    <h3 class="mb-1">${escaparHtmlMedico(c.nombrePaciente)}${badgeEmergencia}</h3>
                    <div class="small text-muted">${escaparHtmlMedico(c.especialidad)}</div>
                </div>
                <span class="text-mono text-muted small">No. ${c.idCita}</span>
            </div>
            ${botones}
        </div>`;
}

function mostrarAlertaGeneral(mensaje, esError) {
    const alerta = document.getElementById('alertaGeneral');
    alerta.className = esError ? 'alert alert-danger' : 'alert alert-success';
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');
    setTimeout(() => alerta.classList.add('d-none'), 7000);
}

// ================================================================
// Paso 2 / FA06
// ================================================================
function iniciarConsulta(idCita) {
    fetch(`/api/medico/citas/${idCita}/iniciar-consulta`, { method: 'POST' })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            anunciarPorVoz(datos.textoAnuncio);
            mostrarAlertaGeneral(datos.textoAnuncio);
            cargarPanel();
        })
        .catch((error) => mostrarAlertaGeneral(error.mensaje || error.message || 'No se pudo iniciar la consulta.', true));
}

function marcarNoAsistio(idCita) {
    fetch(`/api/medico/citas/${idCita}/no-asistio`, { method: 'POST' })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => { mostrarAlertaGeneral(datos.mensaje); cargarPanel(); })
        .catch((error) => mostrarAlertaGeneral(error.mensaje || error.message || 'No se pudo actualizar la cita.', true));
}

function anunciarPorVoz(texto) {
    if (!('speechSynthesis' in window)) return;
    const utterance = new SpeechSynthesisUtterance(texto);
    utterance.lang = 'es-GT';
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
}

// ================================================================
// Pasos 3-9: formulario de consulta
// ================================================================
function abrirModalConsulta(idCita, nombrePaciente, esEmergencia) {
    estadoMedico.citaActual = { idCita, nombrePaciente };
    // CU-08: si el paciente es de emergencia, el formulario lo indica
    document.getElementById('consultaBadgeEmergencia').classList.toggle('d-none', !esEmergencia);
    document.getElementById('consultaNombrePaciente').textContent = nombrePaciente;
    document.getElementById('consultaNoCita').textContent = idCita;
    document.getElementById('consultaAlertaError').classList.add('d-none');
    document.getElementById('cie10Sugerencias').innerHTML = '';
    document.getElementById('formConsulta').reset();

    fetch(`/api/medico/citas/${idCita}/contexto`)
        .then((r) => r.json())
        .then((c) => {
            document.getElementById('motivoVisita').value = c.motivoVisita || '';
            document.getElementById('hallazgosClinicos').value = c.hallazgosClinicos || '';
            document.getElementById('codigoCie10').value = c.codigoCie10 || '';
            document.getElementById('diagnostico').value = c.diagnostico || '';
            document.getElementById('planTratamiento').value = c.planTratamiento || '';
            document.getElementById('notasAdicionales').value = c.notasAdicionales || '';
        });

    new bootstrap.Modal(document.getElementById('modalConsulta')).show();
}

function buscarCie10Debounced() {
    clearTimeout(debounceCie10);
    const texto = document.getElementById('codigoCie10').value.trim();
    debounceCie10 = setTimeout(() => {
        fetch(`/api/catalogos/cie10?buscar=${encodeURIComponent(texto)}`)
            .then((r) => r.json())
            .then((resultados) => {
                const contenedor = document.getElementById('cie10Sugerencias');
                if (resultados.length === 0) { contenedor.innerHTML = ''; return; }
                contenedor.innerHTML = resultados.map((c) => `
                    <button type="button" class="list-group-item list-group-item-action"
                            onclick="seleccionarCie10(${jsEnAtributo(c.codigo)}, ${jsEnAtributo(c.descripcion)})">
                        <strong class="text-mono">${c.codigo}</strong> — ${escaparHtmlMedico(c.descripcion)}
                    </button>`).join('');
            });
    }, 250);
}

function seleccionarCie10(codigo, descripcion) {
    document.getElementById('codigoCie10').value = codigo;
    document.getElementById('cie10Sugerencias').innerHTML = '';
    // Si el diagnóstico todavía está vacío, se aprovecha para prellenarlo con la descripción.
    const diagnostico = document.getElementById('diagnostico');
    if (!diagnostico.value.trim()) diagnostico.value = descripcion;
}

function mostrarErrorConsulta(mensaje) {
    const alerta = document.getElementById('consultaAlertaError');
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');
    // El aviso está arriba del formulario: se desplaza hasta él para que el médico lo vea
    alerta.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function guardarConsulta(estadoConsulta) {
    const cita = estadoMedico.citaActual;
    const body = {
        motivoVisita: document.getElementById('motivoVisita').value.trim(),
        hallazgosClinicos: document.getElementById('hallazgosClinicos').value.trim(),
        codigoCie10: document.getElementById('codigoCie10').value.trim(),
        diagnostico: document.getElementById('diagnostico').value.trim(),
        planTratamiento: document.getElementById('planTratamiento').value.trim(),
        notasAdicionales: document.getElementById('notasAdicionales').value.trim(),
        estadoConsulta,
    };
    document.getElementById('consultaAlertaError').classList.add('d-none');

    // Validación rápida en pantalla; el servidor (ConsultaMedicaService) vuelve a validar.
    if (!body.motivoVisita) {
        mostrarErrorConsulta('El motivo de visita es obligatorio.');
        return;
    }
    if (body.motivoVisita.length < 10) {
        mostrarErrorConsulta('El motivo de visita debe contener entre 10 y 2000 caracteres.');
        return;
    }
    if (estadoConsulta === 'Finalizada' && !body.diagnostico) {
        mostrarErrorConsulta('No es posible finalizar la consulta sin registrar un diagnóstico. El campo Diagnóstico es obligatorio.');
        return;
    }

    const botones = [document.getElementById('btnGuardarEnCurso'), document.getElementById('btnFinalizarConsulta')];
    botones.forEach((b) => (b.disabled = true));

    fetch(`/api/medico/citas/${cita.idCita}/consulta`, {
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
            bootstrap.Modal.getInstance(document.getElementById('modalConsulta')).hide();
            mostrarAlertaGeneral(datos.mensaje);
            cargarPanel();
        })
        .catch((error) => {
            // FA05 y demás errores del servidor
            mostrarErrorConsulta(error.mensaje || error.message || 'No se pudo guardar la consulta.');
        })
        .finally(() => botones.forEach((b) => (b.disabled = false)));
}

// ================================================================
// FA01: orden de laboratorio
// ================================================================
function abrirModalLaboratorio(idCita) {
    estadoMedico.citaActual = { idCita };
    document.getElementById('labAlertaError').classList.add('d-none');
    document.getElementById('labObservaciones').value = '';
    document.getElementById('labOrdenExterna').checked = false;
    document.getElementById('labMontoTotal').textContent = 'Q0.00';

    const cargarLista = (examenes) => {
        estadoMedico.examenesCatalogo = examenes;
        document.getElementById('listaExamenes').innerHTML = examenes.map((e) => `
            <div class="form-check">
                <input class="form-check-input" type="checkbox" value="${e.idExamen}" id="examen${e.idExamen}"
                       data-precio="${e.precio}" onchange="actualizarTotalLaboratorio()">
                <label class="form-check-label" for="examen${e.idExamen}">
                    ${escaparHtmlMedico(e.nombre)} <span class="text-muted small">(${escaparHtmlMedico(e.laboratorio)}) — Q${Number(e.precio).toFixed(2)}</span>
                </label>
            </div>`).join('') || '<p class="text-muted small">No hay exámenes disponibles en el catálogo.</p>';
    };

    if (estadoMedico.examenesCatalogo.length > 0) {
        cargarLista(estadoMedico.examenesCatalogo);
    } else {
        fetch('/api/catalogos/examenes-laboratorio').then((r) => r.json()).then(cargarLista);
    }

    new bootstrap.Modal(document.getElementById('modalLaboratorio')).show();
}

function actualizarTotalLaboratorio() {
    const seleccionados = document.querySelectorAll('#listaExamenes input:checked');
    let total = 0;
    seleccionados.forEach((el) => { total += parseFloat(el.dataset.precio); });
    document.getElementById('labMontoTotal').textContent = 'Q' + total.toFixed(2);
}

function generarOrdenLaboratorio() {
    const cita = estadoMedico.citaActual;
    const idsExamenes = [...document.querySelectorAll('#listaExamenes input:checked')].map((el) => Number(el.value));

    fetch(`/api/medico/citas/${cita.idCita}/orden-laboratorio`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            idsExamenes,
            observaciones: document.getElementById('labObservaciones').value.trim(),
            esExterna: document.getElementById('labOrdenExterna').checked, // CU-09, FA01
        }),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            bootstrap.Modal.getInstance(document.getElementById('modalLaboratorio')).hide();
            mostrarAlertaGeneral(datos.mensaje);
        })
        .catch((error) => {
            const alerta = document.getElementById('labAlertaError');
            alerta.textContent = error.mensaje || error.message || 'No se pudo generar la orden.';
            alerta.classList.remove('d-none');
        });
}

// ================================================================
// FA04: receta médica
// ================================================================
function abrirModalReceta(idCita) {
    estadoMedico.citaActual = { idCita };
    document.getElementById('recetaAlertaError').classList.add('d-none');
    document.getElementById('recetaNotas').value = '';
    document.getElementById('listaMedicamentosReceta').innerHTML = '';

    const abrir = () => { agregarFilaMedicamento(); new bootstrap.Modal(document.getElementById('modalReceta')).show(); };

    if (estadoMedico.medicamentosCatalogo.length > 0) {
        abrir();
    } else {
        fetch('/api/catalogos/medicamentos').then((r) => r.json()).then((meds) => {
            estadoMedico.medicamentosCatalogo = meds;
            abrir();
        });
    }
}

function agregarFilaMedicamento() {
    const opciones = estadoMedico.medicamentosCatalogo
        .map((m) => `<option value="${m.idMedicamento}">${escaparHtmlMedico(m.nombre)} (${escaparHtmlMedico(m.unidad)})</option>`)
        .join('');

    const fila = document.createElement('div');
    fila.className = 'border rounded p-3 mb-2 fila-medicamento';
    fila.innerHTML = `
        <div class="row g-2">
            <div class="col-md-6">
                <label class="form-label small mb-1">Medicamento</label>
                <select class="form-select form-select-sm campo-medicamento">${opciones}</select>
            </div>
            <div class="col-md-3">
                <label class="form-label small mb-1">Cantidad</label>
                <input type="number" min="1" class="form-control form-control-sm campo-cantidad" value="1">
            </div>
            <div class="col-md-3">
                <label class="form-label small mb-1">Dosis</label>
                <input type="text" class="form-control form-control-sm campo-dosis" placeholder="Ej. 500mg">
            </div>
            <div class="col-md-4">
                <label class="form-label small mb-1">Frecuencia</label>
                <input type="text" class="form-control form-control-sm campo-frecuencia" placeholder="Ej. Cada 8 horas">
            </div>
            <div class="col-md-4">
                <label class="form-label small mb-1">Duración</label>
                <input type="text" class="form-control form-control-sm campo-duracion" placeholder="Ej. 7 días">
            </div>
            <div class="col-md-4">
                <label class="form-label small mb-1">Indicaciones</label>
                <input type="text" class="form-control form-control-sm campo-indicaciones" placeholder="Opcional">
            </div>
        </div>
        <button type="button" class="btn btn-link text-danger btn-sm mt-1 p-0" onclick="this.closest('.fila-medicamento').remove()">
            <i class="fas fa-trash me-1"></i>Quitar
        </button>`;
    document.getElementById('listaMedicamentosReceta').appendChild(fila);
}

function generarReceta() {
    const cita = estadoMedico.citaActual;
    const filas = document.querySelectorAll('#listaMedicamentosReceta .fila-medicamento');
    const medicamentos = [...filas].map((fila) => ({
        idMedicamento: Number(fila.querySelector('.campo-medicamento').value),
        cantidad: Number(fila.querySelector('.campo-cantidad').value),
        dosis: fila.querySelector('.campo-dosis').value.trim(),
        frecuencia: fila.querySelector('.campo-frecuencia').value.trim(),
        duracion: fila.querySelector('.campo-duracion').value.trim(),
        indicaciones: fila.querySelector('.campo-indicaciones').value.trim(),
    }));

    fetch(`/api/medico/citas/${cita.idCita}/receta`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ medicamentos, notas: document.getElementById('recetaNotas').value.trim() }),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            bootstrap.Modal.getInstance(document.getElementById('modalReceta')).hide();
            mostrarAlertaGeneral(datos.mensaje);
        })
        .catch((error) => {
            const alerta = document.getElementById('recetaAlertaError');
            alerta.textContent = error.mensaje || error.message || 'No se pudo generar la receta.';
            alerta.classList.remove('d-none');
        });
}

// ================================================================
// FA02: cita de seguimiento / paso 11: finalizar atención
// ================================================================
function irASeguimiento(idPaciente, nombrePaciente, idConsulta) {
    window.location.href = `/citas/agendar?idPaciente=${idPaciente}&nombrePaciente=${encodeURIComponent(nombrePaciente)}&idConsultaOrigen=${idConsulta}`;
}

function finalizarAtencion(idCita) {
    fetch(`/api/medico/citas/${idCita}/finalizar-atencion`, { method: 'POST' })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => { mostrarAlertaGeneral(datos.mensaje); cargarPanel(); })
        .catch((error) => mostrarAlertaGeneral(error.mensaje || error.message || 'No se pudo finalizar la atención.', true));
}

// Valor listo para usar dentro de onclick="..." : escapa las comillas para no romper el atributo HTML
// (con nombres como O'Brien o descripciones con comillas, JSON.stringify solo rompía el botón).
function jsEnAtributo(valor) {
    return JSON.stringify(valor ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function escaparHtmlMedico(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}