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
 * Métricas evaluadas por experimento:
 *   Iteracion, Tiempo_Ejecucion, Envios_Total, Envios_Asignados, Envios_Fallidos,
 *   Pct_Asignados, Pct_Entregas_Tiempo, Costo_Solucion, Tiempo_Entrega,
 *   Consumo_Memoria, Consumo_CPU.
 *
 * Escenarios de carga: 2000, 4000, 6000, 8000 y 10000 envíos.
 */
public class Test {

    // ===================== CONFIGURACIÓN =====================

    /** Cantidades de envíos a evaluar */
    static final int[] ESCENARIOS_ENVIOS = {2000, 4000, 6000, 8000, 10000};

    /** Iteraciones ALNS por día */
    static final int MAX_ITERACIONES = 500;

    /** Días de simulación (periodo de 5 días) */
    static final int DIAS_SIMULACION = 5;

    /** Días extra de vuelos para cubrir SLA intercontinental (48h) */
    static final int DIAS_EXTRA_VUELOS = 2;

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
        System.out.println("║   TEST DE RENDIMIENTO ALNS - Simulación Periodo 5 Días             ║");
        System.out.println("║   Tasf.B2B - Traslado de Equipaje Aeroportuario                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ─── 1. Cargar datos maestros ───
        System.out.println("═══ Cargando datos maestros ═══");
        Map<String, Aeropuerto> aeropuertos = DataParser.parseAeropuertos(dataDir + "/aeropuertos.txt");
        List<FlightTemplate> templates = DataParser.parsePlanesVuelo(dataDir + "/planes_vuelo.txt");
        List<Maleta> todosEnvios = DataParser.parseEnvios(dataDir + "/envios_preliminar", aeropuertos);
        System.out.printf("  Aeropuertos: %d | Plantillas vuelo: %d | Envios totales: %,d%n%n",
                aeropuertos.size(), templates.size(), todosEnvios.size());

        // ─── 2. Agrupar por día y seleccionar rango de 5 días con más envíos ───
        Map<Integer, List<Maleta>> porDia = DataParser.agruparEnviosPorDia(todosEnvios);
        List<Integer> dias = new ArrayList<>(porDia.keySet());
        Collections.sort(dias);

        // Buscar ventana de 5 días consecutivos con más envíos
        int mejorInicio = dias.get(0);
        int maxEnviosVentana = 0;
        for (int i = 0; i <= dias.size() - DIAS_SIMULACION; i++) {
            int inicio = dias.get(i);
            int fin = inicio + DIAS_SIMULACION - 1;
            int total = 0;
            for (int d = inicio; d <= fin; d++) {
                List<Maleta> lista = porDia.get(d);
                if (lista != null) total += lista.size();
            }
            if (total > maxEnviosVentana) {
                maxEnviosVentana = total;
                mejorInicio = inicio;
            }
        }

        // Recopilar envíos del periodo seleccionado
        List<Maleta> enviosPeriodo = new ArrayList<>();
        int[] diasDelPeriodo = new int[DIAS_SIMULACION];
        for (int d = 0; d < DIAS_SIMULACION; d++) {
            diasDelPeriodo[d] = mejorInicio + d;
            List<Maleta> lista = porDia.get(diasDelPeriodo[d]);
            if (lista != null) enviosPeriodo.addAll(lista);
        }

        System.out.printf("  Periodo seleccionado: %s a %s (%d envios disponibles)%n",
                TimeUtils.dayIndexToDate(mejorInicio),
                TimeUtils.dayIndexToDate(mejorInicio + DIAS_SIMULACION - 1),
                enviosPeriodo.size());
        System.out.println();

        // Liberar memoria
        todosEnvios = null;
        System.gc();

        // ─── 3. Preparar archivo CSV de resultados ───
        String csvPath = dataDir + "/test_resultados.csv";
        PrintWriter csv = new PrintWriter(new FileWriter(csvPath));
        csv.println("Escenario,Dia,Iteracion,Tiempo_Ejecucion_seg,Envios_Total,Envios_Asignados,"
                + "Envios_Fallidos,Pct_Asignados,Pct_Entregas_Tiempo,Costo_Solucion,"
                + "Tiempo_Entrega_Prom_min,Consumo_Memoria_MB,Consumo_CPU_seg");

        // Preparar JSON para dashboard
        StringBuilder json = new StringBuilder("{\n  \"experimentos\": [\n");

        // ─── 4. Ejecutar escenarios ───
        for (int e = 0; e < ESCENARIOS_ENVIOS.length; e++) {
            int numEnvios = ESCENARIOS_ENVIOS[e];

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.printf( "║  ESCENARIO: %,d envíos en %d días                           ║%n",
                    numEnvios, DIAS_SIMULACION);
            System.out.println("╚══════════════════════════════════════════════════════════════╝");

            // Tomar subconjunto de envíos (shuffle para diversidad)
            Collections.shuffle(enviosPeriodo, new Random(42 + e));
            List<Maleta> enviosEscenario = new ArrayList<>(
                    enviosPeriodo.subList(0, Math.min(numEnvios, enviosPeriodo.size())));

            // Redistribuir los envíos del escenario por día
            Map<Integer, List<Maleta>> enviosPorDiaEsc = DataParser.agruparEnviosPorDia(enviosEscenario);

            // Variables acumuladas del escenario
            int totalEnviosProc = 0, totalAsignados = 0, totalFallidos = 0;
            int totalATiempo = 0, totalConRuta = 0;
            double costoAcumulado = 0;
            long tiempoEntregaAcumulado = 0;
            int rutasConTiempo = 0;
            double tiempoEjecTotal = 0;
            double cpuTotal = 0;
            double memoriaPico = 0;
            List<Maleta> arrastre = new ArrayList<>();

            if (e > 0) json.append(",\n");
            json.append("    {\n");
            json.append("      \"numEnvios\": ").append(numEnvios).append(",\n");
            json.append("      \"dias\": [\n");

            for (int d = 0; d < DIAS_SIMULACION; d++) {
                int dayIndex = diasDelPeriodo[d];
                List<Maleta> enviosDia = enviosPorDiaEsc.getOrDefault(dayIndex, new ArrayList<>());

                // Agregar envíos de arrastre
                List<Maleta> enviosProcesar = new ArrayList<>(arrastre);
                enviosProcesar.addAll(enviosDia);
                arrastre.clear();

                int enviosDiaTotal = enviosProcesar.size();
                totalEnviosProc += enviosDiaTotal;

                System.out.printf("%n  ── Día %d/%d: %s | %,d envíos ──%n",
                        d + 1, DIAS_SIMULACION, TimeUtils.dayIndexToDate(dayIndex), enviosDiaTotal);

                // Instanciar vuelos
                List<Vuelo> vuelos = DataParser.instanciarVuelos(
                        templates, dayIndex, dayIndex + DIAS_EXTRA_VUELOS, aeropuertos);
                FlightIndex flightIndex = new FlightIndex(vuelos);

                // Medir memoria antes
                System.gc();
                long memAntes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

                // Medir CPU antes
                long cpuAntes = cpuSupported ? threadBean.getCurrentThreadCpuTime() : 0;

                // Generar solución inicial
                long t0 = System.currentTimeMillis();
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
                long t1 = System.currentTimeMillis();

                // Medir CPU después
                long cpuDespues = cpuSupported ? threadBean.getCurrentThreadCpuTime() : 0;
                double cpuSeg = (cpuDespues - cpuAntes) / 1_000_000_000.0;

                // Medir memoria después
                long memDespues = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                double memUsadaMB = Math.max(memDespues, memAntes) / (1024.0 * 1024.0);

                // ── Calcular métricas ──
                double tiempoEjec = (t1 - t0) / 1000.0;
                int asignados = mejorPlan.getTotalMaletasAsignadas();
                int fallidos = mejorPlan.getMaletasNoAsignadas().size();
                double pctAsignados = enviosDiaTotal > 0 ? (asignados * 100.0 / enviosDiaTotal) : 0;
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
                double pctATiempo = asignados > 0 ? (aTiempo * 100.0 / asignados) : 0;
                double tiempoEntregaProm = countRutas > 0 ? (sumaTiempoEntrega / (double) countRutas) : 0;

                // Acumular
                totalAsignados += asignados;
                totalFallidos += fallidos;
                totalATiempo += aTiempo;
                totalConRuta += asignados;
                costoAcumulado += costoSolucion;
                tiempoEntregaAcumulado += sumaTiempoEntrega;
                rutasConTiempo += countRutas;
                tiempoEjecTotal += tiempoEjec;
                cpuTotal += cpuSeg;
                if (memUsadaMB > memoriaPico) memoriaPico = memUsadaMB;

                // Arrastre
                arrastre.addAll(mejorPlan.getMaletasNoAsignadas());

                // Imprimir resultados del día
                System.out.printf("     Iteraciones ALNS:     %d%n", MAX_ITERACIONES);
                System.out.printf("     Tiempo ejecución:     %.3f seg%n", tiempoEjec);
                System.out.printf("     Envíos total:         %d%n", enviosDiaTotal);
                System.out.printf("     Envíos asignados:     %d%n", asignados);
                System.out.printf("     Envíos fallidos:      %d%n", fallidos);
                System.out.printf("     Pct asignados:        %.2f%%%n", pctAsignados);
                System.out.printf("     Pct entregas a tiempo:%.2f%%%n", pctATiempo);
                System.out.printf("     Costo solución:       %.2f%n", costoSolucion);
                System.out.printf("     Tiempo entrega prom:  %.1f min%n", tiempoEntregaProm);
                System.out.printf("     Memoria usada:        %.1f MB%n", memUsadaMB);
                System.out.printf("     CPU usado:            %.3f seg%n", cpuSeg);

                // Escribir CSV
                csv.printf("%d,%s,%d,%.3f,%d,%d,%d,%.2f,%.2f,%.2f,%.1f,%.1f,%.3f%n",
                        numEnvios, TimeUtils.dayIndexToDate(dayIndex), MAX_ITERACIONES,
                        tiempoEjec, enviosDiaTotal, asignados, fallidos,
                        pctAsignados, pctATiempo, costoSolucion,
                        tiempoEntregaProm, memUsadaMB, cpuSeg);

                // JSON por día
                if (d > 0) json.append(",\n");
                json.append("        {\n");
                json.append("          \"dia\": \"").append(TimeUtils.dayIndexToDate(dayIndex)).append("\",\n");
                json.append(String.format("          \"iteracion\": %d,\n", MAX_ITERACIONES));
                json.append(String.format("          \"tiempoEjecucion\": %.3f,\n", tiempoEjec));
                json.append(String.format("          \"enviosTotal\": %d,\n", enviosDiaTotal));
                json.append(String.format("          \"enviosAsignados\": %d,\n", asignados));
                json.append(String.format("          \"enviosFallidos\": %d,\n", fallidos));
                json.append(String.format("          \"pctAsignados\": %.2f,\n", pctAsignados));
                json.append(String.format("          \"pctEntregasTiempo\": %.2f,\n", pctATiempo));
                json.append(String.format("          \"costoSolucion\": %.2f,\n", costoSolucion));
                json.append(String.format("          \"tiempoEntregaProm\": %.1f,\n", tiempoEntregaProm));
                json.append(String.format("          \"consumoMemoriaMB\": %.1f,\n", memUsadaMB));
                json.append(String.format("          \"consumoCPUSeg\": %.3f\n", cpuSeg));
                json.append("        }");

                // Liberar
                vuelos = null;
                flightIndex = null;
                planInicial = null;
                mejorPlan = null;
            }

            json.append("\n      ],\n");

            // ── Resumen del escenario ──
            double pctAsigGlobal = totalEnviosProc > 0 ? (totalAsignados * 100.0 / totalEnviosProc) : 0;
            double pctATiempoGlobal = totalConRuta > 0 ? (totalATiempo * 100.0 / totalConRuta) : 0;
            double tiempoEntregaGlobal = rutasConTiempo > 0 ? (tiempoEntregaAcumulado / (double) rutasConTiempo) : 0;

            json.append(String.format("      \"resumen\": {\n"));
            json.append(String.format("        \"totalEnvios\": %d,\n", totalEnviosProc));
            json.append(String.format("        \"totalAsignados\": %d,\n", totalAsignados));
            json.append(String.format("        \"totalFallidos\": %d,\n", totalFallidos));
            json.append(String.format("        \"pctAsignados\": %.2f,\n", pctAsigGlobal));
            json.append(String.format("        \"pctEntregasTiempo\": %.2f,\n", pctATiempoGlobal));
            json.append(String.format("        \"costoTotal\": %.2f,\n", costoAcumulado));
            json.append(String.format("        \"tiempoEntregaProm\": %.1f,\n", tiempoEntregaGlobal));
            json.append(String.format("        \"tiempoEjecTotal\": %.3f,\n", tiempoEjecTotal));
            json.append(String.format("        \"memoriaPicoMB\": %.1f,\n", memoriaPico));
            json.append(String.format("        \"cpuTotalSeg\": %.3f\n", cpuTotal));
            json.append("      }\n");
            json.append("    }");

            // Escribir fila resumen en CSV
            csv.printf("%d,RESUMEN,%d,%.3f,%d,%d,%d,%.2f,%.2f,%.2f,%.1f,%.1f,%.3f%n",
                    numEnvios, MAX_ITERACIONES * DIAS_SIMULACION,
                    tiempoEjecTotal, totalEnviosProc, totalAsignados, totalFallidos,
                    pctAsigGlobal, pctATiempoGlobal, costoAcumulado,
                    tiempoEntregaGlobal, memoriaPico, cpuTotal);

            System.out.println();
            System.out.println("┌──────────────────────────────────────────────────────────────┐");
            System.out.printf( "│  RESUMEN ESCENARIO %,d envíos                                │%n", numEnvios);
            System.out.println("├──────────────────────────────────────────────────────────────┤");
            System.out.printf( "│  Envíos procesados:      %,10d                          │%n", totalEnviosProc);
            System.out.printf( "│  Envíos asignados:       %,10d (%.2f%%)               │%n", totalAsignados, pctAsigGlobal);
            System.out.printf( "│  Envíos fallidos:        %,10d                          │%n", totalFallidos);
            System.out.printf( "│  Entregas a tiempo:      %9.2f%%                         │%n", pctATiempoGlobal);
            System.out.printf( "│  Costo total:            %,14.2f                      │%n", costoAcumulado);
            System.out.printf( "│  Tiempo entrega prom:    %10.1f min                      │%n", tiempoEntregaGlobal);
            System.out.printf( "│  Tiempo ejecución total: %10.3f seg                      │%n", tiempoEjecTotal);
            System.out.printf( "│  Memoria pico:           %10.1f MB                       │%n", memoriaPico);
            System.out.printf( "│  CPU total:              %10.3f seg                      │%n", cpuTotal);
            System.out.println("└──────────────────────────────────────────────────────────────┘");
            System.out.println();

            // Reset acumuladores para siguiente escenario
            totalEnviosProc = 0; totalAsignados = 0; totalFallidos = 0;
            totalATiempo = 0; totalConRuta = 0;
            costoAcumulado = 0; tiempoEntregaAcumulado = 0; rutasConTiempo = 0;
            tiempoEjecTotal = 0; cpuTotal = 0; memoriaPico = 0;

            System.gc();
        }

        json.append("\n  ]\n}\n");

        csv.close();
        System.out.printf(">>> Resultados CSV exportados a: %s%n", csvPath);

        // Exportar JSON
        String jsonPath = dataDir + "/test_rendimiento.json";
        try (PrintWriter pw = new PrintWriter(new FileWriter(jsonPath))) {
            pw.print(json.toString());
        }
        System.out.printf(">>> Resultados JSON exportados a: %s%n", jsonPath);

        // ─── Tabla comparativa final ───
        imprimirTablaComparativa(csvPath);

        System.out.println("\n=== TEST COMPLETADO ===");
    }

