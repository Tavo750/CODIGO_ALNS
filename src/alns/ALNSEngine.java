package alns;

import model.*;
import operators.*;
import util.CostCalculator;
import util.FlightIndex;

import java.util.*;

/**
 * Motor principal del algoritmo Adaptive Large Neighborhood Search (ALNS).
 * 
 * Implementa el bucle principal del pseudocódigo:
 * 1. Generar solución inicial factible
 * 2. Seleccionar operadores adaptativamente (ruleta)
 * 3. Destruir parte de la solución
 * 4. Reparar la solución
 * 5. Evaluar con Simulated Annealing
 * 6. Actualizar pesos de operadores
 * 7. Repetir hasta condición de parada
 */
public class ALNSEngine {

    // === Parámetros del algoritmo ===
    private final int maxIteraciones;
    private final double porcentajeRemocionMin;
    private final double porcentajeRemocionMax;

    // === Componentes ===
    private final List<DestructionOperator> operadoresDestruccion;
    private final List<RepairOperator> operadoresReparacion;
    private final AdaptiveWeightManager weightManager;
    private final SimulatedAnnealing sa;
    private final FlightIndex flightIndex;
    private final Random random;

    // === Estado ===
    private PlanDeRutas planActual;
    private PlanDeRutas mejorPlanGlobal;
    private final List<double[]> historialCostos; // [iteracion, costoActual, mejorGlobal]

    public ALNSEngine(int maxIteraciones, double porcentajeRemocionMin, double porcentajeRemocionMax,
                      double temperaturaInicial, double tasaEnfriamiento, double tasaReaccion,
                      int periodoActualizacionPesos, FlightIndex flightIndex) {
        this.maxIteraciones = maxIteraciones;
        this.porcentajeRemocionMin = porcentajeRemocionMin;
        this.porcentajeRemocionMax = porcentajeRemocionMax;
        this.flightIndex = flightIndex;
        this.random = new Random();
        this.historialCostos = new ArrayList<>();

        // Inicializar operadores de destrucción
        this.operadoresDestruccion = new ArrayList<>();
        operadoresDestruccion.add(new RandomDestruction());
        operadoresDestruccion.add(new SLAExpiradoDestruction());
        operadoresDestruccion.add(new VueloLlenoDestruction());

        // Inicializar operadores de reparación
        this.operadoresReparacion = new ArrayList<>();
        operadoresReparacion.add(new GreedyInsertion());
        operadoresReparacion.add(new RegretInsertion());

        // Inicializar pesos adaptativos
        List<String> nombresD = new ArrayList<>();
        for (DestructionOperator op : operadoresDestruccion) nombresD.add(op.getNombre());
        List<String> nombresR = new ArrayList<>();
        for (RepairOperator op : operadoresReparacion) nombresR.add(op.getNombre());

        this.weightManager = new AdaptiveWeightManager(nombresD, nombresR,
                tasaReaccion, periodoActualizacionPesos);

        // Inicializar Simulated Annealing
        this.sa = new SimulatedAnnealing(temperaturaInicial, tasaEnfriamiento);
    }

