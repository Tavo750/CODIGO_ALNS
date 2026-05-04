import model.*;
import alns.ALNSEngine;
import util.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;

/**
 * Test de rendimiento del algoritmo ALNS para la simulación de periodo (5 días)
 * del traslado de maletas de Tasf.B2B.
 *
 * Periodo fijo: 06/06/2026 al 10/06/2026.
 * Escenarios de carga: 5000, 10000, 15000, 20120 maletas.
 * Cada escenario se ejecuta 10 veces (corridas) para análisis estadístico.
 *
 * Métricas evaluadas por corrida:
 *   Corrida, Maletas, Tiempo(s), Pedidos, Pedidos_Asignados, Fallidos,
 *   Pct_Asignados, Pct_Cumple_SLA, Costo, Tiempo_Prom, Memoria_MB, CPU_Pct.
 */
public class Test {

    // ===================== CONFIGURACIÓN =====================

    /** Cantidades de maletas a evaluar */
    static final int[] ESCENARIOS_MALETAS = {5000, 10000, 15000, 20120};

    /** Número de corridas (réplicas) por escenario */
    static final int NUM_CORRIDAS = 10;

    /** Iteraciones ALNS por día */
    static final int MAX_ITERACIONES = 50;

    /** Días de simulación (periodo de 5 días: 06/06/2026 al 10/06/2026) */
    static final int DIAS_SIMULACION = 5;

    /** Días extra de vuelos para cubrir SLA intercontinental (48h) */
    static final int DIAS_EXTRA_VUELOS = 2;

    /** Fecha inicio fija: 06/06/2026 (dayIndex desde epoch 2026-01-01) */
    // Ene:31 + Feb:28 + Mar:31 + Abr:30 + May:31 = 151 + (6-1) = 156
    static final int DIA_INICIO = TimeUtils.daysBetweenEpoch(2026, 6, 6);

    // Parámetros ALNS
    static final double PORCENTAJE_REMOCION_MIN = 0.10;
    static final double PORCENTAJE_REMOCION_MAX = 0.40;
    static final double TEMPERATURA_INICIAL     = 100.0;
    static final double TASA_ENFRIAMIENTO        = 0.995;
    static final double TASA_REACCION            = 0.3;
    static final int    PERIODO_ACTUALIZACION    = 100;

    // ===================== MAIN =====================

