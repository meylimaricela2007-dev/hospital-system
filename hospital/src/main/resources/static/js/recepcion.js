// ============================================================
// CU-05: Recepción y Verificación de Cita (recepcion/recepcion.html)
// ============================================================

// Guarda la última cita mostrada para poder refrescarla después de
// "Registrar llegada" o "Reasignar Médico" sin tener que repetir la
// búsqueda completa.
const estadoRecepcion = { idCitaActual: null };

function mensajeErrorDpi(dpi) {
    if (!dpi) return 'El campo DPI es obligatorio. Por favor, ingrese su número de DPI.';
    if (!/^\d+$/.test(dpi)) return 'El DPI debe contener únicamente números. No se permiten letras ni caracteres especiales.';
    if (dpi.length !== 13) return `El DPI debe contener exactamente 13 dígitos. Usted ingresó ${dpi.length} dígitos.`;
    return null;
}

document.addEventListener('DOMContentLoaded', () => {
    // Si venimos de crear un walk-in en el wizard de citas (CU-05 FA04),
    // citas.js nos manda de vuelta con ?walkinCreado=idCita.
    const params = new URLSearchParams(window.location.search);
    const walkinCreado = params.get('walkinCreado');
    if (walkinCreado) {
        const alerta = document.getElementById('alertaWalkinCreado');
        alerta.textContent = `La cita #${walkinCreado} fue creada correctamente. Búsquela por número de cita para continuar con la recepción.`;
        alerta.classList.remove('d-none');
        // Limpia el query param de la URL sin recargar la página.
        window.history.replaceState({}, '', '/recepcion');
    }

    // Alternar el input de búsqueda según "Por DPI" / "Por No. Cita" (FA02).
    document.querySelectorAll('input[name="tipoBusqueda"]').forEach((radio) => {
        radio.addEventListener('change', actualizarPlaceholderBusqueda);
    });

    document.getElementById('inputBusqueda').addEventListener('keyup', (e) => {
        if (e.key === 'Enter') ejecutarBusqueda();
    });
    document.getElementById('btnBuscar').addEventListener('click', ejecutarBusqueda);

    cargarSucursalesEmergencia();
    document.getElementById('emergenciaSucursal').addEventListener('change', cargarEspecialidadesEmergencia);
    document.getElementById('btnConfirmarEmergencia').addEventListener('click', confirmarEmergencia);

    // El modal de emergencia debe quedar limpio cada vez que se abre.
    document.getElementById('modalEmergencia').addEventListener('show.bs.modal', () => {
        document.getElementById('alertaEmergencia').classList.add('d-none');
        document.getElementById('emergenciaDpi').value = '';
        document.getElementById('emergenciaMotivo').value = '';
        document.getElementById('emergenciaTriaje').checked = false;
        document.getElementById('btnRegistrarPacienteEmergencia').classList.add('d-none');
    });
});

function actualizarPlaceholderBusqueda() {
    const esCita = document.getElementById('tipoCita').checked;
    const input = document.getElementById('inputBusqueda');
    input.value = '';
    if (esCita) {
        input.placeholder = 'Número de cita';
        input.maxLength = 10;
    } else {
        input.placeholder = 'DPI del paciente (13 dígitos)';
        input.maxLength = 13;
    }
}

// ================================================================
// Búsqueda (paso 2-3 del flujo normal, FA02)
// ================================================================
function ejecutarBusqueda() {
    const tipo = document.querySelector('input[name="tipoBusqueda"]:checked').value;
    const inputBusqueda = document.getElementById('inputBusqueda');
    const valor = inputBusqueda.value.trim();
    if (!valor) {
        inputBusqueda.classList.add('is-invalid');
        inputBusqueda.focus();
        pintarAlertaSimple('danger', 'Debe ingresar un número de cita o DPI para buscar.');
        return;
    }
    inputBusqueda.classList.remove('is-invalid');

    const btnTexto = document.getElementById('btnBuscarTexto');
    const btnSpinner = document.getElementById('btnBuscarSpinner');
    document.getElementById('inputBusqueda').addEventListener('input', function () {
        this.classList.remove('is-invalid');
    });
    document.getElementById('btnBuscar').disabled = true;
    btnSpinner.classList.remove('d-none');

    fetch(`/api/recepcion/buscar?tipo=${tipo}&valor=${encodeURIComponent(valor)}`)
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then(pintarResultadoBusqueda)
        .catch((error) => {
            if (error.status === 401) {
                pintarAlertaSimple('danger', 'Su sesión ha expirado por inactividad. Por favor, inicie sesión nuevamente.');
                setTimeout(() => { window.location.href = '/login-admin'; }, 3000);
                return;
            }
            pintarAlertaSimple('danger', error.mensaje || error.message || 'No se pudo conectar con el servidor. Intente de nuevo más tarde.');
        })
        .finally(() => {
            document.getElementById('btnBuscar').disabled = false;
            btnSpinner.classList.add('d-none');
        });
}