    /**
     * Ejecuta el algoritmo ALNS completo.
     *
     * @param planInicial El plan de rutas inicial factible
     * @return El mejor plan de rutas encontrado
     */
    public PlanDeRutas ejecutar(PlanDeRutas planInicial) {
        // 1. Inicialización
        this.planActual = planInicial;
        this.mejorPlanGlobal = planInicial.copiar();

        double costoActual = CostCalculator.calcularCosto(planActual);
        double costoMejorGlobal = costoActual;

        imprimirEncabezado();
        imprimirEstado(0, costoActual, costoMejorGlobal, "-", "-");

        // 2. Bucle principal
        for (int iter = 1; iter <= maxIteraciones; iter++) {

            // 3. Selección adaptativa (Ruleta)
            int idxDestruccion = weightManager.seleccionarDestruccion();
            int idxReparacion = weightManager.seleccionarReparacion();

            DestructionOperator opDestruccion = operadoresDestruccion.get(idxDestruccion);
            RepairOperator opReparacion = operadoresReparacion.get(idxReparacion);

            // Porcentaje de remoción aleatorio en el rango configurado
            double porcentaje = porcentajeRemocionMin +
                    random.nextDouble() * (porcentajeRemocionMax - porcentajeRemocionMin);

            // 4. Destruir: remover parte de la solución
            PlanDeRutas planParcial = opDestruccion.destroy(planActual, porcentaje);

            // 5. Reparar: reinsertar envíos
            PlanDeRutas nuevoPlan = opReparacion.repair(planParcial, flightIndex);

            double costoNuevo = CostCalculator.calcularCosto(nuevoPlan);

            // 6. Determinar el puntaje del rendimiento
            int sigma = 0;

            // ¿Es la nueva mejor global?
            if (costoNuevo < costoMejorGlobal) {
                mejorPlanGlobal = nuevoPlan.copiar();
                costoMejorGlobal = costoNuevo;
                sigma = AdaptiveWeightManager.SIGMA_1;
            }
            // ¿Mejora la solución actual?
            else if (costoNuevo < costoActual) {
                sigma = AdaptiveWeightManager.SIGMA_2;
            }

            // 7. Criterio de aceptación (Simulated Annealing)
            if (sa.aceptar(costoNuevo, costoActual)) {
                planActual = nuevoPlan;
                costoActual = costoNuevo;
                if (sigma == 0) {
                    sigma = AdaptiveWeightManager.SIGMA_3; // Aceptada por SA, no mejora
                }
            }

            // 8. Actualizar pesos de operadores
            weightManager.registrarRendimiento(idxDestruccion, idxReparacion, sigma);

            // 9. Enfriar temperatura
            sa.enfriar();

            // Registrar en historial
            historialCostos.add(new double[]{iter, costoActual, costoMejorGlobal});

            // Imprimir progreso cada cierto número de iteraciones
            if (iter % imprimirCada() == 0 || iter == maxIteraciones || iter <= 5) {
                imprimirEstado(iter, costoActual, costoMejorGlobal,
                        opDestruccion.getNombre(), opReparacion.getNombre());
            }
        }

        return mejorPlanGlobal;
    }

    /**
     * Calcula cada cuántas iteraciones imprimir progreso.
     */
    private int imprimirCada() {
        if (maxIteraciones <= 100) return 10;
        if (maxIteraciones <= 1000) return 50;
        return 200;
    }

