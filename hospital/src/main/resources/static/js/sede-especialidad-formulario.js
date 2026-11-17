// ============================================================
// CU-13: formulario "Asignar Especialidad" (admin/sede-especialidad-formulario.html)
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
    await cargarCatalogos();
    document.getElementById('formAsignacion').addEventListener('submit', asignar);
});

async function cargarCatalogos() {
    try {
        const respuesta = await fetch('/api/sede-especialidad/catalogos');
        const datos = await respuesta.json();
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudieron cargar los catálogos.');

        document.getElementById('idSucursal').innerHTML =
            '<option value="">-- Seleccione una sede --</option>' +
            datos.sedes.map((s) => `<option value="${s.id}">${s.nombre}</option>`).join('');
        document.getElementById('idEspecialidad').innerHTML =
            '<option value="">-- Seleccione una especialidad --</option>' +
            datos.especialidades.map((e) => `<option value="${e.id}">${e.nombre}</option>`).join('');
    } catch (error) {
        mostrarError(error.message);
    }
}

async function asignar(evento) {
    evento.preventDefault();
    ocultarMensajes();

    const idSucursal = document.getElementById('idSucursal').value;
    const idEspecialidad = document.getElementById('idEspecialidad').value;

    // FA03: mensaje global debajo del formulario, según el campo que falte
    if (!idSucursal) { mostrarError('Debe seleccionar una sede.'); return; }
    if (!idEspecialidad) { mostrarError('Debe seleccionar una especialidad.'); return; }

    const btn = document.getElementById('btnAsignar');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Asignando...';

    try {
        const respuesta = await fetch('/api/sede-especialidad', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ idSucursal: Number(idSucursal), idEspecialidad: Number(idEspecialidad) })
        });
        const datos = await respuesta.json().catch(() => ({}));
        // FA05: se muestra tal cual el mensaje del servidor ("La asignación ya existe.")
        if (!respuesta.ok) throw new Error(datos.message || 'No se pudo asignar la especialidad.');

        mostrarExito(datos.mensaje);
        setTimeout(() => { window.location.href = '/admin/sede-especialidad'; }, 1500);
    } catch (error) {
        mostrarError(error.message);
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-check me-2"></i>Asignar';
    }
}

function mostrarExito(mensaje) {
    const el = document.getElementById('alertaExito');
    el.textContent = mensaje;
    el.classList.remove('d-none');
}

function mostrarError(mensaje) {
    const el = document.getElementById('alertaError');
    el.textContent = mensaje;
    el.classList.remove('d-none');
}

function ocultarMensajes() {
    document.getElementById('alertaExito').classList.add('d-none');
    document.getElementById('alertaError').classList.add('d-none');
}
