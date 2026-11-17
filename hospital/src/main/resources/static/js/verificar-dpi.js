// CU-00: Modal "Verificar Registro" (vive en fragments/header.html,
// por eso este script corre en TODAS las páginas del sitio).
document.addEventListener('DOMContentLoaded', function () {
    const modalElement = document.getElementById('modalVerificarDpi');
    if (!modalElement) return; // por seguridad, si alguna página no trae el modal

    const dpiInput = document.getElementById('dpiInput');
    const dpiCounter = document.getElementById('dpiCounter');
    const dpiError = document.getElementById('dpiError');
    const btnVerificar = document.getElementById('btnVerificarDpi');
    const btnText = document.getElementById('btnText');
    const btnSpinner = document.getElementById('btnSpinner');
    const formVerificar = document.getElementById('formVerificarDpi');
    const resultadoDiv = document.getElementById('resultadoVerificacion');
    const iconoResultado = document.getElementById('iconoResultado');
    const mensajeResultado = document.getElementById('mensajeResultado');
    const accionesResultado = document.getElementById('accionesResultado');

    // Al abrir el modal, siempre empezar en blanco.
    modalElement.addEventListener('show.bs.modal', function () {
        formVerificar.reset();
        dpiCounter.textContent = '0/13';
        dpiError.classList.add('d-none');
        resultadoDiv.classList.add('d-none');
        btnVerificar.disabled = true;
        dpiInput.classList.remove('is-valid', 'is-invalid');
        setTimeout(() => dpiInput.focus(), 200);
    });

    // Contador en tiempo real + solo dígitos (paso 4 del flujo normal).
    dpiInput.addEventListener('input', function () {
        this.value = this.value.replace(/\D/g, '');
        const count = this.value.length;
        dpiCounter.textContent = `${count}/13`;
        resultadoDiv.classList.add('d-none');

        if (count === 13) {
            dpiError.classList.add('d-none');
            dpiInput.classList.replace('is-invalid', 'is-valid');
            btnVerificar.disabled = false;
        } else {
            dpiInput.classList.remove('is-valid');
            dpiInput.classList.toggle('is-invalid', count > 0);
            dpiError.classList.toggle('d-none', count === 0);
            btnVerificar.disabled = true;
        }
    });

    formVerificar.addEventListener('submit', function (e) {
        e.preventDefault();
        const dpi = dpiInput.value.trim();
        if (dpi.length !== 13) return;

        btnVerificar.disabled = true;
        btnText.textContent = 'Verificando...';
        btnSpinner.classList.remove('d-none');
        resultadoDiv.classList.add('d-none');

        fetch('/api/usuarios/verificar-dpi?dpi=' + encodeURIComponent(dpi))
            .then(async (respuesta) => {
                const datos = await respuesta.json().catch(() => ({}));
                if (!respuesta.ok) throw datos;
                return datos;
            })
            .then((datos) => mostrarResultado(datos, dpi))
            .catch((error) => {
                // FA05: error de conexión con el servidor
                mostrarResultado({ error: true, mensaje: error.mensaje || error.message || 'No se pudo conectar con el servidor. Intente de nuevo más tarde.' }, dpi);
            })
            .finally(() => {
                btnVerificar.disabled = false;
                btnText.textContent = 'Verificar DPI';
                btnSpinner.classList.add('d-none');
            });
    });

    function mostrarResultado(data, dpi) {
        resultadoDiv.classList.remove('d-none');
        accionesResultado.innerHTML = '';

        if (data.error) {
            aplicarEstilo('alert-danger', 'fa-circle-exclamation');
            mensajeResultado.textContent = data.mensaje || 'Ocurrió un error inesperado.';
            accionesResultado.innerHTML = botonSecundario('Reintentar', 'resultadoVerificacion');
            return;
        }

        if (data.existe && data.esPaciente) {
            // RN-CU00-01: registrado como paciente -> ir al login
            aplicarEstilo('alert-success', 'fa-circle-check');
            mensajeResultado.textContent = data.mensaje || 'Usuario registrado correctamente.';
            accionesResultado.innerHTML = redirigiendoA('inicio de sesión');
            setTimeout(() => { window.location.href = '/login?dpi=' + dpi; }, 1600);
        } else if (data.existe && !data.esPaciente) {
            // FA04: DPI de usuario interno
            aplicarEstilo('alert-warning', 'fa-triangle-exclamation');
            mensajeResultado.textContent = data.mensaje || 'Este DPI pertenece a un usuario del sistema interno.';
            accionesResultado.innerHTML = botonSecundario('Cerrar', 'resultadoVerificacion');
        } else {
            // FA03: no registrado -> ir a registro
            aplicarEstilo('alert-warning', 'fa-user-plus');
            mensajeResultado.textContent = data.mensaje || 'No se encontró un registro asociado a este DPI.';
            accionesResultado.innerHTML = redirigiendoA('el formulario de registro');
            setTimeout(() => { window.location.href = '/registro?dpi=' + dpi; }, 1600);
        }
    }

    function aplicarEstilo(claseAlerta, claseIcono) {
        resultadoDiv.className = 'alert ' + claseAlerta;
        iconoResultado.className = 'fas ' + claseIcono + ' me-2 mt-1';
    }

    function redirigiendoA(destino) {
        return `<div class="d-flex align-items-center text-muted small">
                    <span class="spinner-border spinner-border-sm me-2"></span>
                    Serás redirigido a ${destino}...
                </div>`;
    }

    function botonSecundario(texto, idElementoAOcultar) {
        return `<button type="button" class="btn btn-sm btn-outline-secondary"
                    onclick="document.getElementById('${idElementoAOcultar}').classList.add('d-none')">
                    ${texto}
                </button>`;
    }
});
