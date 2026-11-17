// ============================================================
// CU-03: Agendar Cita (citas/agendar.html)
// Wizard de 5 pasos: Sede -> Especialidad -> Médico -> Fecha/Hora -> Confirmar.
// ============================================================

// Todo lo que el paciente va eligiendo se guarda aquí, en memoria,
// mientras avanza por el wizard.
const wizard = {
    pasoActual: 1,
    sucursalId: null, sucursalNombre: null,
    especialidadId: null, especialidadNombre: null,
    medicoId: null, medicoNombre: null,
    horarios: [],          // todos los horarios disponibles del médico elegido
    fechaSeleccionada: null,
    horarioId: null, horaSeleccionada: null,
    // CU-05, FA04: si esta página se abrió desde el módulo de Recepción,
    // idPacienteWalkIn viene precargado en un input oculto (ver agendar.html).
    idPacienteWalkIn: null,
    // NUEVO (CU-08, FA02): igual, pero cuando la abrió un Médico desde
    // "Evaluados" para agendar un seguimiento.
    idConsultaOrigen: null,
    idTipoSeguimiento: null,
    idPrioridad: null,
    // CU-12: true cuando es una cita de seguimiento (sede, especialidad y médico vienen fijos).
    esSeguimiento: false,
    tipoSeguimientoNombre: null,
};

document.addEventListener('DOMContentLoaded', () => {
    const inputWalkIn = document.getElementById('idPacienteWalkIn');
    if (inputWalkIn && inputWalkIn.value.trim() !== '') {
        wizard.idPacienteWalkIn = Number(inputWalkIn.value.trim());
    }

    const inputConsultaOrigen = document.getElementById('idConsultaOrigen');
    if (inputConsultaOrigen && inputConsultaOrigen.value.trim() !== '') {
        wizard.idConsultaOrigen = Number(inputConsultaOrigen.value.trim());
        wizard.esSeguimiento = true;
        iniciarSeguimiento();
    } else {
        cargarSucursales();
    }

    document.getElementById('btnNext').addEventListener('click', avanzarPaso);
    document.getElementById('btnPrev').addEventListener('click', retrocederPaso);

    document.getElementById('motivoConsulta').addEventListener('input', function () {
        document.getElementById('motivoContador').textContent = this.value.length;
        validarPasoActual();
    });
});

// ================================================================
// CU-12: cita de seguimiento. Los pasos 1-3 (sede, especialidad, médico)
// vienen fijos desde la consulta padre, así que el médico arranca
// directamente en el calendario del mismo médico.
// ================================================================
async function iniciarSeguimiento() {
    const btnNext = document.getElementById('btnNext');
    btnNext.disabled = true;
    try {
        const [resCtx, resTipos, resPrioridades] = await Promise.all([
            fetch(`/api/citas/seguimiento/contexto?idConsultaOrigen=${wizard.idConsultaOrigen}`),
            fetch('/api/catalogos/tipos-seguimiento'),
            fetch('/api/catalogos/prioridades'),
        ]);
        const ctx = await resCtx.json().catch(() => ({}));
        if (!resCtx.ok) throw new Error(ctx.message || 'No se pudo cargar la consulta de origen.');

        // Paso 2: datos pre-cargados
        wizard.idPacienteWalkIn = ctx.idPaciente;
        wizard.sucursalId = ctx.idSucursal;     wizard.sucursalNombre = ctx.sucursal;
        wizard.especialidadId = ctx.idEspecialidad; wizard.especialidadNombre = ctx.especialidad;
        wizard.medicoId = ctx.idMedico;         wizard.medicoNombre = ctx.nombreMedico;
        wizard.pacienteNombre = ctx.nombrePaciente;

        document.getElementById('segPaciente').textContent = ctx.nombrePaciente;
        document.getElementById('segMedico').textContent = ctx.nombreMedico;
        document.getElementById('segEspecialidad').textContent = ctx.especialidad;
        document.getElementById('segSucursal').textContent = ctx.sucursal;
        document.getElementById('bannerSeguimiento').classList.remove('d-none');

        // Paso 3: tipo de seguimiento [RN-CU11-01]
        const tipos = resTipos.ok ? await resTipos.json() : [];
        const select = document.getElementById('idTipoSeguimiento');
        select.innerHTML = '<option value="">-- Seleccione el tipo de seguimiento --</option>' +
            tipos.map((t) => `<option value="${t.id}">${escaparHtml(t.nombre)}</option>`).join('');
        select.addEventListener('change', function () {
            wizard.idTipoSeguimiento = this.value ? Number(this.value) : null;
            wizard.tipoSeguimientoNombre = this.value ? this.selectedOptions[0].textContent : null;
            validarPasoActual();
        });
        document.getElementById('bloqueTipoSeguimiento').classList.remove('d-none');

        // Paso 6: prioridad (por defecto "Normal")
        const prioridades = resPrioridades.ok ? await resPrioridades.json() : [];
        const selPrioridad = document.getElementById('idPrioridad');
        selPrioridad.innerHTML = prioridades.map((p) => `<option value="${p.id}">${escaparHtml(p.nombre)}</option>`).join('');
        const normal = prioridades.find((p) => p.nombre.toLowerCase() === 'normal');
        if (normal) selPrioridad.value = normal.id;
        wizard.idPrioridad = selPrioridad.value ? Number(selPrioridad.value) : null;
        selPrioridad.addEventListener('change', function () { wizard.idPrioridad = this.value ? Number(this.value) : null; });
        document.getElementById('bloquePrioridad').classList.remove('d-none');
        document.getElementById('filaResumenTipo').classList.remove('d-none');
        document.getElementById('motivoConsulta').placeholder = 'Ej. Revisión de la respuesta al tratamiento indicado...';

        // Paso 5 del wizard: calendario del MISMO médico
        document.getElementById('btnPrev').innerHTML = '<i class="fas fa-arrow-left me-2"></i>Volver';
        const hayHorarios = await cargarHorarios();
        if (hayHorarios) irAPaso(4);
    } catch (error) {
        mostrarAlertaGeneral(error.message === 'Failed to fetch'
            ? 'No se pudo conectar con el servidor. Intente de nuevo más tarde.' : error.message);
    }
}

