// ============================================================
// CU-02: registro de pacientes (auth/registro.html)
// ============================================================
document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('registroForm');
    const errorBox = document.getElementById('registroError');
    const exitoBox = document.getElementById('registroExito');
    const btn = document.getElementById('btnRegistrar');

    // Solo dígitos en DPI y teléfono
    ['dpi', 'telefono'].forEach((id) => {
        document.getElementById(id).addEventListener('input', function () {
            this.value = this.value.replace(/\D/g, '');
        });
    });

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        limpiarErrores();
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Creando cuenta...';

        const datos = {
            dpi: valor('dpi'),
            nombreCompleto: valor('nombreCompleto'),
            telefono: valor('telefono'),
            numeroSeguro: valor('numeroSeguro'),
            nit: valor('nit'),
            correoElectronico: valor('correoElectronico'),
            nombreUsuario: valor('nombreUsuario'),
            contrasena: document.getElementById('contrasena').value,
        };

        fetch('/api/auth/registro', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(datos),
        })
            .then(async (r) => {
                const data = await r.json().catch(() => ({}));
                if (!r.ok) throw data;
                return data;
            })
            .then((data) => {
                exitoBox.textContent = data.mensaje || 'Registro completado exitosamente.';
                exitoBox.classList.remove('d-none');
                form.reset();
                setTimeout(() => { window.location.href = '/login'; }, 1800);
            })
            .catch((error) => {
                errorBox.textContent = error.mensaje || error.message || 'No se pudo completar el registro.';
                errorBox.classList.remove('d-none');
                // NUEVO: resalta en rojo cada campo que vino en "errors"
                // (ver GlobalExceptionHandler / CampoInvalidoException).
                mostrarErroresDeCampo(error.errors);
            })
            .finally(() => {
                btn.disabled = false;
                btn.innerHTML = '<i class="fas fa-user-plus me-2"></i>Crear mi cuenta';
            });
    });

    // En cuanto el usuario corrige un campo marcado en rojo, se le quita el
    // resaltado a ESE campo (no hace falta reenviar todo el formulario).
    ['nombreCompleto', 'dpi', 'nit', 'telefono', 'numeroSeguro', 'correoElectronico', 'nombreUsuario', 'contrasena'].forEach((id) => {
        document.getElementById(id).addEventListener('input', () => limpiarErrorDeCampo(id));
    });

    function valor(id) {
        return document.getElementById(id).value.trim();
    }

    function mostrarErroresDeCampo(errores) {
        if (!errores) return;
        Object.entries(errores).forEach(([campo, mensaje]) => {
            const input = document.getElementById(campo);
            const feedback = document.querySelector(`[data-error-for="${campo}"]`);
            if (input) input.classList.add('is-invalid');
            if (feedback) feedback.textContent = mensaje;
        });
    }

    function limpiarErrorDeCampo(campo) {
        const input = document.getElementById(campo);
        const feedback = document.querySelector(`[data-error-for="${campo}"]`);
        if (input) input.classList.remove('is-invalid');
        if (feedback) feedback.textContent = '';
    }

    function limpiarErrores() {
        errorBox.classList.add('d-none');
        exitoBox.classList.add('d-none');
        document.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
        document.querySelectorAll('[data-error-for]').forEach((el) => { el.textContent = ''; });
    }
});
