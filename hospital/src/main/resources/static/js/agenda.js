// ============================================================
// CU-16: Agenda Médica (medico/agenda.html)
// Calendario (FullCalendar) + eventos personales + panel de tareas.
// ============================================================
const estadoAgenda = {
    calendario: null,
    fechaSeleccionada: hoyIso(),
    filtroTareas: 'pendientes',
    todasLasFechas: false,
    itemActual: null,        // evento/cita abierto en el detalle
    catalogos: { tiposEvento: [], prioridades: [] }
};

document.addEventListener('DOMContentLoaded', async () => {
    await cargarCatalogos();
    iniciarCalendario();
    cargarTareas();

    document.getElementById('btnNuevoEvento').addEventListener('click', () => abrirFormularioEvento(null));
    document.getElementById('btnTogglePanelTareas').addEventListener('click', alternarPanelTareas);
    document.getElementById('formEvento').addEventListener('submit', guardarEvento);
    document.getElementById('ev_todoElDia').addEventListener('change', alternarTodoElDia);
    document.getElementById('btnEditarEvento').addEventListener('click', () => {
        bootstrap.Modal.getInstance(document.getElementById('modalDetalle')).hide();
        abrirFormularioEvento(estadoAgenda.itemActual);
    });
    document.getElementById('btnEliminarEvento').addEventListener('click', () => {
        bootstrap.Modal.getInstance(document.getElementById('modalDetalle')).hide();
        new bootstrap.Modal(document.getElementById('modalConfirmarEliminacion')).show();
    });
    document.getElementById('btnConfirmarEliminar').addEventListener('click', eliminarEvento);

    document.getElementById('btnNuevaTarea').addEventListener('click', abrirFormularioTarea);
    document.getElementById('formTarea').addEventListener('submit', guardarTarea);
    document.getElementById('chkTodasFechas').addEventListener('change', (e) => {
        estadoAgenda.todasLasFechas = e.target.checked;
        cargarTareas();
    });
    document.querySelectorAll('#filtrosTareas button').forEach((btn) => btn.addEventListener('click', () => {
        estadoAgenda.filtroTareas = btn.dataset.filtro;
        document.querySelectorAll('#filtrosTareas button').forEach((b) => {
            b.className = 'btn btn-sm ' + (b === btn ? 'btn-teal' : 'btn-outline-teal');
        });
        cargarTareas();
    }));
});

// ---------------------------------------------------------------- catálogos

async function cargarCatalogos() {
    try {
        const respuesta = await fetch('/api/agenda/catalogos');
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudieron cargar los catálogos.');
        estadoAgenda.catalogos = datos;
        document.getElementById('ev_idTipoEvento').innerHTML = '<option value="">-- Seleccione --</option>' +
            datos.tiposEvento.map((t) => `<option value="${t.id}">${escaparHtml(t.nombre)}</option>`).join('');
        document.getElementById('ta_idPrioridad').innerHTML = '<option value="">-- Seleccione --</option>' +
            datos.prioridades.map((p) => `<option value="${p.id}">${escaparHtml(p.nombre)}</option>`).join('');
    } catch (error) {
        mostrarError(error.message);
    }
}

// ---------------------------------------------------------------- calendario (pasos 1-2, FA05)

