// ============================================================
// CU-01: pantalla "Crear Usuario" / "Editar Usuario"
// (admin/usuario-formulario.html — un solo archivo sirve para
// ambos casos: si idUsuarioEditar tiene valor, es edición).
// ============================================================
let catalogosFormulario = null;

document.addEventListener('DOMContentLoaded', async () => {
    const idUsuarioEditar = document.getElementById('idUsuarioEditar').value;
    const esEdicion = idUsuarioEditar && idUsuarioEditar !== '';

    if (esEdicion) {
        document.getElementById('tituloFormulario').textContent = 'Editar Usuario';
        document.getElementById('subtituloFormulario').textContent = 'Modifica los campos que necesites actualizar.';
        document.getElementById('etiquetaContrasena').textContent = '(dejar vacío para no cambiarla)';
        document.getElementById('btnGuardarTexto').textContent = 'Actualizar';
    }

    await cargarCatalogos();
    if (esEdicion) await cargarDatosUsuario(idUsuarioEditar);

    document.getElementById('idRol').addEventListener('change', actualizarVisibilidadEspecialidad);
    // NUEVO: al elegir sede, la Especialidad se filtra a las que esa sede
    // realmente ofrece (mismo endpoint que ya usa el wizard de citas y el
    // modal de emergencia de Recepción).
    document.getElementById('idSucursal').addEventListener('change', cargarEspecialidadesPorSucursal);
    document.getElementById('estadoActivo').addEventListener('change', function () {
        document.getElementById('estadoActivoLabel').textContent = this.checked ? 'Activo' : 'Inactivo';
    });
    document.getElementById('formUsuario').addEventListener('submit', (e) => guardarUsuario(e, esEdicion, idUsuarioEditar));

    // Solo dígitos en DPI y teléfono
    ['dpi', 'telefono'].forEach((id) => {
        document.getElementById(id).addEventListener('input', function () { this.value = this.value.replace(/\D/g, ''); });
    });

    // NUEVO: en cuanto el usuario corrige un campo marcado en rojo, se le
    // quita el resaltado de ese campo (no hace falta esperar a reenviar
    // todo el formulario para que desaparezca).
    ['nombreCompleto', 'correoElectronico', 'nombreUsuario', 'contrasena', 'idRol', 'dpi', 'nit', 'telefono'].forEach((id) => {
        document.getElementById(id).addEventListener('input', () => limpiarErrorDeCampo(id));
    });
    document.getElementById('idRol').addEventListener('change', () => limpiarErrorDeCampo('idRol'));
});

async function cargarCatalogos() {
    const respuesta = await fetch('/api/usuarios/catalogos');
    catalogosFormulario = await respuesta.json();

    document.getElementById('idRol').innerHTML = catalogosFormulario.roles
        .map((r) => `<option value="${r.id}">${r.nombre}</option>`).join('');

    document.getElementById('idSucursal').innerHTML =
        `<option value="">-- Sin asignar --</option>` +
        catalogosFormulario.sucursales.map((s) => `<option value="${s.id}">${s.nombre}</option>`).join('');

    // Lista completa por defecto (sin sede elegida todavía); en cuanto se
    // elige una sede, cargarEspecialidadesPorSucursal() la reemplaza por la
    // lista filtrada.
    document.getElementById('idEspecialidad').innerHTML = catalogosFormulario.especialidades
        .map((e) => `<option value="${e.id}">${e.nombre}</option>`).join('');

    actualizarVisibilidadEspecialidad();
}

// NUEVO: especialidades filtradas según la sede elegida (paso 2 del wizard
// de CU-03 usa el mismo endpoint). Si no hay sede elegida, se vuelve a
// mostrar el catálogo completo de especialidades.
async function cargarEspecialidadesPorSucursal() {
    const idSucursal = document.getElementById('idSucursal').value;
    const selectEspecialidad = document.getElementById('idEspecialidad');
    const valorPrevio = selectEspecialidad.value;

    let especialidades;
    if (!idSucursal) {
        especialidades = catalogosFormulario.especialidades;
    } else {
        const respuesta = await fetch(`/api/catalogos/especialidades-por-sucursal/${idSucursal}`);
        especialidades = await respuesta.json();
    }

    selectEspecialidad.innerHTML = especialidades.map((e) => `<option value="${e.id}">${e.nombre}</option>`).join('');

    // Si la especialidad que ya estaba elegida sigue en la nueva lista
    // (por ejemplo, al precargar un médico en modo edición), se conserva.
    if (especialidades.some((e) => String(e.id) === String(valorPrevio))) {
        selectEspecialidad.value = valorPrevio;
    }
}

// FA04, paso 2: precargar el formulario con los valores actuales
async function cargarDatosUsuario(idUsuario) {
    const respuesta = await fetch(`/api/usuarios/${idUsuario}`);
    if (!respuesta.ok) { mostrarError('No se pudo cargar el usuario solicitado.'); return; }
    const u = await respuesta.json();

    document.getElementById('nombreCompleto').value = u.nombreCompleto ?? '';
    document.getElementById('correoElectronico').value = u.correoElectronico ?? '';
    document.getElementById('nombreUsuario').value = u.nombreUsuario ?? '';
    document.getElementById('dpi').value = u.dpi ?? '';
    document.getElementById('nit').value = u.nit ?? '';
    document.getElementById('telefono').value = u.telefono ?? '';
    document.getElementById('numeroSeguro').value = u.numeroSeguro ?? '';
    document.getElementById('idRol').value = u.idRol ?? '';
    document.getElementById('idSucursal').value = u.idSucursal ?? '';
    // Al editar, primero se filtra la lista de especialidades según la sede
    // que ya tenía asignada el usuario, y RECIÉN DESPUÉS se selecciona su
    // especialidad — si se hiciera al revés, cargarEspecialidadesPorSucursal()
    // reemplazaría el <select> y perdería el valor que acabamos de poner.
    await cargarEspecialidadesPorSucursal();
    document.getElementById('idEspecialidad').value = u.idEspecialidad ?? '';
    document.getElementById('estadoActivo').checked = u.estadoActivo;
    document.getElementById('estadoActivoLabel').textContent = u.estadoActivo ? 'Activo' : 'Inactivo';

    actualizarVisibilidadEspecialidad();
}

