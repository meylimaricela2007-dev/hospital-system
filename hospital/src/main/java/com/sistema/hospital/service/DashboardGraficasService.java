package com.sistema.hospital.service;

import com.sistema.hospital.dto.DashboardGraficasDTO;
import com.sistema.hospital.dto.DashboardGraficasDTO.Grafica;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gráficas del dashboard. CADA llamada consulta la base de datos (sin caché): si alguien cambia un registro
 * en la base, la siguiente lectura ya trae el dato nuevo y el dashboard redibuja la gráfica.
 *
 * Gráficas:  1) top de médicos con más consultas   2) días de la semana con más demanda de consultas médicas
 *            3) fechas con más consultas médicas               4) medicamentos más despachados
 *            5) pagos aprobados por método de pago (Visa, Mastercard, Efectivo, ...)
 */
@Service
public class DashboardGraficasService {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

    @Transactional(readOnly = true)
    public DashboardGraficasDTO obtener() {
        return new DashboardGraficasDTO(
                topMedicos(),
                diasDeLaSemana(),
                fechasConMasCitas(),
                medicamentosMasDespachados(),
                pagosPorMetodo(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    // 1) Top 10 de médicos con más consultas médicas
    private Grafica topMedicos() {
        return agrupar("Top 10 de médicos con más consultas",
                "SELECT m.nombre_completo, COUNT(*) AS total "
                        + "FROM his.consultas_medicas cm JOIN his.usuarios m ON m.id_usuario = cm.id_medico "
                        + "GROUP BY m.id_usuario, m.nombre_completo "
                        + "ORDER BY total DESC, m.nombre_completo LIMIT 10");
    }

    // 2) Días de la semana con más demanda (consultas médicas realizadas, según la fecha en que iniciaron)
    private Grafica diasDeLaSemana() {
        List<?> filas = entityManager.createNativeQuery(
                "SELECT CAST(EXTRACT(ISODOW FROM cm.fecha_inicio) AS INTEGER) AS dia, COUNT(*) AS total "
                        + "FROM his.consultas_medicas cm "
                        + "GROUP BY 1 ORDER BY 1").getResultList();
        long[] porDia = new long[7];                                   // lunes = posición 0 ... domingo = 6
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            int dia = ((Number) f[0]).intValue();
            if (dia >= 1 && dia <= 7) {
                porDia[dia - 1] = ((Number) f[1]).longValue();
            }
        }
        List<String> etiquetas = new ArrayList<>();
        List<Long> valores = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            etiquetas.add(DIAS[i]);
            valores.add(porDia[i]);
        }
        return new Grafica("Días de la semana con más demanda de consultas médicas", etiquetas, valores);
    }

    // 3) Las 7 fechas con más consultas médicas
    private Grafica fechasConMasCitas() {
        List<?> filas = entityManager.createNativeQuery(
                "SELECT CAST(cm.fecha_inicio AS DATE) AS fecha, COUNT(*) AS total "
                        + "FROM his.consultas_medicas cm "
                        + "GROUP BY CAST(cm.fecha_inicio AS DATE) ORDER BY total DESC, fecha DESC LIMIT 7").getResultList();
        List<String> etiquetas = new ArrayList<>();
        List<Long> valores = new ArrayList<>();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            LocalDate fecha = LocalDate.parse(f[0].toString().substring(0, 10));
            etiquetas.add(DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            valores.add(((Number) f[1]).longValue());
        }
        return new Grafica("Fechas con más consultas médicas (top 7)", etiquetas, valores);
    }

    // 4) Medicamentos más despachados (unidades de despachos completados)
    private Grafica medicamentosMasDespachados() {
        return agrupar("Medicamentos más despachados (unidades)",
                "SELECT me.nombre, SUM(dd.cantidad_despachada) AS total "
                        + "FROM his.detalle_despacho dd "
                        + "JOIN his.despachos_medicamentos d ON d.id_despacho = dd.id_despacho "
                        + "JOIN his.detalle_receta dr ON dr.id_detalle_receta = dd.id_detalle_receta "
                        + "JOIN his.medicamentos me ON me.id_medicamento = dr.id_medicamento "
                        + "WHERE d.estado = 'Completado' "
                        + "GROUP BY me.id_medicamento, me.nombre "
                        + "ORDER BY total DESC, me.nombre LIMIT 10");
    }

    // 5) Pagos aprobados por método de pago. LEFT JOIN: un método sin pagos también aparece, con 0.
    private Grafica pagosPorMetodo() {
        return agrupar("Pagos aprobados por método de pago",
                "SELECT mp.nombre, COUNT(p.id_pago) AS total "
                        + "FROM his.metodos_pago mp "
                        + "LEFT JOIN his.pagos_citas p ON p.id_metodo_pago = mp.id_metodo_pago "
                        + "AND p.id_estado_pago = (SELECT ep.id_estado_pago FROM his.estados_pago ep WHERE ep.nombre_estado = 'Aprobado') "
                        + "GROUP BY mp.id_metodo_pago, mp.nombre "
                        + "ORDER BY total DESC, mp.nombre");
    }

    /** Ejecuta una consulta de dos columnas (etiqueta, número) y la convierte en una gráfica. */
    private Grafica agrupar(String titulo, String sql) {
        List<?> filas = entityManager.createNativeQuery(sql).getResultList();
        List<String> etiquetas = new ArrayList<>();
        List<Long> valores = new ArrayList<>();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            etiquetas.add(f[0] == null ? "Sin dato" : f[0].toString());
            valores.add(f[1] == null ? 0L : ((Number) f[1]).longValue());
        }
        return new Grafica(titulo, etiquetas, valores);
    }
}