function pintarResultadoBusqueda(data) {
    const contenedor = document.getElementById('resultadoBusqueda');
    estadoRecepcion.idCitaActual = data.cita ? data.cita.idCita : null;

    switch (data.resultado) {
        case 'SIN_RESULTADOS':
        case 'NO_ES_PACIENTE':
            pintarAlertaSimple('danger', data.mensaje);
            return;

        case 'PACIENTE_NO_REGISTRADO': // FA03
            contenedor.innerHTML = `
                <div class="alert alert-warning">
                    <div class="fw-semibold mb-1">${escaparHtml(data.mensaje)}</div>
                    <div class="small mb-3">${escaparHtml(data.subMensaje || '')}</div>
                    <a class="btn btn-teal" target="_blank" href="/registro?dpi=${encodeURIComponent(document.getElementById('inputBusqueda').value.trim())}">
                        <i class="fas fa-user-plus me-2"></i>Registrar Paciente
                    </a>
                </div>`;
            return;

        case 'PACIENTE_SIN_CITAS': // FA04
            contenedor.innerHTML = `
                <div class="alert alert-warning">
                    <div class="fw-semibold mb-1">${escaparHtml(data.mensaje)}</div>
                    <div class="small mb-3">${escaparHtml(data.subMensaje || '')}</div>
                    <button class="btn btn-teal" onclick="irAWalkIn(${data.idPacienteEncontrado}, '${escaparAtributo(data.nombrePacienteEncontrado)}')">
                        <i class="fas fa-calendar-plus me-2"></i>Nueva Cita (Walk-in)
                    </button>
                </div>`;
            return;

        case 'CITA_CANCELADA': // FA06
            contenedor.innerHTML = `
                ${tarjetaCita(data.cita)}
                <div class="alert alert-danger mt-3">
                    <div class="mb-3">${escaparHtml(data.mensaje)}</div>
                    <button class="btn btn-accent" onclick="irAWalkIn(${data.cita.idPaciente}, '${escaparAtributo(data.cita.nombrePaciente)}')">
                        <i class="fas fa-calendar-plus me-2"></i>Nueva Cita
                    </button>
                </div>`;
            return;

        case 'CITA_PENDIENTE_PAGO': // FA05
            contenedor.innerHTML = `
                ${tarjetaCita(data.cita)}
                <div class="alert alert-warning mt-3">${escaparHtml(data.mensaje)}</div>`;
            return;

        case 'CITA_ENCONTRADA': // flujo normal, paso 4 en adelante
            contenedor.innerHTML = tarjetaCita(data.cita, true);
            return;

        default:
            pintarAlertaSimple('danger', 'Respuesta inesperada del servidor.');
    }
}

function pintarAlertaSimple(tipo, mensaje) {
    document.getElementById('resultadoBusqueda').innerHTML =
        `<div class="alert alert-${tipo}">${escaparHtml(mensaje)}</div>`;
}

/**
 * Arma la tarjeta con los datos de la cita (paso 4 del flujo normal):
 * nombre del paciente, estado, prioridad, número de cita, especialidad,
 * sucursal, fecha, motivo de consulta y hora de llegada. "conAcciones"
 * controla si se muestran los botones (no aplica para FA05/FA06, donde
 * la cita solo se exhibe como referencia).
 */
