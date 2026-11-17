// ============================================================
// CU-15: detalle de un movimiento (inventario/movimiento-detalle.html)
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
    const id = document.getElementById('idMovimiento').value;
    try {
        const respuesta = await fetch(`/api/inventario/movimientos/${id}`);
        const m = await respuesta.json();
        if (!respuesta.ok) throw new Error(m.message || 'No se pudo cargar el movimiento.');

        document.getElementById('detNumero').textContent = '#' + m.idMovimiento;
        document.getElementById('detBadges').innerHTML =
            `<span class="badge ${m.entrada ? 'badge-soft-success' : 'badge-soft-warning'}">${escaparHtml(m.tipo)}</span> ` +
            `<span class="badge ${m.estado ? 'badge-soft-success' : 'badge-soft-danger'}">${m.estado ? 'Activo' : 'Inactivo'}</span>`;
        poner('detMedicamento', m.medicamento);
        poner('detSucursal', m.sucursal);
        poner('detCantidad', (m.entrada ? '+' : '−') + m.cantidad);
        poner('detAnterior', m.stockAnterior);
        poner('detNuevo', m.stockNuevo);
        poner('detCosto', m.costoUnitario != null ? 'Q' + Number(m.costoUnitario).toFixed(2) : '—');
        poner('detReferencia', m.numeroReferencia || '—');
        poner('detUsuario', m.usuario);
        poner('detFecha', new Date(m.fechaHora).toLocaleString('es-GT'));
        poner('detMotivo', m.motivoNotas || '—');
        document.getElementById('tarjetaDetalle').classList.remove('d-none');
    } catch (error) {
        const el = document.getElementById('alertaError');
        el.textContent = error.message;
        el.classList.remove('d-none');
    }
});

function poner(id, texto) {
    document.getElementById(id).textContent = texto ?? '';
}

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}
