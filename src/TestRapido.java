import model.*;
import util.*;
import java.util.*;

/**
 * Test rápido del pipeline con 350 envíos.
 * Ejecuta ALNS con 250 y 500 iteraciones y compara resultados.
 */
public class TestRapido {
    public static void main(String[] args) throws Exception {
        String dataDir = "data";
        int NUM_ENVIOS = 350;
        int[] iteraciones = {250, 500};

        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║         TEST RAPIDO: 350 Envios | 250 vs 500 Iteraciones        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

        // 1. Cargar datos
        System.out.println("--- Cargando datos ---");
        Map<String, Aeropuerto> aeropuertos = DataParser.parseAeropuertos(dataDir + "/aeropuertos.txt");
        System.out.println("Aeropuertos: " + aeropuertos.size());

        List<FlightTemplate> templates = DataParser.parsePlanesVuelo(dataDir + "/planes_vuelo.txt");
        System.out.println("Templates de vuelo: " + templates.size());

        // Cargar envíos desde datos reales (envios_preliminar)
        List<Maleta> todosEnvios = DataParser.parseEnvios(dataDir + "/envios_preliminar", aeropuertos);
        System.out.println("Total envios cargados: " + todosEnvios.size());

        if (todosEnvios.size() < NUM_ENVIOS) {
            System.out.println("WARN: Solo hay " + todosEnvios.size() + " envios disponibles, se usarán todos.");
            NUM_ENVIOS = todosEnvios.size();
        }

        // Tomar los primeros 350 envíos
        List<Maleta> enviosTest = todosEnvios.subList(0, NUM_ENVIOS);
        System.out.printf("\nEnvios seleccionados para test: %d%n", enviosTest.size());

        // 2. Agrupar por día y tomar primer día con envíos
        Map<Integer, List<Maleta>> porDia = DataParser.agruparEnviosPorDia(enviosTest);
        System.out.println("Dias con envios: " + porDia.size());

        // Tomar el día con más envíos para tener buena muestra
        Map.Entry<Integer, List<Maleta>> mejorDia = null;
        for (Map.Entry<Integer, List<Maleta>> entry : porDia.entrySet()) {
            if (mejorDia == null || entry.getValue().size() > mejorDia.getValue().size()) {
                mejorDia = entry;
            }
        }

        int dayIndex = mejorDia.getKey();
        List<Maleta> enviosDia = mejorDia.getValue();

        // Si un solo día no tiene los 350, usar todos los envíos del test directamente
        List<Maleta> enviosParaALNS;
        if (enviosDia.size() >= NUM_ENVIOS) {
            enviosParaALNS = enviosDia.subList(0, NUM_ENVIOS);
        } else {
            // Usar todos los 350 envíos, instanciar vuelos para el rango completo de días
            enviosParaALNS = enviosTest;
        }

        System.out.printf("Procesando %d envios%n", enviosParaALNS.size());

        // Determinar rango de días necesarios
        int minDay = Integer.MAX_VALUE, maxDay = Integer.MIN_VALUE;
        for (Maleta m : enviosParaALNS) {
            int d = (int) (m.getFechaCreacionUTC() / 1440);
            if (d < minDay) minDay = d;
            if (d > maxDay) maxDay = d;
        }

        // 3. Instanciar vuelos para el rango de días + 2 para conexiones
        List<Vuelo> vuelos = DataParser.instanciarVuelos(templates, minDay, maxDay + 2, aeropuertos);
        System.out.println("Vuelos instanciados: " + vuelos.size());

        FlightIndex flightIndex = new FlightIndex(vuelos);

        // 4. Solución inicial
        PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(enviosParaALNS, flightIndex, aeropuertos);
        double costoInicial = CostCalculator.calcularCosto(planInicial);

        System.out.printf("%n--- Solucion Inicial ---%n");
        System.out.printf("  Paquetes asignados: %d/%d%n", planInicial.getTotalMaletasAsignadas(), planInicial.getTotalMaletas());
        System.out.printf("  %% Asignados: %.2f%%%n",
            planInicial.getTotalMaletas() > 0
                ? (planInicial.getTotalMaletasAsignadas() * 100.0 / planInicial.getTotalMaletas())
                : 0.0);
        System.out.printf("  Fitness (costo): %.2f%n", costoInicial);

        // 5. Ejecutar ALNS con cada configuración de iteraciones
        // Almacenar resultados para tabla comparativa
        double[] fitness = new double[iteraciones.length];
        double[] tiempos = new double[iteraciones.length];
        int[] paquetesAsignados = new int[iteraciones.length];
        int[] totalPaquetes = new int[iteraciones.length];
        double[] porcentajes = new double[iteraciones.length];

        for (int i = 0; i < iteraciones.length; i++) {
            int numIter = iteraciones[i];
            System.out.printf("%n%n╔══════════════════════════════════════════════════════════════════╗%n");
            System.out.printf("║            ALNS con %d iteraciones                              ║%n", numIter);
            System.out.printf("╚══════════════════════════════════════════════════════════════════╝%n");

            // Re-generar plan inicial limpio para cada corrida
            PlanDeRutas planBase = SolutionGenerator.generarPlanInicial(enviosParaALNS, flightIndex, aeropuertos);

            alns.ALNSEngine engine = new alns.ALNSEngine(
                numIter, 0.10, 0.40, 100.0, 0.995, 0.3, 100, flightIndex);

            long inicio = System.currentTimeMillis();
            PlanDeRutas mejorPlan = engine.ejecutar(planBase);
            long fin = System.currentTimeMillis();

            double costoFinal = CostCalculator.calcularCosto(mejorPlan);
            double tiempoSeg = (fin - inicio) / 1000.0;

            // Guardar resultados
            fitness[i] = costoFinal;
            tiempos[i] = tiempoSeg;
            paquetesAsignados[i] = mejorPlan.getTotalMaletasAsignadas();
            totalPaquetes[i] = mejorPlan.getTotalMaletas();
            porcentajes[i] = totalPaquetes[i] > 0
                ? (paquetesAsignados[i] * 100.0 / totalPaquetes[i])
                : 0.0;

            engine.imprimirReporteFinal();
        }

        // 6. Tabla comparativa final
        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        TABLA COMPARATIVA DE RESULTADOS                             ║");
        System.out.printf("║                        Envios: %d                                                  ║%n", NUM_ENVIOS);
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ %-14s │ %18s │ %14s │ %16s │ %10s ║%n",
            "Iteraciones", "Fitness (Costo)", "Tiempo (seg)", "Paq. Asignados", "% Asignados");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════╣");