function tarjetaCita(cita, conAcciones) {
    const esEmergencia = !!cita.esEmergencia;
    const badgeEstado = badgeParaEstado(cita.estadoCita);
    const indicadorLlegada = cita.horaLlegada
        ? `<div class="indicador-llegada mt-2">
                <i class="fas ${esEmergencia ? 'fa-truck-medical' : 'fa-circle-check'}"></i>
                ${esEmergencia ? 'EMERGENCIA — pasar directamente a signos vitales'
                                : 'Llegada registrada — esperando llamado de enfermería'}
           </div>`
        : '';

    let botones = '';
    if (conAcciones) {
        botones = '<div class="d-flex flex-wrap gap-2 mt-3">';
        if (cita.estadoCita === 'Confirmada') {
            botones += `<button class="btn btn-accent" onclick="registrarLlegada(${cita.idCita})">
                            <i class="fas fa-right-to-bracket me-2"></i>Registrar llegada
                         </button>`;
        }
        if (cita.estadoCita === 'Confirmada' || cita.estadoCita === 'Paciente Presente') {
            botones += `<button class="btn btn-outline-teal" onclick="abrirReasignarMedico(${cita.idCita})">
                            <i class="fas fa-user-doctor me-2"></i>Reasignar Médico
                         </button>`;
        }
        if (esEmergencia) {
            // CORREGIDO: esta condición pedía estadoCita === 'Confirmada',
            // pero una emergencia registrada por FA01 nunca pasa por
            // "Confirmada" (RecepcionService.registrarEmergencia la deja
            // directo en "Paciente Presente"), así que el botón nunca se
            // mostraba. Ahora se muestra siempre que la cita sea de
            // emergencia, como acceso directo al panel de Enfermería (CU-07).
            botones += `<button class="btn" style="background: var(--color-danger); color:#fff;" onclick="irASignosVitales()">
                            <i class="fas fa-kit-medical me-2"></i>Signos Vitales (Urgente)
                         </button>`;
        }
        botones += '</div>';
    }

    return `
        <div class="hh-card hh-card-recepcion ${esEmergencia ? 'es-emergencia' : ''}" id="tarjetaCita${cita.idCita}">
            <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2">
                <div>
                    <h3 class="mb-1">
                        ${escaparHtml(cita.nombrePaciente)}
                        ${esEmergencia ? '<span class="badge badge-emergencia ms-2">EMERGENCIA</span>' : ''}
                    </h3>
                    <span class="badge ${badgeEstado}">${escaparHtml(cita.estadoCita)}</span>
                </div>
                <span class="text-mono text-muted">No. Cita: ${cita.idCita}</span>
            </div>
            <div class="row g-2 small">
                <div class="col-md-6"><strong>Especialidad:</strong> ${escaparHtml(cita.especialidad)}</div>
                <div class="col-md-6"><strong>Sucursal:</strong> ${escaparHtml(cita.sucursal)}</div>
                <div class="col-md-6"><strong>Médico:</strong> ${escaparHtml(cita.nombreMedico)}</div>
                <div class="col-md-6"><strong>Fecha:</strong> ${cita.fechaCita} ${(cita.horaCita || '').substring(0, 5)}</div>
                <div class="col-12"><strong>Motivo de consulta:</strong> ${escaparHtml(cita.motivoConsulta)}</div>
            </div>
            ${(esEmergencia || cita.prioridad) ? `<div class="col-md-6"><strong>Prioridad:</strong> ${esEmergencia ? 'EMERGENCIA' : escaparHtml(cita.prioridad)}</div>` : ''}
            ${cita.horaLlegada ? `<div class="col-md-6"><strong>Hora de llegada:</strong> ${String(cita.horaLlegada).substring(11, 16)}</div>` : ''}
            ${indicadorLlegada}
            ${botones}
        </div>`;
}

function badgeParaEstado(estado) {
    if (estado === 'Confirmada' || estado === 'Paciente Presente') return 'badge-soft-success';
    if (estado === 'Pendiente de pago') return 'badge-soft-warning';
    if (estado === 'Cancelada' || estado === 'Expirada') return 'badge-soft-danger';
    return 'badge-soft-info';
}

function irASignosVitales() {
    // CU-07 ya existe: el panel de Enfermería lee directamente las citas en
    // estado "Paciente Presente", así que no hace falta mandar ningún dato
    // por la URL; el paciente ya aparece ahí (priorizado por ser emergencia).
    window.location.href = '/enfermeria';
}

// CU-05, FA04: navega al wizard de CU-03 en modo walk-in para el paciente encontrado.
function irAWalkIn(idPaciente, nombrePaciente) {
    window.location.href = `/citas/agendar?idPaciente=${idPaciente}&nombrePaciente=${encodeURIComponent(nombrePaciente)}`;
}

// ================================================================
// Paso 8 del flujo normal: registrar llegada
// ================================================================
function registrarLlegada(idCita) {
    fetch(`/api/recepcion/citas/${idCita}/registrar-llegada`, { method: 'POST' })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos; // FA09
            return datos;
        })
        .then((datos) => {
            pintarResultadoBusqueda({ resultado: 'CITA_ENCONTRADA', cita: datos.cita });
            const contenedor = document.getElementById('resultadoBusqueda');
            contenedor.insertAdjacentHTML('afterbegin',
                `<div class="alert alert-success">${escaparHtml(datos.mensaje)}</div>`);
        })
        .catch((error) => {
            // FA09: "Operación no permitida" / "Error al registrar la llegada"
            pintarAlertaSimple('danger', error.mensaje || error.message || 'Error al registrar la llegada.');
        });
}