// ================================================================
// PASO 1: Sedes
// ================================================================
function cargarSucursales() {
    fetch('/api/catalogos/sucursales')
        .then((r) => r.json())
        .then((sucursales) => {
            document.getElementById('sucursalesList').innerHTML = sucursales.map((s) => `
                <div class="selection-card" data-id="${s.id}" data-nombre="${escaparHtml(s.nombre)}" onclick="seleccionarSucursal(this)">
                    <div class="d-flex align-items-center gap-3">
                        <div class="icon-badge mb-0"><i class="fas fa-hospital"></i></div>
                        <div><strong>${escaparHtml(s.nombre)}</strong></div>
                    </div>
                </div>
            `).join('');
        })
        .catch(() => mostrarAlertaGeneral('No se pudo conectar con el servidor. Intente de nuevo más tarde.'));
}

function seleccionarSucursal(elemento) {
    marcarSeleccionUnica(elemento, '#sucursalesList .selection-card');
    wizard.sucursalId = elemento.dataset.id;
    wizard.sucursalNombre = elemento.dataset.nombre;
    validarPasoActual();
}

// ================================================================
// PASO 2: Especialidades por sede
// ================================================================
function cargarEspecialidades() {
    document.getElementById('sucursalNombreDisplay').textContent = wizard.sucursalNombre;

    return fetch(`/api/catalogos/especialidades-por-sucursal/${wizard.sucursalId}`)
        .then((r) => r.json())
        .then((especialidades) => {
            if (especialidades.length === 0) {
                // FA01: no hay especialidades en la sucursal elegida
                mostrarAlertaGeneral(`No hay especialidades disponibles para la sucursal ${wizard.sucursalNombre}. Seleccione otra sucursal.`);
                // Se deselecciona la sede para obligar a elegir otra (el paciente sigue en el paso 1).
                wizard.sucursalId = null;
                wizard.sucursalNombre = null;
                document.querySelectorAll('#sucursalesList .selection-card').forEach((el) => el.classList.remove('selected'));
                validarPasoActual();
                document.getElementById('alertaGeneral').scrollIntoView({ behavior: 'smooth', block: 'center' });
                return false;
            }
            document.getElementById('especialidadesList').innerHTML = especialidades.map((e) => `
                <div class="selection-card" data-id="${e.id}" data-nombre="${escaparHtml(e.nombre)}" onclick="seleccionarEspecialidad(this)">
                    <strong>${escaparHtml(e.nombre)}</strong>
                </div>
            `).join('');
            return true;
        });
}

