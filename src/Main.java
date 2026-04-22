import model.*;
import alns.ALNSEngine;
import util.*;

import java.io.*;
import java.util.*;

/**
 * Punto de entrada del algoritmo ALNS para logística de envíos B2B.
 * 
 * Lee los datos reales desde:
 * - data/aeropuertos.txt (30 aeropuertos con continente, GMT, capacidad)
 * - data/planes_vuelo.txt (2,866 plantillas de vuelo)
 * - data/envios_preliminar/ (9.5M envíos en 30 archivos)
 * 
 * Procesa día por día: cada día se ejecuta un ALNS independiente.
 * Los envíos no asignados se acumulan para el siguiente día.
 * 
 * Uso: java Main [directorio_datos]
 * Si no se especifica, usa "data" por defecto.
 */
public class Main {

    // === Parámetros ALNS (configurables) ===
    private static final int MAX_ITERACIONES = 500;
    private static final double PORCENTAJE_REMOCION_MIN = 0.10;
    private static final double PORCENTAJE_REMOCION_MAX = 0.40;
    private static final double TEMPERATURA_INICIAL = 100.0;
    private static final double TASA_ENFRIAMIENTO = 0.995;
    private static final double TASA_REACCION = 0.3;
    private static final int PERIODO_ACTUALIZACION = 100;

    // Días extra de vuelos a instanciar (para SLA intercontinental de 48h)
    private static final int DIAS_EXTRA_VUELOS = 2;