function iniciarCalendario() {
    estadoAgenda.calendario = new FullCalendar.Calendar(document.getElementById('calendario'), {
        locale: 'es',
        initialView: 'dayGridMonth',
        height: 'auto',
        firstDay: 0,
        nowIndicator: true,
        headerToolbar: { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,timeGridDay' },
        buttonText: { today: 'Hoy', month: 'Mes', week: 'Semana', day: 'Día' },
        slotMinTime: '06:00:00',
        slotMaxTime: '21:00:00',
        events: obtenerItems,
        dateClick: (info) => seleccionarFecha(info.dateStr.substring(0, 10)),
        eventClick: (info) => { info.jsEvent.preventDefault(); abrirDetalle(info.event); }
    });
    estadoAgenda.calendario.render();
}

async function obtenerItems(info, exito, fallo) {
    try {
        // end de FullCalendar es exclusivo: se resta un día para el rango del servidor
        const hasta = sumarDias(info.endStr.substring(0, 10), -1);
        const respuesta = await fetch(`/api/agenda/calendario?desde=${info.startStr.substring(0, 10)}&hasta=${hasta}`);
        const items = await respuesta.json();
        if (!respuesta.ok) throw new Error(items.message || 'No se pudo cargar la agenda.');

        exito(items.map((it) => ({
            id: `${it.tipo}-${it.id}`,
            title: it.titulo,
            start: it.todoElDia ? it.inicio.substring(0, 10) : it.inicio,
            end: it.todoElDia ? sumarDias(it.fin.substring(0, 10), 1) : it.fin, // en "todo el día" el fin es exclusivo
            allDay: it.todoElDia,
            color: it.color,
            extendedProps: it
        })));
    } catch (error) {
        mostrarError(error.message);
        fallo(error);
    }
}

function seleccionarFecha(fechaIso) {
    estadoAgenda.fechaSeleccionada = fechaIso;
    if (!estadoAgenda.todasLasFechas) cargarTareas();
}

// ---------------------------------------------------------------- detalle (FA01)

function abrirDetalle(evento) {
    const it = evento.extendedProps;
    estadoAgenda.itemActual = it;
    document.getElementById('detTitulo').textContent = it.titulo;
    document.getElementById('detBadge').innerHTML = it.tipo === 'evento'
        ? `<span class="badge" style="background:#8b5cf6;color:#fff">${escaparHtml(it.tipoEvento)}</span>`
        : `<span class="badge" style="background:${it.color};color:#fff">${escaparHtml(it.estado)}</span>`;
    document.getElementById('detInicio').textContent = formatoLargo(it.inicio, it.todoElDia);
    document.getElementById('detFin').textContent = formatoLargo(it.fin, it.todoElDia);
    document.getElementById('detBloquePaciente').classList.toggle('d-none', it.tipo !== 'cita');
    document.getElementById('detPaciente').textContent = it.paciente || '';
    document.getElementById('detEtiquetaDescripcion').textContent = it.tipo === 'cita' ? 'Motivo de consulta' : 'Descripción';
    document.getElementById('detDescripcion').textContent = it.descripcion || '—';
    // solo los eventos se editan o eliminan; las citas se gestionan desde el panel de consulta
    document.getElementById('detAcciones').classList.toggle('d-none', it.tipo !== 'evento');
    new bootstrap.Modal(document.getElementById('modalDetalle')).show();
}

// ---------------------------------------------------------------- formulario de evento (pasos 3-8, FA07)

function abrirFormularioEvento(item) {
    limpiarErrores('formEvento');
    document.getElementById('alertaEventoError').classList.add('d-none');
    const editando = item && item.tipo === 'evento';

    document.getElementById('tituloModalEvento').textContent = editando ? 'Editar Evento' : 'Nuevo Evento';
    document.getElementById('ev_idEvento').value = editando ? item.id : '';
    document.getElementById('ev_titulo').value = editando ? item.titulo : '';
    document.getElementById('ev_descripcion').value = editando ? (item.descripcion || '') : '';
    document.getElementById('ev_idTipoEvento').value = editando ? item.idTipoEvento : '';
    document.getElementById('ev_todoElDia').checked = editando ? item.todoElDia : false;
    alternarTodoElDia();

    const todoDia = editando && item.todoElDia;
    const fecha = estadoAgenda.fechaSeleccionada;
    document.getElementById('ev_fechaInicio').value = editando
        ? (todoDia ? item.inicio.substring(0, 10) : item.inicio.substring(0, 16))
        : `${fecha}T09:00`;
    document.getElementById('ev_fechaFin').value = editando
        ? (todoDia ? item.fin.substring(0, 10) : item.fin.substring(0, 16))
        : `${fecha}T10:00`;
    alternarTodoElDia(true);
    new bootstrap.Modal(document.getElementById('modalEvento')).show();
}

// RN-CU14-01: con "Todo el día" los campos pasan de datetime-local a date
function alternarTodoElDia(conservarValores) {
    const todoDia = document.getElementById('ev_todoElDia').checked;
    ['ev_fechaInicio', 'ev_fechaFin'].forEach((id) => {
        const input = document.getElementById(id);
        const valor = input.value;
        const tipoNuevo = todoDia ? 'date' : 'datetime-local';
        if (input.type !== tipoNuevo) {
            input.type = tipoNuevo;
            if (valor) {
                const fecha = valor.substring(0, 10);
                input.value = todoDia ? fecha : `${fecha}T${id === 'ev_fechaInicio' ? '09:00' : '10:00'}`;
            }
        }
    });
}

async function guardarEvento(e) {
    e.preventDefault();
    limpiarErrores('formEvento');
    document.getElementById('alertaEventoError').classList.add('d-none');

    const todoDia = document.getElementById('ev_todoElDia').checked;
    const completar = (v) => (todoDia && v ? `${v}T00:00` : v);
    const cuerpo = {
        titulo: document.getElementById('ev_titulo').value.trim(),
        descripcion: document.getElementById('ev_descripcion').value.trim() || null,
        idTipoEvento: document.getElementById('ev_idTipoEvento').value ? Number(document.getElementById('ev_idTipoEvento').value) : null,
        todoElDia: todoDia,
        fechaInicio: completar(document.getElementById('ev_fechaInicio').value) || null,
        fechaFin: completar(document.getElementById('ev_fechaFin').value) || null
    };
    const id = document.getElementById('ev_idEvento').value;

    const btn = document.getElementById('btnGuardarEvento');
    btn.disabled = true;
    try {
        const respuesta = await fetch(id ? `/api/agenda/eventos/${id}` : '/api/agenda/eventos', {
            method: id ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(cuerpo)
        });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) {
            if (datos.errors) pintarErrores(datos.errors, 'ev_'); // FA03
            throw new Error(datos.message || 'No se pudo guardar el evento.');
        }
        bootstrap.Modal.getInstance(document.getElementById('modalEvento')).hide();
        mostrarExito(datos.mensaje);
        estadoAgenda.calendario.refetchEvents();
    } catch (error) {
        const el = document.getElementById('alertaEventoError');
        el.textContent = error.message;
        el.classList.remove('d-none');
    } finally {
        btn.disabled = false;
    }
}

