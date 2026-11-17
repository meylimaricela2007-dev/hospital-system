// ============================================================
// JavaScript compartido por TODO el sitio (se carga desde
// fragments/footer.html en cada página).
// ============================================================

// Efecto de aparición suave al hacer scroll, para los elementos
// marcados con la clase ".hh-reveal" (tarjetas de servicios, etc.).
document.addEventListener('DOMContentLoaded', function () {
    const elementos = document.querySelectorAll('.hh-reveal');
    if (elementos.length === 0) return;

    const prefiereMenosMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefiereMenosMovimiento) {
        elementos.forEach((el) => el.classList.add('is-visible'));
        return;
    }

    const observador = new IntersectionObserver((entradas) => {
        entradas.forEach((entrada, indice) => {
            if (entrada.isIntersecting) {
                // Pequeño desfase entre tarjetas para que no aparezcan todas de golpe.
                setTimeout(() => entrada.target.classList.add('is-visible'), indice * 60);
                observador.unobserve(entrada.target);
            }
        });
    }, { threshold: 0.15 });

    elementos.forEach((el) => observador.observe(el));
});


// ============================================================
// MEJORAS VISUALES (v2)
// ============================================================

// 1) Sombra/compactado del navbar al hacer scroll + enlace activo según la URL.
document.addEventListener('DOMContentLoaded', function () {
    const nav = document.querySelector('.hh-navbar');
    if (nav) {
        const onScroll = () => nav.classList.toggle('is-scrolled', window.scrollY > 10);
        onScroll();
        window.addEventListener('scroll', onScroll, { passive: true });

        const ruta = window.location.pathname;
        nav.querySelectorAll('.navbar-nav .nav-link').forEach((link) => {
            const href = link.getAttribute('href');
            if (!href || href === '#') return;
            const activo = href === '/' ? ruta === '/' : ruta.startsWith(href);
            link.classList.toggle('active', activo);
        });
    }
});

// 2) Contadores animados: <div class="stat-num" data-count="5">
document.addEventListener('DOMContentLoaded', function () {
    const contadores = document.querySelectorAll('.stat-num[data-count]');
    if (contadores.length === 0) return;
    const reducido = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    contadores.forEach((el) => {
        const meta = parseInt(el.dataset.count, 10);
        if (isNaN(meta) || reducido) { el.textContent = isNaN(meta) ? el.textContent : meta; return; }
        const duracion = 900;
        const inicio = performance.now();
        const paso = (ahora) => {
            const progreso = Math.min((ahora - inicio) / duracion, 1);
            el.textContent = Math.round(meta * (1 - Math.pow(1 - progreso, 3)));
            if (progreso < 1) requestAnimationFrame(paso);
        };
        requestAnimationFrame(paso);
    });
});