    /**
     * Imprime el encabezado de la tabla de progreso.
     */
    private void imprimirEncabezado() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              ALNS - Logística de Envíos B2B (Adaptive Large Neighborhood Search)       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ %-8s │ %-14s │ %-14s │ %-10s │ %-13s │ %-12s ║%n",
                "Iter", "Costo Actual", "Mejor Global", "Temp SA", "Destrucción", "Reparación");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════╣");
    }

    /**
     * Imprime el estado de una iteración.
     */
    private void imprimirEstado(int iter, double costoActual, double costoMejorGlobal,
                                 String opD, String opR) {
        System.out.printf("║ %8d │ %14.2f │ %14.2f │ %10.4f │ %-13s │ %-12s ║%n",
                iter, costoActual, costoMejorGlobal, sa.getTemperatura(), opD, opR);
    }

    /**
     * Imprime el reporte final completo.
     */
    public void imprimirReporteFinal() {
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // === Mejor plan encontrado ===
        System.out.println("┌──────────────────────────────────────────────────────────────────┐");
        System.out.println("│                    MEJOR PLAN ENCONTRADO                         │");
        System.out.println("├──────────────────────────────────────────────────────────────────┤");

        double costo = CostCalculator.calcularCosto(mejorPlanGlobal);
        System.out.printf("│ Costo Total: %.2f%n", costo);
        System.out.printf("│ Envíos Asignados: %d/%d%n",
                mejorPlanGlobal.getTotalMaletasAsignadas(), mejorPlanGlobal.getTotalMaletas());
        System.out.printf("│ Maletas Físicas Asignadas: %d/%d%n",
                mejorPlanGlobal.getTotalMaletasFisicasAsignadas(), mejorPlanGlobal.getTotalMaletasFisicas());
        System.out.printf("│ Plan Factible: %s%n", mejorPlanGlobal.esFactible() ? "SÍ ✓" : "NO ✗");
        System.out.println("├──────────────────────────────────────────────────────────────────┤");
        System.out.println("│ Desglose de Costos:");

        System.out.println(CostCalculator.desgloseCosto(mejorPlanGlobal));
        System.out.println("├──────────────────────────────────────────────────────────────────┤");
        System.out.println("│ Detalle de Rutas (primeros 20):");

        int count = 0;
        for (Map.Entry<Maleta, Ruta> entry : mejorPlanGlobal.getAsignaciones().entrySet()) {
            Maleta m = entry.getKey();
            Ruta r = entry.getValue();
            long horaLlegada = r.getHoraLlegadaFinal();
            System.out.printf("│   %s: %s | T: %d min | Cant: %d | SLA: %s%n",
                    m.getId(), r.toString(), r.getTiempoTotal(), m.getCantidad(),
                    m.isSLAExpirado(horaLlegada) ? "VIOLADO ✗" : "OK ✓");
            count++;
            if (count >= 20) {
                int remaining = mejorPlanGlobal.getTotalMaletasAsignadas() - count;
                if (remaining > 0) {
                    System.out.printf("│   ... y %d envíos más%n", remaining);
                }
                break;
            }
        }

        if (!mejorPlanGlobal.getMaletasNoAsignadas().isEmpty()) {
            System.out.println("├──────────────────────────────────────────────────────────────────┤");
            int noAsignados = mejorPlanGlobal.getMaletasNoAsignadas().size();
            System.out.printf("│ Envíos NO Asignados: %d%n", noAsignados);
            int shown = 0;
            for (Maleta m : mejorPlanGlobal.getMaletasNoAsignadas()) {
                System.out.printf("│   %s (%s → %s, Cant=%d, P=%d)%n",
                        m.getId(), m.getAeropuertoOrigen(), m.getAeropuertoDestino(),
                        m.getCantidad(), m.getPrioridad());
                shown++;
                if (shown >= 10) {
                    if (noAsignados - shown > 0) {
                        System.out.printf("│   ... y %d envíos más%n", noAsignados - shown);
                    }
                    break;
                }
            }
        }

        System.out.println("├──────────────────────────────────────────────────────────────────┤");
        System.out.println("│ Pesos Finales de Operadores:");

        System.out.print("│   Destrucción: ");
        Map<String, Double> pesosD = weightManager.getPesosDestruccion();
        for (Map.Entry<String, Double> entry : pesosD.entrySet()) {
            System.out.printf("%s=%.3f  ", entry.getKey(), entry.getValue());
        }
        System.out.println();

        System.out.print("│   Reparación:  ");
        Map<String, Double> pesosR = weightManager.getPesosReparacion();
        for (Map.Entry<String, Double> entry : pesosR.entrySet()) {
            System.out.printf("%s=%.3f  ", entry.getKey(), entry.getValue());
        }
        System.out.println();

        System.out.println("└──────────────────────────────────────────────────────────────────┘");

        // === Convergencia ===
        imprimirConvergencia();
    }

    /**
     * Imprime una visualización ASCII de la convergencia del algoritmo.
     */
    private void imprimirConvergencia() {
        if (historialCostos.isEmpty()) return;

        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────────┐");
        System.out.println("│                    CONVERGENCIA DEL ALGORITMO                    │");
        System.out.println("├──────────────────────────────────────────────────────────────────┤");

        double maxCosto = historialCostos.get(0)[2];
        double minCosto = historialCostos.get(historialCostos.size() - 1)[2];

        int ancho = 50;
        int alto = 15;
        int step = Math.max(1, historialCostos.size() / ancho);

        // Muestrear puntos
        List<Double> muestras = new ArrayList<>();
        for (int i = 0; i < historialCostos.size(); i += step) {
            muestras.add(historialCostos.get(i)[2]); // Mejor global
        }

        double rango = maxCosto - minCosto;
        if (rango == 0) rango = 1;

        for (int y = alto; y >= 0; y--) {
            double nivel = minCosto + (rango * y / alto);
            System.out.printf("│ %10.0f │", nivel);
            for (int x = 0; x < Math.min(muestras.size(), ancho); x++) {
                double val = muestras.get(x);
                double normalizado = (val - minCosto) / rango * alto;
                if (Math.abs(normalizado - y) < 0.5) {
                    System.out.print("█");
                } else if (normalizado > y) {
                    System.out.print("░");
                } else {
                    System.out.print(" ");
                }
            }
            System.out.println(" ║");
        }

        System.out.print("│            └");
        for (int i = 0; i < ancho; i++) System.out.print("─");
        System.out.println("─╢");
        System.out.printf("│             Iteraciones: 0 → %d%n", maxIteraciones);
        System.out.printf("│             Mejora: %.2f → %.2f (%.1f%% reducción)%n",
                maxCosto, minCosto, (1 - minCosto / maxCosto) * 100);
        System.out.println("└──────────────────────────────────────────────────────────────────┘");
    }

    // === Getters ===
    public PlanDeRutas getMejorPlanGlobal() { return mejorPlanGlobal; }
    public List<double[]> getHistorialCostos() { return historialCostos; }
}
