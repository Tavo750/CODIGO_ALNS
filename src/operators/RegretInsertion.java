package operators;

import model.*;
import util.FlightIndex;

import java.util.*;

/**
 * Operador de reparación con Regret-2 y perturbación.
 * 
 * Estrategia: Prioriza los envíos cuya "segunda mejor opción" es significativamente
 * peor que su "primera mejor opción". Incluye perturbación aleatoria para
 * diversificar las soluciones encontradas.
 * 
 * Regret = (costo 2da mejor ruta) - (costo 1ra mejor ruta)
 * Se inserta primero el envío con mayor regret.
 */
public class RegretInsertion implements RepairOperator {
    private final GreedyInsertion greedy;
    private final Random random = new Random();
    private static final double NOISE_FACTOR = 0.10; // 10% ruido en regret

    public RegretInsertion() {
        this.greedy = new GreedyInsertion();
    }

    @Override
    public PlanDeRutas repair(PlanDeRutas planParcial, FlightIndex flightIndex) {
        PlanDeRutas plan = planParcial.copiar();

        int sinMejora = 0;
        int prevPendientes = plan.getMaletasNoAsignadas().size();

        while (!plan.getMaletasNoAsignadas().isEmpty()) {
            List<Maleta> pendientes = new ArrayList<>(plan.getMaletasNoAsignadas());

            // Calcular regret para cada envío
            Maleta mejorMaleta = null;
            Ruta mejorRutaParaMejorMaleta = null;
            double maxRegret = -Double.MAX_VALUE;

            for (Maleta maleta : pendientes) {
                List<Ruta> rutasFactibles = greedy.encontrarRutasFactibles(maleta, plan, flightIndex);

                if (rutasFactibles.isEmpty()) continue;

                // Evaluar costos de todas las rutas factibles
                List<double[]> costosConIndice = new ArrayList<>();
                for (int i = 0; i < rutasFactibles.size(); i++) {
                    double costo = greedy.evaluarCostoInsercion(maleta, rutasFactibles.get(i));
                    costosConIndice.add(new double[]{costo, i});
                }

                // Ordenar por costo ascendente
                costosConIndice.sort(Comparator.comparingDouble(a -> a[0]));

                double costoMejor = costosConIndice.get(0)[0];
                int indiceMejor = (int) costosConIndice.get(0)[1];

                // Calcular regret con ruido
                double regret;
                if (costosConIndice.size() >= 2) {
                    double costoSegundo = costosConIndice.get(1)[0];
                    regret = costoSegundo - costoMejor;
                } else {
                    regret = Double.MAX_VALUE / 2;
                }

                // Agregar ruido al regret para diversificación
                double noise = Math.abs(regret) * NOISE_FACTOR * (random.nextDouble() * 2 - 1);
                regret += noise;

                if (regret > maxRegret) {
                    maxRegret = regret;
                    mejorMaleta = maleta;
                    mejorRutaParaMejorMaleta = rutasFactibles.get(indiceMejor);
                }
            }

            // Insertar el envío con mayor regret
            if (mejorMaleta != null && mejorRutaParaMejorMaleta != null) {
                plan.asignarMaleta(mejorMaleta, mejorRutaParaMejorMaleta);
            } else {
                break;
            }

            // Detección de estancamiento
            int currentPendientes = plan.getMaletasNoAsignadas().size();
            if (currentPendientes >= prevPendientes) {
                sinMejora++;
                if (sinMejora > pendientes.size()) break;
            } else {
                sinMejora = 0;
            }
            prevPendientes = currentPendientes;
        }

        return plan;
    }

    @Override
    public String getNombre() {
        return "Regret-2";
    }
}