// ================================================================
// FA01: registrar emergencia
// ================================================================
function cargarSucursalesEmergencia() {
    fetch('/api/catalogos/sucursales')
        .then((r) => r.json())
        .then((sucursales) => {
            const select = document.getElementById('emergenciaSucursal');
            select.innerHTML = '<option value="">Seleccione una sede...</option>' +
                sucursales.map((s) => `<option value="${s.id}">${escaparHtml(s.nombre)}</option>`).join('');
        });
}

function cargarEspecialidadesEmergencia() {
    const idSucursal = document.getElementById('emergenciaSucursal').value;
    const select = document.getElementById('emergenciaEspecialidad');
    if (!idSucursal) {
        select.disabled = true;
        select.innerHTML = '<option value="">Seleccione una sede primero</option>';
        return;
    }
    fetch(`/api/catalogos/especialidades-por-sucursal/${idSucursal}`)
        .then((r) => r.json())
        .then((especialidades) => {
            select.disabled = false;
            select.innerHTML = '<option value="">Seleccione una especialidad...</option>' +
                especialidades.map((e) => `<option value="${e.id}">${escaparHtml(e.nombre)}</option>`).join('');
        });
}

function confirmarEmergencia() {
    const dpi = document.getElementById('emergenciaDpi').value.trim();
    const idSucursal = document.getElementById('emergenciaSucursal').value;
    const idEspecialidad = document.getElementById('emergenciaEspecialidad').value;
    const motivoConsulta = document.getElementById('emergenciaMotivo').value.trim();
    const alerta = document.getElementById('alertaEmergencia');
    alerta.classList.add('d-none');
    document.getElementById('btnRegistrarPacienteEmergencia').classList.add('d-none');

    const errorDpi = mensajeErrorDpi(dpi);
    if (errorDpi) {
        alerta.textContent = errorDpi;
        alerta.classList.remove('d-none');
        return;
    }
    if (!idSucursal || !idEspecialidad) {
        alerta.textContent = 'Debe seleccionar sede y especialidad.';
        alerta.classList.remove('d-none');
        return;
    }

    if (!document.getElementById('emergenciaTriaje').checked) {
        alerta.textContent = 'Debe clasificar la prioridad según triaje antes de registrar la emergencia.';
        alerta.classList.remove('d-none');
        return;
    }

    const btn = document.getElementById('btnConfirmarEmergencia');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Registrando...';

    fetch('/api/recepcion/emergencia', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dpi, idSucursal: Number(idSucursal), idEspecialidad: Number(idEspecialidad), motivoConsulta }),
    })
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            bootstrap.Modal.getInstance(document.getElementById('modalEmergencia')).hide();
            pintarResultadoBusqueda({ resultado: 'CITA_ENCONTRADA', cita: datos.cita });
            document.getElementById('resultadoBusqueda').insertAdjacentHTML('afterbegin',
                `<div class="alert" style="background: var(--color-danger-bg); color: var(--color-danger); font-weight:600;">${escaparHtml(datos.mensaje)}</div>`);
        })
        .catch((error) => {
            const mensaje = error.mensaje || error.message || 'No se pudo registrar la emergencia.';
            alerta.textContent = mensaje;
            alerta.classList.remove('d-none');

            // Si el DPI no está registrado, se ofrece registrar al paciente sin salir del modal
            const noRegistrado = /no está registrado|No se encontró ningún paciente/i.test(mensaje);
            const btnRegistrar = document.getElementById('btnRegistrarPacienteEmergencia');
            btnRegistrar.href = `/registro?dpi=${encodeURIComponent(dpi)}`;
            btnRegistrar.classList.toggle('d-none', !noRegistrado);
        })
        .finally(() => {
            btn.disabled = false;
            btn.innerHTML = '<i class="fas fa-truck-medical me-2"></i>Registrar EMERGENCIA';
        });
}

