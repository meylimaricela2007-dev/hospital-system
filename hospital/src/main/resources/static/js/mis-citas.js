// ============================================================
// Portal del paciente: "Mis Citas"
// ============================================================

document.addEventListener('DOMContentLoaded', cargarMisCitas);

function cargarMisCitas() {
    fetch('/api/citas/mis-citas')
        .then((r) => r.json())
        .then(pintarMisCitas)
        .catch(() => {
            document.getElementById('listaMisCitas').innerHTML =
                '<div class="alert alert-danger">No se pudieron cargar tus citas. Intenta de nuevo más tarde.</div>';
        });
}

function pintarMisCitas(citas) {
    const contenedor = document.getElementById('listaMisCitas');

    if (citas.length === 0) {
        contenedor.innerHTML = `
            <div class="hh-card text-center py-5">
                <i class="fas fa-calendar-xmark fa-2x text-muted mb-3"></i>
                <p class="mb-0 text-muted">Todavía no tienes ninguna cita agendada.</p>
            </div>`;
        return;
    }

    contenedor.innerHTML = citas.map(tarjetaMiCita).join('');
}

function tarjetaMiCita(c) {
    const badgeEmergencia = c.esEmergencia ? '<span class="badge badge-emergencia ms-2">EMERGENCIA</span>' : '';

    // Solo tiene sentido pagar mientras la cita sigue "Pendiente de pago";
    // cualquier otro estado ya pasó ese punto o ya no aplica.
    const botonPago = c.estadoCita === 'Pendiente de pago'
        ? `<a href="/pago?idCita=${c.idCita}" class="btn btn-accent btn-sm mt-2">
               <i class="fas fa-credit-card me-2"></i>Pagar ahora
           </a>`
        : '';

    return `
        <div class="hh-card hh-card-recepcion ${c.esEmergencia ? 'es-emergencia' : ''} mb-3">
            <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2">
                <div>
                    <h3 class="mb-1">${escaparHtmlMisCitas(c.especialidad)}${badgeEmergencia}</h3>
                    <span class="badge ${badgeParaEstadoMisCitas(c.estadoCita)}">${escaparHtmlMisCitas(c.estadoCita)}</span>
                </div>
                <span class="text-mono text-muted">No. Cita: ${c.idCita}</span>
            </div>
            <div class="row g-2 small">
                <div class="col-md-6"><strong>Médico:</strong> ${escaparHtmlMisCitas(c.nombreMedico)}</div>
                <div class="col-md-6"><strong>Sucursal:</strong> ${escaparHtmlMisCitas(c.sucursal)}</div>
                <div class="col-md-6"><strong>Fecha:</strong> ${c.fechaCita} ${(c.horaCita || '').substring(0, 5)}</div>
                <div class="col-md-6"><strong>Monto:</strong> Q${Number(c.montoTotal).toFixed(2)}</div>
                <div class="col-12"><strong>Motivo:</strong> ${escaparHtmlMisCitas(c.motivoConsulta)}</div>
            </div>
            ${botonPago}
        </div>`;
}

function badgeParaEstadoMisCitas(estado) {
    if (estado === 'Confirmada' || estado === 'Paciente Presente' || estado === 'Signos Vitales'
        || estado === 'En Espera' || estado === 'Consulta Médica') return 'badge-soft-success';
    if (estado === 'Pendiente de pago') return 'badge-soft-warning';
    if (estado === 'Cancelada' || estado === 'Expirada') return 'badge-soft-danger';
    return 'badge-soft-info';
}

function escaparHtmlMisCitas(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