async function eliminarEvento() {
    const it = estadoAgenda.itemActual;
    const modal = bootstrap.Modal.getInstance(document.getElementById('modalConfirmarEliminacion'));
    try {
        const respuesta = await fetch(`/api/agenda/eventos/${it.id}`, { method: 'DELETE' });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo eliminar el evento.');
        modal.hide();
        mostrarExito(datos.mensaje);
        estadoAgenda.calendario.refetchEvents();
    } catch (error) {
        modal.hide();
        mostrarError(error.message);
    }
}

// ---------------------------------------------------------------- panel de tareas (FA06)

function alternarPanelTareas() {
    const panel = document.getElementById('columnaTareas');
    const visible = panel.classList.toggle('d-none') === false;
    document.getElementById('columnaCalendario').className = visible ? 'col-lg-8' : 'col-12';
    setTimeout(() => estadoAgenda.calendario.updateSize(), 50);
}

async function cargarTareas() {
    const params = new URLSearchParams({ filtro: estadoAgenda.filtroTareas });
    if (!estadoAgenda.todasLasFechas) params.set('fecha', estadoAgenda.fechaSeleccionada);

    document.getElementById('tareasFechaTexto').textContent = estadoAgenda.todasLasFechas
        ? 'Todas las fechas'
        : new Date(estadoAgenda.fechaSeleccionada + 'T00:00:00').toLocaleDateString('es-GT', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

    try {
        const respuesta = await fetch(`/api/agenda/tareas?${params}`);
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudieron cargar las tareas.');
        document.getElementById('tareasConteo').textContent = `${datos.pendientes} pend. · ${datos.completadas} compl.`;
        pintarTareas(datos.tareas);
    } catch (error) {
        mostrarError(error.message);
    }
}

function pintarTareas(tareas) {
    const lista = document.getElementById('listaTareas');
    if (tareas.length === 0) {
        lista.innerHTML = '<p class="text-muted small mb-0">No hay tareas para mostrar.</p>';
        return;
    }
    const color = { Alta: 'var(--color-danger)', Normal: 'var(--color-warning)', Baja: 'var(--color-success)' };
    lista.innerHTML = tareas.map((t) => `
        <div class="d-flex align-items-start gap-2 py-2 border-bottom">
            <input class="form-check-input mt-1" type="checkbox" ${t.completada ? 'checked' : ''}
                   onchange="cambiarCompletada(${t.idTarea})" aria-label="Completar tarea">
            <div class="flex-grow-1">
                <div class="${t.completada ? 'text-decoration-line-through text-muted' : 'fw-semibold'}">${escaparHtml(t.titulo)}</div>
                ${t.descripcion ? `<div class="small text-muted">${escaparHtml(t.descripcion)}</div>` : ''}
                <div class="small">
                    <i class="fas fa-flag me-1" style="color:${color[t.prioridad] || 'inherit'}"></i>${escaparHtml(t.prioridad)}
                    · <i class="far fa-calendar me-1"></i>${t.fechaLimite ? new Date(t.fechaLimite + 'T00:00:00').toLocaleDateString('es-GT') : '—'}
                    · <span class="badge ${t.completada ? 'badge-soft-success' : 'badge-soft-warning'}">${t.completada ? 'Completada' : 'Pendiente'}</span>
                </div>
            </div>
            <button class="btn btn-sm btn-outline-danger" onclick="eliminarTarea(${t.idTarea})" title="Eliminar tarea">
                <i class="fas fa-trash"></i>
            </button>
        </div>`).join('');
}

async function cambiarCompletada(id) {
    try {
        const respuesta = await fetch(`/api/agenda/tareas/${id}/completada`, { method: 'PATCH' });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo actualizar la tarea.');
        cargarTareas();
    } catch (error) {
        mostrarError(error.message);
        cargarTareas();
    }
}

async function eliminarTarea(id) {
    try {
        const respuesta = await fetch(`/api/agenda/tareas/${id}`, { method: 'DELETE' });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo eliminar la tarea.');
        mostrarExito(datos.mensaje);
        cargarTareas();
    } catch (error) {
        mostrarError(error.message);
    }
}

function abrirFormularioTarea() {
    limpiarErrores('formTarea');
    document.getElementById('alertaTareaError').classList.add('d-none');
    document.getElementById('formTarea').reset();
    document.getElementById('ta_fechaLimite').value = estadoAgenda.fechaSeleccionada >= hoyIso() ? estadoAgenda.fechaSeleccionada : hoyIso();
    document.getElementById('ta_fechaLimite').min = hoyIso();
    new bootstrap.Modal(document.getElementById('modalTarea')).show();
}

async function guardarTarea(e) {
    e.preventDefault();
    limpiarErrores('formTarea');
    document.getElementById('alertaTareaError').classList.add('d-none');

    const prioridad = document.getElementById('ta_idPrioridad').value;
    const cuerpo = {
        titulo: document.getElementById('ta_titulo').value.trim(),
        descripcion: document.getElementById('ta_descripcion').value.trim() || null,
        idPrioridad: prioridad ? Number(prioridad) : null,
        fechaLimite: document.getElementById('ta_fechaLimite').value || null
    };

    const btn = document.getElementById('btnGuardarTarea');
    btn.disabled = true;
    try {
        const respuesta = await fetch('/api/agenda/tareas', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(cuerpo)
        });
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) {
            if (datos.errors) pintarErrores(datos.errors, 'ta_');
            throw new Error(datos.message || 'No se pudo guardar la tarea.');
        }
        bootstrap.Modal.getInstance(document.getElementById('modalTarea')).hide();
        mostrarExito(datos.mensaje);
        cargarTareas();
    } catch (error) {
        const el = document.getElementById('alertaTareaError');
        el.textContent = error.message;
        el.classList.remove('d-none');
    } finally {
        btn.disabled = false;
    }
}

// ---------------------------------------------------------------- utilidades

function hoyIso() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function sumarDias(fechaIso, dias) {
    const d = new Date(fechaIso + 'T00:00:00');
    d.setDate(d.getDate() + dias);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function formatoLargo(iso, soloFecha) {
    const f = new Date(iso);
    return soloFecha
        ? f.toLocaleDateString('es-GT', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
        : f.toLocaleString('es-GT', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

// Los mensajes del servidor traen el nombre del campo del DTO (titulo, fechaInicio...); los inputs llevan un prefijo.
function pintarErrores(errores, prefijo) {
    Object.entries(errores).forEach(([campo, mensaje]) => {
        const input = document.getElementById(prefijo + campo);
        const aviso = document.querySelector(`[data-error-for="${prefijo}${campo}"]`);
        if (input) input.classList.add('is-invalid');
        if (aviso) aviso.textContent = mensaje;
    });
}

function limpiarErrores(idFormulario) {
    const form = document.getElementById(idFormulario);
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
    form.querySelectorAll('[data-error-for]').forEach((el) => { el.textContent = ''; });
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