// Quita la especialidad y el médico elegidos para obligar a escoger otra opción.
function reiniciarEspecialidad() {
    wizard.especialidadId = null;
    wizard.especialidadNombre = null;
    wizard.medicoId = null;
    wizard.medicoNombre = null;
    wizard.horarios = [];
    document.querySelectorAll('#especialidadesList .selection-card, #medicosList .selection-card')
        .forEach((el) => el.classList.remove('selected'));
}

function seleccionarEspecialidad(elemento) {
    marcarSeleccionUnica(elemento, '#especialidadesList .selection-card');
    wizard.especialidadId = elemento.dataset.id;
    wizard.especialidadNombre = elemento.dataset.nombre;
    validarPasoActual();
}

// ================================================================
// PASO 3: Médicos por especialidad + sede
// ================================================================
function cargarMedicos() {
    document.getElementById('especialidadNombreDisplay').textContent = wizard.especialidadNombre;

    return fetch(`/api/catalogos/medicos-por-especialidad/${wizard.especialidadId}?sucursal=${wizard.sucursalId}`)
        .then((r) => r.json())
        .then((medicos) => {
            if (medicos.length === 0) {
                // FA02: sin médicos/horarios -> el documento pide regresar al paso 2 (Especialidad)
                const msg = `No se encontraron horarios disponibles para la especialidad ${wizard.especialidadNombre} en la Sede ${wizard.sucursalNombre}. Por favor, seleccione otra especialidad o sede.`;
                reiniciarEspecialidad();
                irAPaso(2);              // primero se vuelve al paso 2 (esto limpia las alertas)...
                mostrarAlertaGeneral(msg); // ...y DESPUÉS se muestra el mensaje
                return false;
            }
            document.getElementById('medicosList').innerHTML = medicos.map((m) => `
                <div class="selection-card" data-id="${m.idUsuario}" data-nombre="${escaparHtml(m.nombreCompleto)}" onclick="seleccionarMedico(this)">
                    <div class="d-flex align-items-center gap-3">
                        <div class="icon-badge mb-0"><i class="fas fa-user-doctor"></i></div>
                        <div><strong>${escaparHtml(m.nombreCompleto)}</strong></div>
                    </div>
                </div>
            `).join('');
            return true;
        });
}

function seleccionarMedico(elemento) {
    marcarSeleccionUnica(elemento, '#medicosList .selection-card');
    wizard.medicoId = elemento.dataset.id;
    wizard.medicoNombre = elemento.dataset.nombre;
    validarPasoActual();
}

// ================================================================
// PASO 4: Fecha y hora (calendario dinámico)
// ================================================================
function cargarHorarios() {
    document.getElementById('medicoNombreDisplay').textContent = wizard.medicoNombre;

    return fetch(`/api/citas/horarios?medico=${wizard.medicoId}&sucursal=${wizard.sucursalId}&especialidad=${wizard.especialidadId}`)
        .then((r) => r.json())
        .then((horarios) => {
            wizard.horarios = horarios;
            if (horarios.length === 0 && wizard.esSeguimiento) {
                // CU-12: el seguimiento es con el mismo médico, no se puede cambiar de médico ni de sede.
                mostrarAlertaGeneral(`El médico ${wizard.medicoNombre} no tiene horarios disponibles por el momento. Intente de nuevo más tarde.`);
                return false;
            }
            if (horarios.length === 0) {
                // FA02, también aplica cuando el médico elegido no tiene horarios
                const msg = `No se encontraron horarios disponibles para la especialidad ${wizard.especialidadNombre} en la Sede ${wizard.sucursalNombre}. Por favor, seleccione otra especialidad o sede.`;
                reiniciarEspecialidad();
                irAPaso(2);
                mostrarAlertaGeneral(msg);
                return false;
            }
            pintarCalendario();
            return true;
        });
}

const NOMBRES_MES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
                     'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const NOMBRES_DIA_CORTO = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

// Calendario MENSUAL dinámico (CU-03, paso 4): los días con cupos del médico salen
// resaltados y se pueden elegir; los demás aparecen deshabilitados. Se puede navegar
// entre meses con las flechas, solo dentro del rango en el que el médico tiene horarios.
function pintarCalendario() {
    const fechas = wizard.horarios.map((h) => h.fecha).sort();
    const [anio, mes] = fechas[0].split('-').map(Number);
    wizard.mesVisible = new Date(anio, mes - 1, 1);   // abre en el primer mes con cupos

    wizard.fechaSeleccionada = null;
    wizard.horarioId = null;
    wizard.horaSeleccionada = null;
    document.getElementById('horariosContainer').style.display = 'none';

    renderMes();
    validarPasoActual();
}

