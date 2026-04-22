package util;

import model.*;
import java.util.Map;

/**
 * Calcula el costo total de un plan de rutas.
 * 
 * Componentes del costo:
 * - Tiempo total de transporte de los envíos
 * - Penalización por violaciones de SLA (basada en hora de llegada absoluta)
 * - Penalización por envíos no asignados (ponderada por cantidad)
 * - Penalización por conexiones (escalas)
 * - Penalización por tiempo de espera en tránsito
 */
public class CostCalculator {

    // Pesos de los componentes del costo
    private static final double PESO_TIEMPO = 0.5;         // Por minuto de transporte
    private static final double PESO_CONEXION = 50.0;      // Por cada escala
    private static final double PESO_ESPERA = 0.3;         // Por minuto de espera
    private static final double PESO_SLA_VIOLADO = 5.0;    // Por minuto de exceso sobre SLA
    private static final double PESO_NO_ASIGNADA = 500.0;  // Por cada envío sin ruta (base)
    private static final double FACTOR_PRIORIDAD_ALTA = 1.5; // Multiplicador para prioridad 1

    /**
     * Calcula el costo total de un plan de rutas.
     * El costo tiene en cuenta la cantidad de maletas por envío.
     */
    public static double calcularCosto(PlanDeRutas plan) {
        // Verificar cache
        if (plan.getCostoCalculado() >= 0) {
            return plan.getCostoCalculado();
        }

        double costoTotal = 0;

        // Costo de envíos asignados
        for (Map.Entry<Maleta, Ruta> entry : plan.getAsignaciones().entrySet()) {
            Maleta maleta = entry.getKey();
            Ruta ruta = entry.getValue();

            double costoEnvio = 0;

            // Costo por tiempo de transporte
            costoEnvio += ruta.getTiempoTotal() * PESO_TIEMPO;

            // Costo por conexiones
            costoEnvio += ruta.getNumeroConexiones() * PESO_CONEXION;

            // Costo por tiempo de espera
            costoEnvio += ruta.getTiempoEspera() * PESO_ESPERA;

            // Penalización por SLA violado (basada en hora de llegada absoluta)
            long horaLlegada = ruta.getHoraLlegadaFinal();
            if (maleta.isSLAExpirado(horaLlegada)) {
                long exceso = horaLlegada - maleta.getSlaLimite();
                costoEnvio += exceso * PESO_SLA_VIOLADO;
            }

            // Ajuste por prioridad
            if (maleta.getPrioridad() == 1) {
                costoEnvio *= FACTOR_PRIORIDAD_ALTA;
            } else if (maleta.getPrioridad() == 2) {
                costoEnvio *= 1.2;
            }

            // Escalar por cantidad de maletas en el envío
            costoEnvio *= maleta.getCantidad();

            costoTotal += costoEnvio;
        }

        // Penalización por envíos no asignados (ponderada por cantidad)
        for (Maleta maleta : plan.getMaletasNoAsignadas()) {
            double penalizacion = PESO_NO_ASIGNADA * maleta.getCantidad();
            if (maleta.getPrioridad() == 1) {
                penalizacion *= FACTOR_PRIORIDAD_ALTA;
            }
            costoTotal += penalizacion;
        }

        // Cache del costo calculado
        plan.setCostoCalculado(costoTotal);
        return costoTotal;
    }

    /**
     * Genera un desglose detallado del costo para reporte.
     */
    public static String desgloseCosto(PlanDeRutas plan) {
        StringBuilder sb = new StringBuilder();
        double costoTransporte = 0, costoConexiones = 0, costoEspera = 0;
        double costoSLA = 0, costoNoAsignadas = 0;
        int violacionesSLA = 0;
        int maletasFisicasNoAsignadas = 0;

        for (Map.Entry<Maleta, Ruta> entry : plan.getAsignaciones().entrySet()) {
            Maleta maleta = entry.getKey();
            Ruta ruta = entry.getValue();
            double factor = maleta.getPrioridad() == 1 ? FACTOR_PRIORIDAD_ALTA :
                           (maleta.getPrioridad() == 2 ? 1.2 : 1.0);
            int cant = maleta.getCantidad();

            costoTransporte += ruta.getTiempoTotal() * PESO_TIEMPO * factor * cant;
            costoConexiones += ruta.getNumeroConexiones() * PESO_CONEXION * factor * cant;
            costoEspera += ruta.getTiempoEspera() * PESO_ESPERA * factor * cant;

            long horaLlegada = ruta.getHoraLlegadaFinal();
            if (maleta.isSLAExpirado(horaLlegada)) {
                long exceso = horaLlegada - maleta.getSlaLimite();
                costoSLA += exceso * PESO_SLA_VIOLADO * factor * cant;
                violacionesSLA++;
            }
        }

        for (Maleta maleta : plan.getMaletasNoAsignadas()) {
            costoNoAsignadas += PESO_NO_ASIGNADA * maleta.getCantidad() *
                    (maleta.getPrioridad() == 1 ? FACTOR_PRIORIDAD_ALTA : 1.0);
            maletasFisicasNoAsignadas += maleta.getCantidad();
        }

        sb.append(String.format("  Transporte:    %12.2f\n", costoTransporte));
        sb.append(String.format("  Conexiones:    %12.2f\n", costoConexiones));
        sb.append(String.format("  Espera:        %12.2f\n", costoEspera));
        sb.append(String.format("  SLA Violado:   %12.2f (%d violaciones)\n", costoSLA, violacionesSLA));
        sb.append(String.format("  No Asignadas:  %12.2f (%d envíos / %d maletas)\n",
                costoNoAsignadas, plan.getMaletasNoAsignadas().size(), maletasFisicasNoAsignadas));
        sb.append(String.format("  TOTAL:         %12.2f", calcularCosto(plan)));

        return sb.toString();
    }
}