    /**
     * Lee el CSV generado e imprime una tabla comparativa de los resúmenes.
     */
    private static void imprimirTablaComparativa(String csvPath) throws IOException {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                           TABLA COMPARATIVA DE ESCENARIOS                                                     ║");
        System.out.println("╠═══════════╦═══════════╦══════════╦══════════╦══════════╦══════════╦════════════╦══════════╦═════════╦══════════╣");
        System.out.println("║ Escenario ║ Asignados ║ Fallidos ║ %Asig    ║ %Tiempo  ║ Costo   ║ T.Entrega  ║ T.Ejec   ║ Mem MB  ║ CPU seg  ║");
        System.out.println("╠═══════════╬═══════════╬══════════╬══════════╬══════════╬══════════╬════════════╬══════════╬═════════╬══════════╣");

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String linea;
            br.readLine(); // skip header
            while ((linea = br.readLine()) != null) {
                String[] cols = linea.split(",");
                if (cols.length >= 13 && cols[1].equals("RESUMEN")) {
                    System.out.printf("║ %,9s ║ %,9s ║ %,8s ║ %7s%% ║ %7s%% ║ %8s ║ %8s m ║ %7s s ║ %7s ║ %7s s ║%n",
                            cols[0], cols[5], cols[6], cols[7], cols[8],
                            abreviar(cols[9]), cols[10], cols[3], cols[11], cols[12]);
                }
            }
        }

        System.out.println("╚═══════════╩═══════════╩══════════╩══════════╩══════════╩══════════╩════════════╩══════════╩═════════╩══════════╝");
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