function cambiarMes(delta) {
    const m = wizard.mesVisible;
    wizard.mesVisible = new Date(m.getFullYear(), m.getMonth() + delta, 1);
    renderMes();
}

function renderMes() {
    const anio = wizard.mesVisible.getFullYear();
    const mes = wizard.mesVisible.getMonth();
    const clave = `${anio}-${String(mes + 1).padStart(2, '0')}`;

    // fecha -> cantidad de cupos
    const cupos = {};
    wizard.horarios.forEach((h) => { cupos[h.fecha] = (cupos[h.fecha] || 0) + 1; });
    const fechas = Object.keys(cupos).sort();
    const primerMes = fechas[0].slice(0, 7);
    const ultimoMes = fechas[fechas.length - 1].slice(0, 7);

    const hoy = new Date();
    const hoyStr = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;

    const huecosInicio = new Date(anio, mes, 1).getDay();     // 0 = domingo
    const diasDelMes = new Date(anio, mes + 1, 0).getDate();

    let celdas = '';
    for (let i = 0; i < huecosInicio; i++) celdas += '<div class="calendar-day empty"></div>';
    for (let d = 1; d <= diasDelMes; d++) {
        const fecha = `${clave}-${String(d).padStart(2, '0')}`;
        const n = cupos[fecha] || 0;
        const clases = ['calendar-day'];
        clases.push(n > 0 ? 'has-slots' : 'disabled');
        if (fecha === hoyStr) clases.push('today');
        if (fecha === wizard.fechaSeleccionada) clases.push('selected');
        celdas += `
            <div class="${clases.join(' ')}" data-fecha="${fecha}" ${n > 0 ? 'onclick="seleccionarFecha(this)"' : ''}>
                <div class="day-number">${d}</div>
                ${n > 0 ? `<span class="day-available">${n} cupo${n === 1 ? '' : 's'}</span>` : ''}
            </div>`;
    }

    document.getElementById('calendarioContainer').innerHTML = `
        <div class="calendar-header">
            <button type="button" class="calendar-nav" onclick="cambiarMes(-1)" ${clave <= primerMes ? 'disabled' : ''} aria-label="Mes anterior">
                <i class="fas fa-chevron-left"></i>
            </button>
            <div class="mes-titulo">${NOMBRES_MES[mes]} ${anio}</div>
            <button type="button" class="calendar-nav" onclick="cambiarMes(1)" ${clave >= ultimoMes ? 'disabled' : ''} aria-label="Mes siguiente">
                <i class="fas fa-chevron-right"></i>
            </button>
        </div>
        <div class="calendar-weekdays">${NOMBRES_DIA_CORTO.map((n) => `<div>${n}</div>`).join('')}</div>
        <div class="calendar-grid">${celdas}</div>
        <div class="calendar-leyenda">
            <span><i class="leyenda-punto con-cupos"></i> Día con horarios disponibles</span>
            <span><i class="leyenda-punto sin-cupos"></i> Sin disponibilidad</span>
        </div>`;
}

function seleccionarFecha(elemento) {
    document.querySelectorAll('.calendar-day').forEach((el) => el.classList.remove('selected'));
    elemento.classList.add('selected');

    wizard.fechaSeleccionada = elemento.dataset.fecha;
    wizard.horarioId = null;
    wizard.horaSeleccionada = null;

    const horariosDelDia = wizard.horarios.filter((h) => h.fecha === wizard.fechaSeleccionada);
    document.getElementById('horariosList').innerHTML = horariosDelDia.map((h) => `
        <div class="time-slot" data-id="${h.idHorario}" data-hora="${h.horaInicio}" onclick="seleccionarHorario(this)">
            ${h.horaInicio.substring(0, 5)}
        </div>
    `).join('');
    document.getElementById('horariosContainer').style.display = 'block';
    validarPasoActual();
}

function seleccionarHorario(elemento) {
    marcarSeleccionUnica(elemento, '#horariosList .time-slot');
    wizard.horarioId = elemento.dataset.id;
    wizard.horaSeleccionada = elemento.dataset.hora;
    validarPasoActual();
}