    public static void main(String[] args) throws Exception {
        String dataDir = args.length > 0 ? args[0] : "data";
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        boolean cpuSupported = threadBean.isCurrentThreadCpuTimeSupported();
        if (cpuSupported) threadBean.setThreadCpuTimeEnabled(true);

        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   TEST DE RENDIMIENTO ALNS - Periodo 06/06/2026 al 10/06/2026      ║");
        System.out.println("║   Tasf.B2B - Traslado de Equipaje Aeroportuario                    ║");
        System.out.println("║   10 Corridas por escenario para análisis estadístico               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ─── 1. Cargar datos maestros ───
        System.out.println("═══ Cargando datos maestros ═══");
        Map<String, Aeropuerto> aeropuertos = DataParser.parseAeropuertos(dataDir + "/aeropuertos.txt");
        List<FlightTemplate> templates = DataParser.parsePlanesVuelo(dataDir + "/planes_vuelo.txt");
        List<Maleta> todosEnvios = DataParser.parseEnvios(dataDir + "/envios_preliminar", aeropuertos);
        System.out.printf("  Aeropuertos: %d | Plantillas vuelo: %d | Envios totales: %,d%n%n",
                aeropuertos.size(), templates.size(), todosEnvios.size());

        // ─── 2. Filtrar envíos del periodo fijo: 06/06/2026 al 10/06/2026 ───
        int[] diasDelPeriodo = new int[DIAS_SIMULACION];
        for (int d = 0; d < DIAS_SIMULACION; d++) {
            diasDelPeriodo[d] = DIA_INICIO + d;
        }

        Map<Integer, List<Maleta>> porDia = DataParser.agruparEnviosPorDia(todosEnvios);
        List<Maleta> enviosPeriodo = new ArrayList<>();
        for (int d = 0; d < DIAS_SIMULACION; d++) {
            List<Maleta> lista = porDia.get(diasDelPeriodo[d]);
            if (lista != null) enviosPeriodo.addAll(lista);
        }

        System.out.printf("  Periodo fijo: %s a %s%n",
                TimeUtils.dayIndexToDate(DIA_INICIO),
                TimeUtils.dayIndexToDate(DIA_INICIO + DIAS_SIMULACION - 1));
        System.out.printf("  Envíos disponibles en el periodo: %,d%n", enviosPeriodo.size());
        System.out.println();

        // Liberar memoria
        todosEnvios = null;
        System.gc();

        // ─── 3. Preparar archivo CSV de resultados ───
        String csvPath = dataDir + "/test_resultados_alns.csv";
        PrintWriter csv = new PrintWriter(new FileWriter(csvPath));
        csv.println("Corrida,Maletas,Tiempo_seg,Pedidos,Pedidos_Asignados,"
                + "Fallidos,Pct_Asignados,Pct_Cumple_SLA,Costo,"
                + "Tiempo_Prom_min,Memoria_MB,CPU_Pct");

        // Preparar JSON para dashboard
        StringBuilder json = new StringBuilder("{\n  \"algoritmo\": \"ALNS\",\n");
        json.append("  \"periodo\": \"2026-06-06 a 2026-06-10\",\n");
        json.append("  \"corridas\": ").append(NUM_CORRIDAS).append(",\n");
        json.append("  \"experimentos\": [\n");

        // ─── 4. Ejecutar escenarios ───
        for (int e = 0; e < ESCENARIOS_MALETAS.length; e++) {
            int numMaletas = ESCENARIOS_MALETAS[e];

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.printf( "║  ESCENARIO: %,d maletas | %d corridas | %d días              ║%n",
                    numMaletas, NUM_CORRIDAS, DIAS_SIMULACION);
            System.out.println("╚══════════════════════════════════════════════════════════════╝");

            if (e > 0) json.append(",\n");
            json.append("    {\n");
            json.append("      \"maletas\": ").append(numMaletas).append(",\n");
            json.append("      \"corridas\": [\n");

            // ─── Ejecutar N corridas para este escenario ───
            for (int corrida = 1; corrida <= NUM_CORRIDAS; corrida++) {
                System.out.printf("%n  ── Corrida %d/%d (escenario %,d maletas) ──%n",
                        corrida, NUM_CORRIDAS, numMaletas);

                // Tomar subconjunto de envíos (shuffle con semilla diferente por corrida)
                Collections.shuffle(enviosPeriodo, new Random(42 + e * 1000 + corrida));
                List<Maleta> enviosEscenario = new ArrayList<>(
                        enviosPeriodo.subList(0, Math.min(numMaletas, enviosPeriodo.size())));

                // Redistribuir los envíos del escenario por día
                Map<Integer, List<Maleta>> enviosPorDiaEsc = DataParser.agruparEnviosPorDia(enviosEscenario);

                // Variables acumuladas de la corrida
                int totalPedidos = 0, totalAsignados = 0, totalFallidos = 0;
                int totalATiempo = 0;
                int totalMaletasFisicas = 0;
                double costoAcumulado = 0;
                long tiempoEntregaAcumulado = 0;
                int rutasConTiempo = 0;
                double memoriaPico = 0;
                List<Maleta> arrastre = new ArrayList<>();

                // Medir tiempo total de ejecución y CPU de la corrida
                System.gc();
                long cpuAntesCorrida = cpuSupported ? threadBean.getCurrentThreadCpuTime() : 0;
                long t0Corrida = System.currentTimeMillis();

                for (int d = 0; d < DIAS_SIMULACION; d++) {
                    int dayIndex = diasDelPeriodo[d];
                    List<Maleta> enviosDia = enviosPorDiaEsc.getOrDefault(dayIndex, new ArrayList<>());

                    // Agregar envíos de arrastre
                    List<Maleta> enviosProcesar = new ArrayList<>(arrastre);
                    enviosProcesar.addAll(enviosDia);
                    arrastre.clear();

                    int enviosDiaTotal = enviosProcesar.size();
                    totalPedidos += enviosDiaTotal;

                    // Instanciar vuelos
                    List<Vuelo> vuelos = DataParser.instanciarVuelos(
                            templates, dayIndex, dayIndex + DIAS_EXTRA_VUELOS, aeropuertos);
                    FlightIndex flightIndex = new FlightIndex(vuelos);

                    // Medir memoria antes
                    long memAntes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

                    // Generar solución inicial
                    PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(
                            enviosProcesar, flightIndex, aeropuertos);

                    // Ejecutar ALNS
                    PlanDeRutas mejorPlan;
                    if (planInicial.getTotalMaletasAsignadas() > 0) {
                        ALNSEngine engine = new ALNSEngine(
                                MAX_ITERACIONES, PORCENTAJE_REMOCION_MIN, PORCENTAJE_REMOCION_MAX,
                                TEMPERATURA_INICIAL, TASA_ENFRIAMIENTO, TASA_REACCION,
                                PERIODO_ACTUALIZACION, flightIndex);
                        mejorPlan = engine.ejecutar(planInicial);
                    } else {
                        mejorPlan = planInicial;
                    }

                    // Contar maletas físicas del día
                    int maletasFisicasDia = mejorPlan.getTotalMaletasFisicas();

                    // Medir memoria después
                    long memDespues = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    double memUsadaMB = Math.max(memDespues, memAntes) / (1024.0 * 1024.0);

                    // ── Calcular métricas del día ──
                    int asignados = mejorPlan.getTotalMaletasAsignadas();
                    int fallidos = mejorPlan.getMaletasNoAsignadas().size();
                    double costoSolucion = CostCalculator.calcularCosto(mejorPlan);

                    // Entregas a tiempo y tiempo promedio de entrega
                    int aTiempo = 0;
                    long sumaTiempoEntrega = 0;
                    int countRutas = 0;
                    for (Map.Entry<Maleta, Ruta> entry : mejorPlan.getAsignaciones().entrySet()) {
                        Maleta m = entry.getKey();
                        Ruta r = entry.getValue();
                        long horaLlegada = r.getHoraLlegadaFinal();
                        if (!m.isSLAExpirado(horaLlegada)) aTiempo++;
                        sumaTiempoEntrega += r.getTiempoTotal();
                        countRutas++;
                    }

                    // Acumular
                    totalAsignados += asignados;
                    totalFallidos += fallidos;
                    totalATiempo += aTiempo;
                    costoAcumulado += costoSolucion;
                    tiempoEntregaAcumulado += sumaTiempoEntrega;
                    rutasConTiempo += countRutas;
                    totalMaletasFisicas += maletasFisicasDia;
                    if (memUsadaMB > memoriaPico) memoriaPico = memUsadaMB;

                    // Arrastre
                    arrastre.addAll(mejorPlan.getMaletasNoAsignadas());

                    // Liberar
                    vuelos = null;
                    flightIndex = null;
                    planInicial = null;
                    mejorPlan = null;
                }

                long t1Corrida = System.currentTimeMillis();
                long cpuDespuesCorrida = cpuSupported ? threadBean.getCurrentThreadCpuTime() : 0;

                // ── Métricas consolidadas de la corrida ──
                double tiempoEjecCorrida = (t1Corrida - t0Corrida) / 1000.0;
                double cpuSegCorrida = (cpuDespuesCorrida - cpuAntesCorrida) / 1_000_000_000.0;
                double cpuPctCorrida = tiempoEjecCorrida > 0 ? (cpuSegCorrida / tiempoEjecCorrida) * 100.0 : 0;
                double pctAsigGlobal = totalPedidos > 0 ? (totalAsignados * 100.0 / totalPedidos) : 0;
                double pctCumpleSLA = totalAsignados > 0 ? (totalATiempo * 100.0 / totalAsignados) : 0;
                double tiempoEntregaProm = rutasConTiempo > 0 ? (tiempoEntregaAcumulado / (double) rutasConTiempo) : 0;

                // Imprimir resultados de la corrida
                System.out.printf("     Corrida:              %d%n", corrida);
                System.out.printf("     Maletas:              %,d%n", totalMaletasFisicas);
                System.out.printf("     Tiempo ejecución:     %.2f seg%n", tiempoEjecCorrida);
                System.out.printf("     Pedidos:              %,d%n", totalPedidos);
                System.out.printf("     Pedidos asignados:    %,d%n", totalAsignados);
                System.out.printf("     Fallidos:             %,d%n", totalFallidos);
                System.out.printf("     %% Asignados:          %.2f%%%n", pctAsigGlobal);
                System.out.printf("     %% Cumple SLA:         %.2f%%%n", pctCumpleSLA);
                System.out.printf("     Costo:                %,.2f%n", costoAcumulado);
                System.out.printf("     Tiempo entrega prom:  %.2f min%n", tiempoEntregaProm);
                System.out.printf("     Memoria pico:         %.2f MB%n", memoriaPico);
                System.out.printf("     CPU:                  %.2f%%%n", cpuPctCorrida);

                // Escribir CSV
                csv.printf("%d,%d,%.2f,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                        corrida, numMaletas, tiempoEjecCorrida,
                        totalPedidos, totalAsignados, totalFallidos,
                        pctAsigGlobal, pctCumpleSLA, costoAcumulado,
                        tiempoEntregaProm, memoriaPico, cpuPctCorrida);

                // JSON por corrida
                if (corrida > 1) json.append(",\n");
                json.append("        {\n");
                json.append(String.format("          \"corrida\": %d,\n", corrida));
                json.append(String.format("          \"maletas\": %d,\n", numMaletas));
                json.append(String.format("          \"tiempoSeg\": %.2f,\n", tiempoEjecCorrida));
                json.append(String.format("          \"pedidos\": %d,\n", totalPedidos));
                json.append(String.format("          \"pedidosAsignados\": %d,\n", totalAsignados));
                json.append(String.format("          \"fallidos\": %d,\n", totalFallidos));
                json.append(String.format("          \"pctAsignados\": %.2f,\n", pctAsigGlobal));
                json.append(String.format("          \"pctCumpleSLA\": %.2f,\n", pctCumpleSLA));
                json.append(String.format("          \"costo\": %.2f,\n", costoAcumulado));
                json.append(String.format("          \"tiempoProm\": %.2f,\n", tiempoEntregaProm));
                json.append(String.format("          \"memoriaMB\": %.2f,\n", memoriaPico));
                json.append(String.format("          \"cpuPct\": %.2f\n", cpuPctCorrida));
                json.append("        }");

                System.gc();
            }

            json.append("\n      ]\n");
            json.append("    }");

            System.out.println();
            System.out.println("┌──────────────────────────────────────────────────────────────┐");
            System.out.printf( "│  ESCENARIO %,d maletas COMPLETADO (%d corridas)              │%n",
                    numMaletas, NUM_CORRIDAS);
            System.out.println("└──────────────────────────────────────────────────────────────┘");
            System.out.println();
        }

        json.append("\n  ]\n}\n");

        csv.close();
        System.out.printf(">>> Resultados CSV exportados a: %s%n", csvPath);

        // Exportar JSON
        String jsonPath = dataDir + "/test_resultados_alns.json";
        try (PrintWriter pw = new PrintWriter(new FileWriter(jsonPath))) {
            pw.print(json.toString());
        }
        System.out.printf(">>> Resultados JSON exportados a: %s%n", jsonPath);

        // ─── Tabla comparativa final ───
        imprimirTablaComparativa(csvPath);

        System.out.println("\n=== TEST COMPLETADO ===");
    }

    /**
     * Lee el CSV generado e imprime una tabla comparativa estilo Excel.
     * Formato: Corrida | Maletas | Tiempo(s) | Pedidos | Pedidos Asignados | Fallidos |
     *          % Asignados | %Cumple SLA | Costo | Tiempo Prom | Memoria (MB) | CPU %
     */
    private static void imprimirTablaComparativa(String csvPath) throws IOException {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                              RESULTADOS ALNS - Periodo 06/06/2026 al 10/06/2026                                      ║");
        System.out.println("╠═════════╦═════════╦══════════╦═════════╦══════════════╦══════════╦════════════╦════════════╦════════════╦══════════╦══════════╦════════╣");
        System.out.println("║ Corrida ║ Maletas ║ Tiempo(s)║ Pedidos ║ P.Asignados  ║ Fallidos ║ %Asignados ║ %CumpleSLA ║   Costo    ║ T.Prom   ║ Mem(MB)  ║ CPU %  ║");
        System.out.println("╠═════════╬═════════╬══════════╬═════════╬══════════════╬══════════╬════════════╬════════════╬════════════╬══════════╬══════════╬════════╣");

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String linea;
            br.readLine(); // skip header
            int currentMaletas = -1;
            while ((linea = br.readLine()) != null) {
                String[] cols = linea.split(",");
                if (cols.length >= 12) {
                    int maletas = Integer.parseInt(cols[1]);
                    // Separador entre escenarios
                    if (maletas != currentMaletas && currentMaletas != -1) {
                        System.out.println("╠═════════╬═════════╬══════════╬═════════╬══════════════╬══════════╬════════════╬════════════╬════════════╬══════════╬══════════╬════════╣");
                    }
                    currentMaletas = maletas;

                    System.out.printf("║ %7s ║ %7s ║ %8s ║ %7s ║ %12s ║ %8s ║ %9s%% ║ %9s%% ║ %10s ║ %8s ║ %8s ║ %5s%% ║%n",
                            cols[0], cols[1], cols[2], cols[3], cols[4], cols[5],
                            cols[6], cols[7], abreviar(cols[8]), cols[9], cols[10], cols[11]);
                }
            }
        }

        System.out.println("╚═════════╩═════════╩══════════╩═════════╩══════════════╩══════════╩════════════╩════════════╩════════════╩══════════╩══════════╩════════╝");
    }

    /** Abrevia números grandes para la tabla */
    private static String abreviar(String num) {
        try {
            double val = Double.parseDouble(num);
            if (val >= 1_000_000) return String.format("%.1fM", val / 1_000_000);
            if (val >= 1_000) return String.format("%.1fK", val / 1_000);
            return String.format("%.1f", val);
        } catch (NumberFormatException e) {
            return num;
        }
    }
}