// El campo Especialidad solo aplica si el rol elegido es "Médico" (RN-CU01-14)
function actualizarVisibilidadEspecialidad() {
    const idRolSeleccionado = document.getElementById('idRol').value;
    const rol = catalogosFormulario?.roles.find((r) => String(r.id) === String(idRolSeleccionado));
    document.getElementById('campoEspecialidad').style.display = rol && rol.nombre === 'Médico' ? 'block' : 'none';
}

async function guardarUsuario(evento, esEdicion, idUsuarioEditar) {
    evento.preventDefault();
    limpiarErrores();

    const datos = {
        nombreCompleto: valor('nombreCompleto'),
        nombreUsuario: valor('nombreUsuario'),
        contrasena: document.getElementById('contrasena').value || null,
        correoElectronico: valor('correoElectronico'),
        idRol: Number(document.getElementById('idRol').value),
        idSucursal: document.getElementById('idSucursal').value ? Number(document.getElementById('idSucursal').value) : null,
        idEspecialidad: document.getElementById('campoEspecialidad').style.display === 'block' && document.getElementById('idEspecialidad').value
            ? Number(document.getElementById('idEspecialidad').value) : null,
        dpi: valor('dpi'),
        nit: valor('nit'),
        telefono: valor('telefono'),
        numeroSeguro: valor('numeroSeguro'),
        estadoActivo: document.getElementById('estadoActivo').checked,
    };

    const btn = document.getElementById('btnGuardar');
    btn.disabled = true;
    btn.querySelector('#btnGuardarTexto') ? null : null;
    const textoOriginal = document.getElementById('btnGuardarTexto').textContent;
    document.getElementById('btnGuardarTexto').textContent = esEdicion ? 'Actualizando...' : 'Creando...';

    const url = esEdicion ? `/api/usuarios/${idUsuarioEditar}` : '/api/usuarios';
    const metodo = esEdicion ? 'PUT' : 'POST';

    try {
        const respuesta = await fetch(url, {
            method: metodo,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(datos),
        });
        const cuerpo = await respuesta.json().catch(() => ({}));

        if (!respuesta.ok) {
            // FA06: validación de guardado fallida
            mostrarError(cuerpo.mensaje || cuerpo.message || 'No se pudo guardar el usuario. Revise los campos.');
            // NUEVO: además de la alerta general, resalta en rojo cada
            // campo que vino en "errors" (ver GlobalExceptionHandler).
            mostrarErroresDeCampo(cuerpo.errors);
            return;
        }

        const mensajeExito = document.getElementById('alertaExito');
        mensajeExito.textContent = esEdicion ? 'Usuario actualizado correctamente.' : 'Usuario creado correctamente.';
        mensajeExito.classList.remove('d-none');
        setTimeout(() => { window.location.href = '/admin/usuarios'; }, 1200);
    } catch (error) {
        mostrarError('Error de conexión con el servidor.');
    } finally {
        btn.disabled = false;
        document.getElementById('btnGuardarTexto').textContent = textoOriginal;
    }
}

function valor(id) { return document.getElementById(id).value.trim(); }

function mostrarError(mensaje) {
    const el = document.getElementById('alertaError');
    el.textContent = mensaje;
    el.classList.remove('d-none');
}

/**
 * NUEVO: pinta el borde rojo (clase Bootstrap "is-invalid") en cada campo
 * que falló y escribe su mensaje específico en el <div class="invalid-
 * feedback" data-error-for="..."> que ya existía en el HTML pero nunca se
 * llenaba. "errores" es { nombreDelCampo: "mensaje" }, tal como lo arma
 * GlobalExceptionHandler.
 */
function mostrarErroresDeCampo(errores) {
    if (!errores) return;
    Object.entries(errores).forEach(([campo, mensaje]) => {
        const input = document.getElementById(campo);
        const feedback = document.querySelector(`[data-error-for="${campo}"]`);
        if (input) input.classList.add('is-invalid');
        if (feedback) feedback.textContent = mensaje;
    });
}

// Quita el resaltado de UN campo (se llama mientras el usuario va corrigiendo).
function limpiarErrorDeCampo(campo) {
    const input = document.getElementById(campo);
    const feedback = document.querySelector(`[data-error-for="${campo}"]`);
    if (input) input.classList.remove('is-invalid');
    if (feedback) feedback.textContent = '';
}

function limpiarErrores() {
    document.getElementById('alertaError').classList.add('d-none');
    document.getElementById('alertaExito').classList.add('d-none');
    // Limpia también el resaltado de TODOS los campos antes de un nuevo intento.
    document.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
    document.querySelectorAll('[data-error-for]').forEach((el) => { el.textContent = ''; });
}