// ================================================================
// PASO 5: Resumen y confirmación
// ================================================================
function pintarResumen() {
    document.getElementById('resumenSucursal').textContent = wizard.sucursalNombre;
    document.getElementById('resumenEspecialidad').textContent = wizard.especialidadNombre;
    document.getElementById('resumenMedico').textContent = wizard.medicoNombre;
    document.getElementById('resumenFecha').textContent = formatearFecha(wizard.fechaSeleccionada);
    document.getElementById('resumenHora').textContent = wizard.horaSeleccionada.substring(0, 5);
    document.getElementById('resumenMonto').textContent = 'Q150.00'; // monto fijo de consulta (ver application.properties)
    if (wizard.esSeguimiento) {
        document.getElementById('resumenTipo').textContent = wizard.tipoSeguimientoNombre || '-';
    }
}

function confirmarCita() {
    const motivo = document.getElementById('motivoConsulta').value.trim();
    const btnNext = document.getElementById('btnNext');
    btnNext.disabled = true;
    btnNext.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Reservando...';

    fetch('/api/citas/crear', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            sucursalId: Number(wizard.sucursalId),
            especialidadId: Number(wizard.especialidadId),
            medicoId: Number(wizard.medicoId),
            horarioId: Number(wizard.horarioId),
            motivo,
            idPacienteWalkIn: wizard.idPacienteWalkIn, // null salvo en el walk-in de CU-05 FA04
            idConsultaOrigen: wizard.idConsultaOrigen, // null salvo en el seguimiento de CU-08 FA02
            idTipoSeguimiento: wizard.idTipoSeguimiento,
            idPrioridad: wizard.idPrioridad,   // CU-12: solo en el seguimiento
        }),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            if (wizard.idConsultaOrigen) {
                // CU-08, FA02, paso 5: de vuelta al panel del médico con el
                // mensaje de confirmación (el mismo que ya armó el backend).
                window.location.href = '/medico?seguimientoCreado=' + encodeURIComponent(datos.mensaje);
                return;
            }
            if (wizard.idPacienteWalkIn) {
                // CU-05, FA04: el walk-in creado por Recepción queda
                // "Pendiente de pago" igual que cualquier otra cita; el
                // pago se hace en caja (CU-06), no con tarjeta en línea,
                // así que aquí no tiene sentido mandar al paciente a /pago.
                window.location.href = '/recepcion?walkinCreado=' + datos.idCita;
                return;
            }
            // Paso 9-10 del flujo normal (CU-03): mensaje de éxito y luego redirección al pago,
            // donde arranca el temporizador de 5 minutos.
            btnNext.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Redirigiendo al pago...';
            mostrarExitoCita('Su cita ha sido registrada exitosamente. Será redirigido al proceso de pago para confirmar la reserva.');
            setTimeout(() => { window.location.href = `/pago?idCita=${datos.idCita}`; }, 2500);
        })
        .catch((error) => {
            const mensajeError = error.mensaje || error.message || 'No se pudo registrar la cita. Intente de nuevo.';
            btnNext.disabled = false;
            btnNext.innerHTML = 'Confirmar Cita <i class="fas fa-arrow-right ms-2"></i>';
            if (wizard.esSeguimiento && error.status === 409) {
                // CU-12, FA01: conflicto de horario -> alerta y regreso automático al calendario
                wizard.horarioId = null;
                wizard.horaSeleccionada = null;
                wizard.fechaSeleccionada = null;
                cargarHorarios().then(() => {
                    irAPaso(4);
                    mostrarAlertaGeneral(mensajeError);
                });
                return;
            }
            mostrarAlertaGeneral(mensajeError);
        });
}

// ================================================================
// NAVEGACIÓN DEL WIZARD
// ================================================================
function avanzarPaso() {
    if (wizard.pasoActual === 5) {
        confirmarCita();
        return;
    }

    ocultarAlertaGeneral();
    const btnNext = document.getElementById('btnNext');
    btnNext.disabled = true;
    btnNext.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Cargando...';

    let promesa;
    if (wizard.pasoActual === 1) promesa = cargarEspecialidades();
    else if (wizard.pasoActual === 2) promesa = cargarMedicos();
    else if (wizard.pasoActual === 3) promesa = cargarHorarios();
    else promesa = Promise.resolve(true); // paso 4 -> 5 no necesita llamada al servidor

    promesa.then((continuar) => {
        btnNext.disabled = false;
        btnNext.innerHTML = 'Siguiente <i class="fas fa-arrow-right ms-2"></i>';
        if (continuar !== false) {
            irAPaso(wizard.pasoActual + 1);
        }
    }).catch(() => {
        btnNext.disabled = false;
        btnNext.innerHTML = 'Siguiente <i class="fas fa-arrow-right ms-2"></i>';
        mostrarAlertaGeneral('No se pudo conectar con el servidor. Intente de nuevo más tarde.');
    });
}