    public static void main(String[] args) {
        String dataDir = args.length > 0 ? args[0] : "data";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║    ALNS - Adaptive Large Neighborhood Search                ║");
        System.out.println("║    Logística de Envíos B2B - Red Aeroportuaria              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            long tiempoGlobalInicio = System.currentTimeMillis();

            // ═══════════════════════════════════════════════════════════
            // FASE 1: Cargar datos
            // ═══════════════════════════════════════════════════════════
            System.out.println("═══ FASE 1: Carga de Datos ═══");
            System.out.println();

            // 1.1 Aeropuertos
            System.out.println("─── Cargando Aeropuertos ───");
            Map<String, Aeropuerto> aeropuertos = DataParser.parseAeropuertos(
                    dataDir + File.separator + "aeropuertos.txt");
            System.out.printf("  Aeropuertos cargados: %d%n", aeropuertos.size());

            for (Aeropuerto aero : aeropuertos.values()) {
                System.out.printf("    %s %-18s %-20s GMT%+d  Cap=%d%n",
                        aero.getCodigoICAO(), aero.getCiudad(),
                        aero.getContinente().getNombre(),
                        aero.getGmtOffset(), aero.getCapacidadAlmacen());
            }
            System.out.println();

            // 1.2 Planes de vuelo
            System.out.println("─── Cargando Planes de Vuelo ───");
            List<FlightTemplate> templates = DataParser.parsePlanesVuelo(
                    dataDir + File.separator + "planes_vuelo.txt");
            System.out.printf("  Plantillas de vuelo cargadas: %d%n", templates.size());

            // Estadísticas de rutas
            Set<String> paresOD = new HashSet<>();
            for (FlightTemplate ft : templates) {
                paresOD.add(ft.getOrigen() + "-" + ft.getDestino());
            }
            System.out.printf("  Pares origen-destino únicos: %d%n", paresOD.size());
            System.out.println();

            // 1.3 Envíos
            System.out.println("─── Cargando Envíos ───");
            List<Maleta> todosEnvios = DataParser.parseEnvios(
                    dataDir + File.separator + "envios_preliminar", aeropuertos);
            System.out.printf("%n  Total envíos cargados: %,d%n", todosEnvios.size());

            // Calcular total de maletas físicas
            long totalMaletasFisicas = 0;
            for (Maleta m : todosEnvios) totalMaletasFisicas += m.getCantidad();
            System.out.printf("  Total maletas físicas: %,d%n", totalMaletasFisicas);
            System.out.println();

            // ═══════════════════════════════════════════════════════════
            // FASE 2: Agrupar envíos por día
            // ═══════════════════════════════════════════════════════════
            System.out.println("═══ FASE 2: Organización por Días ═══");
            Map<Integer, List<Maleta>> enviosPorDia = DataParser.agruparEnviosPorDia(todosEnvios);
            System.out.printf("  Días con envíos: %d%n", enviosPorDia.size());

            if (enviosPorDia.isEmpty()) {
                System.out.println("  ERROR: No se encontraron envíos para procesar.");
                System.exit(1);
            }

            int primerDia = Collections.min(enviosPorDia.keySet());
            int ultimoDia = Collections.max(enviosPorDia.keySet());
            System.out.printf("  Rango: %s a %s%n",
                    TimeUtils.dayIndexToDate(primerDia), TimeUtils.dayIndexToDate(ultimoDia));

            // Estadísticas por día
            int minEnviosDia = Integer.MAX_VALUE, maxEnviosDia = 0;
            for (List<Maleta> lista : enviosPorDia.values()) {
                minEnviosDia = Math.min(minEnviosDia, lista.size());
                maxEnviosDia = Math.max(maxEnviosDia, lista.size());
            }
            System.out.printf("  Envíos por día: min=%,d, max=%,d, prom=%,d%n",
                    minEnviosDia, maxEnviosDia, todosEnvios.size() / enviosPorDia.size());
            System.out.println();

            // Liberar memoria de la lista original
            todosEnvios = null;
            System.gc();

            // ═══════════════════════════════════════════════════════════
            // FASE 3: Procesamiento día por día
            // ═══════════════════════════════════════════════════════════
            System.out.println("═══ FASE 3: Procesamiento ALNS por Día ═══");
            System.out.println();
            System.out.printf("  Parámetros ALNS:%n");
            System.out.printf("    Max iteraciones/día: %d%n", MAX_ITERACIONES);
            System.out.printf("    Remoción: %.0f%% - %.0f%%%n",
                    PORCENTAJE_REMOCION_MIN * 100, PORCENTAJE_REMOCION_MAX * 100);
            System.out.printf("    Temperatura SA: %.1f%n", TEMPERATURA_INICIAL);
            System.out.printf("    Enfriamiento (α): %.4f%n", TASA_ENFRIAMIENTO);
            System.out.println();

            // Variables para reporte global
            int totalEnviosAsignados = 0;
            int totalEnviosProcesados = 0;
            int totalMaletasFisicasAsignadas = 0;
            double costoGlobalAcumulado = 0;
            int diasProcesados = 0;
            int totalViolacionesSLA = 0;
            List<Maleta> enviosArrastre = new ArrayList<>(); // No asignados del día anterior

            // Iterar por cada día
            for (Map.Entry<Integer, List<Maleta>> entry : enviosPorDia.entrySet()) {
                int dayIndex = entry.getKey();
                List<Maleta> enviosDia = entry.getValue();

                // Agregar envíos no asignados del día anterior
                List<Maleta> enviosAProcesar = new ArrayList<>(enviosArrastre);
                enviosAProcesar.addAll(enviosDia);
                enviosArrastre.clear();

                totalEnviosProcesados += enviosAProcesar.size();

                System.out.printf("┌─── Día %s (dayIndex=%d) ─── %,d envíos (%,d arrastre) ───┐%n",
                        TimeUtils.dayIndexToDate(dayIndex), dayIndex,
                        enviosAProcesar.size(), enviosAProcesar.size() - enviosDia.size());

                // Instanciar vuelos para este día + DIAS_EXTRA (para SLA intercontinental)
                List<Vuelo> vuelosDia = DataParser.instanciarVuelos(
                        templates, dayIndex, dayIndex + DIAS_EXTRA_VUELOS, aeropuertos);

                FlightIndex flightIndex = new FlightIndex(vuelosDia);

                // Generar solución inicial
                PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(
                        enviosAProcesar, flightIndex, aeropuertos);

                double costoInicial = CostCalculator.calcularCosto(planInicial);
                System.out.printf("│ Solución inicial: %,d/%,d envíos asignados | Costo: %.2f%n",
                        planInicial.getTotalMaletasAsignadas(), planInicial.getTotalMaletas(), costoInicial);

                // Ejecutar ALNS solo si hay envíos asignados para optimizar
                PlanDeRutas mejorPlan;
                if (planInicial.getTotalMaletasAsignadas() > 0) {
                    ALNSEngine engine = new ALNSEngine(
                            MAX_ITERACIONES,
                            PORCENTAJE_REMOCION_MIN,
                            PORCENTAJE_REMOCION_MAX,
                            TEMPERATURA_INICIAL,
                            TASA_ENFRIAMIENTO,
                            TASA_REACCION,
                            PERIODO_ACTUALIZACION,
                            flightIndex
                    );

                    mejorPlan = engine.ejecutar(planInicial);
                    double costoFinal = CostCalculator.calcularCosto(mejorPlan);

                    System.out.printf("│ Resultado ALNS: %,d/%,d envíos | Costo: %.2f → %.2f (%.1f%% mejora)%n",
                            mejorPlan.getTotalMaletasAsignadas(), mejorPlan.getTotalMaletas(),
                            costoInicial, costoFinal,
                            costoInicial > 0 ? (1 - costoFinal / costoInicial) * 100 : 0);
                } else {
                    mejorPlan = planInicial;
                    System.out.println("│ Sin envíos asignables — omitiendo ALNS");
                }

                // Acumular estadísticas
                totalEnviosAsignados += mejorPlan.getTotalMaletasAsignadas();
                totalMaletasFisicasAsignadas += mejorPlan.getTotalMaletasFisicasAsignadas();
                costoGlobalAcumulado += CostCalculator.calcularCosto(mejorPlan);

                // Contar violaciones SLA
                for (Map.Entry<Maleta, Ruta> asig : mejorPlan.getAsignaciones().entrySet()) {
                    if (asig.getKey().isSLAExpirado(asig.getValue().getHoraLlegadaFinal())) {
                        totalViolacionesSLA++;
                    }
                }

                // Envíos no asignados pasan al siguiente día
                enviosArrastre.addAll(mejorPlan.getMaletasNoAsignadas());

                System.out.printf("│ No asignados arrastrados: %,d%n", enviosArrastre.size());
                System.out.println("└──────────────────────────────────────────────────────────┘");
                System.out.println();

                diasProcesados++;

                // Liberar memoria
                vuelosDia = null;
                flightIndex = null;
                planInicial = null;
                mejorPlan = null;

                // Progreso cada 50 días
                if (diasProcesados % 50 == 0) {
                    System.out.printf("    *** Progreso: %d/%d días procesados ***%n%n",
                            diasProcesados, enviosPorDia.size());
                    System.gc();
                }
            }

            long tiempoGlobalFin = System.currentTimeMillis();

            // ═══════════════════════════════════════════════════════════
            // FASE 4: Reporte Global Final
            // ═══════════════════════════════════════════════════════════
            imprimirReporteGlobal(diasProcesados, totalEnviosProcesados, totalEnviosAsignados,
                    totalMaletasFisicasAsignadas, totalMaletasFisicas,
                    costoGlobalAcumulado, totalViolacionesSLA, enviosArrastre.size(),
                    tiempoGlobalFin - tiempoGlobalInicio);

        } catch (FileNotFoundException e) {
            System.err.println("ERROR: No se encontró el archivo: " + e.getMessage());
            System.err.println("Asegúrese de que los archivos de datos están en la carpeta correcta.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Imprime el reporte global acumulado de todos los días procesados.
     */
    private static void imprimirReporteGlobal(int dias, int totalProcesados, int totalAsignados,
                                               int maletasFisicasAsignadas, long maletasFisicasTotal,
                                               double costoAcumulado, int violacionesSLA,
                                               int noAsignadosFinales, long tiempoMs) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                 REPORTE GLOBAL FINAL                            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Días procesados:              %,10d                        ║%n", dias);
        System.out.printf("║ Total envíos procesados:      %,10d                        ║%n", totalProcesados);
        System.out.printf("║ Total envíos asignados:       %,10d                        ║%n", totalAsignados);
        System.out.printf("║ Tasa de asignación:           %9.2f%%                        ║%n",
                totalProcesados > 0 ? (double) totalAsignados / totalProcesados * 100 : 0);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Maletas físicas asignadas:    %,10d                        ║%n", maletasFisicasAsignadas);
        System.out.printf("║ Maletas físicas totales:      %,10d                        ║%n", maletasFisicasTotal);
        System.out.printf("║ Tasa de maletas:              %9.2f%%                        ║%n",
                maletasFisicasTotal > 0 ? (double) maletasFisicasAsignadas / maletasFisicasTotal * 100 : 0);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Costo global acumulado:       %,14.2f                    ║%n", costoAcumulado);
        System.out.printf("║ Costo promedio por día:       %,14.2f                    ║%n",
                dias > 0 ? costoAcumulado / dias : 0);
        System.out.printf("║ Violaciones SLA:              %,10d                        ║%n", violacionesSLA);
        System.out.printf("║ Envíos no asignados (final):  %,10d                        ║%n", noAsignadosFinales);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Tiempo total de ejecución:    %10.2f segundos               ║%n", tiempoMs / 1000.0);
        System.out.printf("║ Tiempo promedio por día:      %10.2f segundos               ║%n",
                dias > 0 ? tiempoMs / 1000.0 / dias : 0);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    }
}
