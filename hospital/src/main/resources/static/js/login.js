// ============================================================
// CU-00: pantalla de login de pacientes (auth/login.html)
// ============================================================
document.addEventListener('DOMContentLoaded', function () {
    const loginForm = document.getElementById('loginForm');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const btnLogin = document.getElementById('btnLogin');
    const loginError = document.getElementById('loginError');
    const bloqueoMensaje = document.getElementById('bloqueoMensaje');
    const tiempoRestante = document.getElementById('tiempoRestante');
    const intentosCount = document.getElementById('intentosCount');
    let bloqueado = false;
    const rolNoAutorizado = document.getElementById('rolNoAutorizado');
    const rolNoAutorizadoTexto = document.getElementById('rolNoAutorizadoTexto');

    // El servidor es la autoridad real sobre el bloqueo; esto solo evita
    // que el usuario intente de nuevo mientras la página sigue abierta.
    function mostrarBloqueo(minutos) {
        bloqueado = true;
        tiempoRestante.textContent = `Cuenta bloqueada temporalmente. Intenta de nuevo en ${minutos} minutos.`;
        bloqueoMensaje.classList.remove('d-none');
        usernameInput.disabled = true;
        passwordInput.disabled = true;
        btnLogin.disabled = true;
    }

    function mostrarError(mensaje) {
        loginError.textContent = mensaje;
        loginError.classList.remove('d-none');
        setTimeout(() => loginError.classList.add('d-none'), 6000);
    }

    function actualizarIntentos(restantes) {
        intentosCount.textContent = restantes;
        intentosCount.className = 'badge ' + (restantes <= 2 ? 'bg-danger' : restantes <= 3 ? 'bg-warning' : 'bg-secondary');
    }

    loginForm.addEventListener('submit', function (e) {
        e.preventDefault();
        if (bloqueado) return;
        loginError.classList.add('d-none');
        rolNoAutorizado.classList.add('d-none');

        const username = usernameInput.value.trim();
        const password = passwordInput.value.trim();
        if (!username || !password) {
            mostrarError('Por favor, completa todos los campos.');
            return;
        }

        btnLogin.disabled = true;
        btnLogin.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Verificando...';

        fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password, portal: 'paciente' }),
        })
            .then((r) => r.json())
            .then((data) => {
                if (data.rol && data.rol !== 'Paciente') {
                    rolNoAutorizadoTexto.textContent = data.mensaje;
                    rolNoAutorizado.classList.remove('d-none');
                    passwordInput.value = '';
                    return;
                }

                if (data.success) {
                    window.location.href = data.rol === 'Paciente' ? '/dashboard' : '/admin/dashboard';
                    return;
                }

                const intentos = data.intentosFallidos ?? 0;
                if (intentos >= 5) {
                    mostrarBloqueo(15);
                } else {
                    actualizarIntentos(5 - intentos);
                    mostrarError(data.mensaje || 'Credenciales incorrectas.');
                    passwordInput.value = '';
                    passwordInput.focus();
                }
            })
            .catch(() => mostrarError('Error de conexión con el servidor. Intenta de nuevo más tarde.'))
            .finally(() => {
                btnLogin.disabled = bloqueado;
                btnLogin.innerHTML = '<i class="fas fa-right-to-bracket me-2"></i>Iniciar sesión';
            });
    });
});