// FA04: "Volver" reinicia las selecciones de los pasos posteriores
function retrocederPaso() {
    if (wizard.pasoActual === 1) return;
    // CU-12, FA02: "Volver" en el calendario regresa a la consulta médica; no se guarda nada.
    if (wizard.esSeguimiento && wizard.pasoActual === 4) {
        window.location.href = '/medico';
        return;
    }
    const destino = wizard.pasoActual - 1;

    if (destino < 2) { wizard.especialidadId = null; wizard.especialidadNombre = null; }
    if (destino < 3) { wizard.medicoId = null; wizard.medicoNombre = null; }
    if (destino < 4) { wizard.horarios = []; wizard.fechaSeleccionada = null; wizard.horarioId = null; wizard.horaSeleccionada = null; }

    irAPaso(destino);
}

function irAPaso(numero) {
    wizard.pasoActual = numero;
    ocultarAlertaGeneral();

    document.querySelectorAll('.step-content').forEach((el) => el.classList.remove('active'));
    document.querySelector(`[data-step-content="${numero}"]`).classList.add('active');

    document.querySelectorAll('.step-item').forEach((item) => {
        const paso = Number(item.dataset.step);
        const circulo = item.querySelector('.step-circle');
        const etiqueta = item.querySelector('.step-label');
        circulo.classList.remove('active', 'completed');
        etiqueta.classList.remove('active', 'completed');
        if (paso < numero) { circulo.classList.add('completed'); etiqueta.classList.add('completed'); }
        else if (paso === numero) { circulo.classList.add('active'); etiqueta.classList.add('active'); }
    });

    document.getElementById('btnPrev').disabled = numero === 1;
    document.getElementById('btnNext').innerHTML = numero === 5
        ? 'Confirmar Cita <i class="fas fa-arrow-right ms-2"></i>'
        : 'Siguiente <i class="fas fa-arrow-right ms-2"></i>';

    if (numero === 5) pintarResumen();
    validarPasoActual();
}

// Habilita/deshabilita "Siguiente" según si el paso actual tiene una selección válida
function validarPasoActual() {
    const btnNext = document.getElementById('btnNext');
    let valido = false;

    switch (wizard.pasoActual) {
        case 1: valido = !!wizard.sucursalId; break;
        case 2: valido = !!wizard.especialidadId; break;
        case 3: valido = !!wizard.medicoId; break;
        case 4: valido = !!wizard.horarioId && (!wizard.esSeguimiento || !!wizard.idTipoSeguimiento); break;
        case 5: {
            const motivo = document.getElementById('motivoConsulta').value.trim();
            const esValido = motivo.length >= 10 && motivo.length <= 2000;
            document.getElementById('motivoError').style.display = motivo.length > 0 && !esValido ? 'block' : 'none';
            valido = esValido;
            break;
        }
    }
    btnNext.disabled = !valido;
}

// ================================================================
// Utilidades
// ================================================================
function marcarSeleccionUnica(elementoElegido, selectorTodos) {
    document.querySelectorAll(selectorTodos).forEach((el) => el.classList.remove('selected'));
    elementoElegido.classList.add('selected');
}

function mostrarAlertaGeneral(mensaje) {
    const el = document.getElementById('alertaGeneral');
    el.textContent = mensaje;
    el.classList.remove('d-none');
}

function mostrarExitoCita(mensaje) {
    ocultarAlertaGeneral();
    const el = document.getElementById('alertaExito');
    el.innerHTML = '<i class="fas fa-circle-check me-2"></i>' + mensaje;
    el.classList.remove('d-none');
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function ocultarAlertaGeneral() {
    document.getElementById('alertaGeneral').classList.add('d-none');
}

function formatearFecha(fechaISO) {
    const fecha = new Date(fechaISO + 'T00:00:00');
    return fecha.toLocaleDateString('es-GT', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