// ================================================================
// FA07: reasignación de médico
// ================================================================
function abrirReasignarMedico(idCita) {
    const modalEl = document.getElementById('modalReasignar');
    const modal = new bootstrap.Modal(modalEl);
    document.getElementById('alertaReasignar').classList.add('d-none');
    document.getElementById('reasignarNota').value = '';
    document.getElementById('resumenReasignacion').innerHTML = '<span class="text-muted">Cargando...</span>';
    document.getElementById('listaMedicosReasignar').innerHTML = '';
    document.getElementById('btnConfirmarReasignar').disabled = true;
    document.getElementById('btnConfirmarReasignar').dataset.idCita = idCita;
    modal.show();

    fetch(`/api/recepcion/citas/${idCita}/reasignacion`)
        .then(async (r) => {
            const datos = await r.json().catch(() => ({}));
            if (!r.ok) throw datos;
            return datos;
        })
        .then((datos) => {
            const c = datos.resumenCita;
            document.getElementById('resumenReasignacion').innerHTML = `
                <div class="small">
                    <div><strong>Paciente:</strong> ${escaparHtml(c.nombrePaciente)}</div>
                    <div><strong>Fecha:</strong> ${c.fechaCita} ${(c.horaCita || '').substring(0, 5)}</div>
                    <div><strong>Especialidad:</strong> ${escaparHtml(c.especialidad)}</div>
                    <div><strong>Sede:</strong> ${escaparHtml(c.sucursal)}</div>
                    <div><strong>Médico actual:</strong> ${escaparHtml(c.nombreMedico)}</div>
                </div>`;

            if (datos.medicosDisponibles.length === 0) {
                document.getElementById('listaMedicosReasignar').innerHTML =
                    '<div class="alert alert-warning mb-0">No hay otros médicos disponibles para esta especialidad y sede.</div>';
                return;
            }
            document.getElementById('listaMedicosReasignar').innerHTML = datos.medicosDisponibles.map((m) => `
                <div class="selection-card" data-id="${m.idUsuario}" onclick="seleccionarMedicoReasignar(this)">
                    <strong>${escaparHtml(m.nombreCompleto)}</strong>
                </div>
            `).join('');
        })
        .catch((error) => {
            document.getElementById('alertaReasignar').textContent =
                error.mensaje || error.message || 'No se pudo cargar la información de reasignación.';
            document.getElementById('alertaReasignar').classList.remove('d-none');
        });
}

function seleccionarMedicoReasignar(elemento) {
    document.querySelectorAll('#listaMedicosReasignar .selection-card').forEach((el) => el.classList.remove('selected'));
    elemento.classList.add('selected');
    document.getElementById('btnConfirmarReasignar').disabled = false;
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('btnConfirmarReasignar').addEventListener('click', function () {
        const idCita = this.dataset.idCita;
        const seleccionado = document.querySelector('#listaMedicosReasignar .selection-card.selected');
        if (!seleccionado) return;

        const btn = this;
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Guardando...';

        fetch(`/api/recepcion/citas/${idCita}/reasignar-medico`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                idMedicoNuevo: Number(seleccionado.dataset.id),
                notaMotivo: document.getElementById('reasignarNota').value.trim(),
            }),
        })
            .then(async (r) => {
                const datos = await r.json().catch(() => ({}));
                if (!r.ok) throw datos;
                return datos;
            })
            .then(() => {
                bootstrap.Modal.getInstance(document.getElementById('modalReasignar')).hide();
                // El documento pide: "muestra 'Médico reasignado correctamente' y
                // redirige a la lista de citas". Aquí la "lista de citas" es la
                // tarjeta que ya se estaba mostrando, así que solo la refrescamos.
                fetch(`/api/recepcion/buscar?tipo=CITA&valor=${idCita}`)
                    .then((r) => r.json())
                    .then((datosCita) => {
                        pintarResultadoBusqueda(datosCita);
                        document.getElementById('resultadoBusqueda').insertAdjacentHTML('afterbegin',
                            '<div class="alert alert-success">Médico reasignado correctamente</div>');
                    });
            })
            .catch((error) => {
                document.getElementById('alertaReasignar').textContent =
                    error.mensaje || error.message || 'No se pudo reasignar el médico.';
                document.getElementById('alertaReasignar').classList.remove('d-none');
            })
            .finally(() => {
                btn.disabled = false;
                btn.innerHTML = '<i class="fas fa-check me-2"></i>Confirmar Reasignación';
            });
    });
});

// ================================================================
// Utilidades
// ================================================================
function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}

// Igual que escaparHtml, pero seguro para usar dentro de comillas simples
// de un atributo onclick="..." (evita que un nombre con comilla rompa el HTML).
function escaparAtributo(texto) {
    return (texto ?? '').replace(/'/g, '&#39;').replace(/"/g, '&quot;');
}