        for (int i = 0; i < iteraciones.length; i++) {
            System.out.printf("║ %14d │ %18.2f │ %14.3f │ %8d / %-5d │ %9.2f%% ║%n",
                iteraciones[i], fitness[i], tiempos[i],
                paquetesAsignados[i], totalPaquetes[i], porcentajes[i]);
        }

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════╣");

        // Fila de solución inicial para comparación
        System.out.printf("║ %-14s │ %18.2f │ %14s │ %8d / %-5d │ %9.2f%% ║%n",
            "Inicial (ref)",
            costoInicial,
            "-",
            planInicial.getTotalMaletasAsignadas(),
            planInicial.getTotalMaletas(),
            planInicial.getTotalMaletas() > 0
                ? (planInicial.getTotalMaletasAsignadas() * 100.0 / planInicial.getTotalMaletas())
                : 0.0);

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════╣");

        // Mejora porcentual
        for (int i = 0; i < iteraciones.length; i++) {
            double mejora = costoInicial > 0 ? (1 - fitness[i] / costoInicial) * 100 : 0;
            System.out.printf("║  Mejora con %d iter vs Inicial: %+.2f%%                                          ║%n",
                iteraciones[i], mejora);
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println("\n=== TEST RAPIDO COMPLETADO ===");
    }
}
