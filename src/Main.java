import model.*;
import alns.ALNSEngine;
import util.*;

import java.io.*;
import java.util.*;

/**
 * Punto de entrada del algoritmo ALNS para logística de envíos B2B.
 *
 * Tres modos de ejecución:
 *   java Main daily    <yyyymmdd>                    → procesa un solo día
 *   java Main periodo  <yyyymmdd_inicio> <yyyymmdd_fin> → procesa rango de fechas
 *   java Main collapse <yyyymmdd>                    → desde la fecha hasta colapso
 *
 * Collapse: se detiene cuando UNA sola maleta no puede entregarse dentro del SLA
 *           (24h mismo continente, 48h intercontinental).
 * Daily/Periodo: procesan todos los días del rango sin detenerse.
 */
public class Main {

    // === Parámetros ALNS (configurables) ===
    private static final int MAX_ITERACIONES = 50;
    private static final double PORCENTAJE_REMOCION_MIN = 0.10;
    private static final double PORCENTAJE_REMOCION_MAX = 0.40;
    private static final double TEMPERATURA_INICIAL = 100.0;
    private static final double TASA_ENFRIAMIENTO = 0.995;
    private static final double TASA_REACCION = 0.3;
    private static final int PERIODO_ACTUALIZACION = 100;

    // Días extra de vuelos a instanciar (para SLA intercontinental de 48h)
    private static final int DIAS_EXTRA_VUELOS = 2;

    private enum Modo { DAILY, PERIODO, COLLAPSE }

    public static void main(String[] args) {
        // ── Parseo de argumentos ──
        if (args.length < 2) {
            imprimirUso();
            System.exit(1);
        }

        String dataDir = "data";
        Modo modo;
        int diaInicio, diaFin;

        String modoStr = args[0].toLowerCase();
        switch (modoStr) {
            case "daily":
                if (args.length < 2) { imprimirUso(); System.exit(1); }
                modo = Modo.DAILY;
                diaInicio = TimeUtils.parseDateToDayIndex(args[1]);
                diaFin = diaInicio;
                break;
            case "periodo":
                if (args.length < 3) { imprimirUso(); System.exit(1); }
                modo = Modo.PERIODO;
                diaInicio = TimeUtils.parseDateToDayIndex(args[1]);
                diaFin = TimeUtils.parseDateToDayIndex(args[2]);
                break;
            case "collapse":
                if (args.length < 2) { imprimirUso(); System.exit(1); }
                modo = Modo.COLLAPSE;
                diaInicio = TimeUtils.parseDateToDayIndex(args[1]);
                diaFin = Integer.MAX_VALUE; // hasta que colapse
                break;
            default:
                System.err.println("ERROR: Modo desconocido '" + args[0] + "'");
                imprimirUso();
                System.exit(1);
                return;
        }

        // Si se pasa un directorio de datos adicional
        if (args.length > 3 || (modo != Modo.PERIODO && args.length > 2)) {
            dataDir = args[args.length - 1];
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║    ALNS - Adaptive Large Neighborhood Search                ║");
        System.out.println("║    Logística de Envíos B2B - Red Aeroportuaria              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("  Modo: %s%n", modo);
        System.out.printf("  Fecha inicio: %s%n", TimeUtils.dayIndexToDate(diaInicio));
        if (modo == Modo.PERIODO) {
            System.out.printf("  Fecha fin:    %s%n", TimeUtils.dayIndexToDate(diaFin));
        } else if (modo == Modo.COLLAPSE) {
            System.out.println("  Fecha fin:    hasta colapso");
        }
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

            long totalMaletasFisicas = 0;
            for (Maleta m : todosEnvios) totalMaletasFisicas += m.getCantidad();
            System.out.printf("  Total maletas físicas: %,d%n", totalMaletasFisicas);
            System.out.println();

            // ═══════════════════════════════════════════════════════════
            // FASE 2: Agrupar envíos por día y filtrar según modo
            // ═══════════════════════════════════════════════════════════
            System.out.println("═══ FASE 2: Organización por Días ═══");
            Map<Integer, List<Maleta>> enviosPorDia = DataParser.agruparEnviosPorDia(todosEnvios);
            System.out.printf("  Días totales con envíos: %d%n", enviosPorDia.size());

            if (enviosPorDia.isEmpty()) {
                System.out.println("  ERROR: No se encontraron envíos para procesar.");
                System.exit(1);
            }

            // Filtrar días según el rango solicitado
            int ultimoDiaDisponible = Collections.max(enviosPorDia.keySet());
            int diaFinEfectivo = (modo == Modo.COLLAPSE) ? ultimoDiaDisponible : Math.min(diaFin, ultimoDiaDisponible);

            // Para collapse, tomar desde diaInicio hasta el último día disponible
            Map<Integer, List<Maleta>> diasAProcesar = new TreeMap<>();
            for (Map.Entry<Integer, List<Maleta>> entry : enviosPorDia.entrySet()) {
                int day = entry.getKey();
                if (day >= diaInicio && day <= diaFinEfectivo) {
                    diasAProcesar.put(day, entry.getValue());
                }
            }

            System.out.printf("  Días a procesar: %d (de %s a %s)%n",
                    diasAProcesar.size(),
                    TimeUtils.dayIndexToDate(diaInicio),
                    TimeUtils.dayIndexToDate(diaFinEfectivo));

            if (diasAProcesar.isEmpty()) {
                System.out.println("  WARN: No hay envíos en el rango solicitado.");
                System.exit(0);
            }

            // Recalcular maletas físicas del rango
            long maletasFisicasRango = 0;
            for (List<Maleta> lista : diasAProcesar.values()) {
                for (Maleta m : lista) maletasFisicasRango += m.getCantidad();
            }
            System.out.printf("  Maletas físicas en rango: %,d%n", maletasFisicasRango);
            System.out.println();

            // Liberar memoria
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
            List<Maleta> enviosArrastre = new ArrayList<>();
            boolean colapso = false;
            String fechaColapso = "";
            String maletaColapso = "";

            for (Map.Entry<Integer, List<Maleta>> entry : diasAProcesar.entrySet()) {
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

                // Instanciar vuelos para este día + DIAS_EXTRA
                List<Vuelo> vuelosDia = DataParser.instanciarVuelos(
                        templates, dayIndex, dayIndex + DIAS_EXTRA_VUELOS, aeropuertos);

                FlightIndex flightIndex = new FlightIndex(vuelosDia);

                // Generar solución inicial
                PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(
                        enviosAProcesar, flightIndex, aeropuertos);

                double costoInicial = CostCalculator.calcularCosto(planInicial);
                System.out.printf("│ Solución inicial: %,d/%,d envíos asignados | Costo: %.2f%n",
                        planInicial.getTotalMaletasAsignadas(), planInicial.getTotalMaletas(), costoInicial);

                // Ejecutar ALNS
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

                // ── Verificación de colapso (solo modo COLLAPSE) ──
                if (modo == Modo.COLLAPSE) {
                    // Verificar maletas asignadas con SLA violado
                    for (Map.Entry<Maleta, Ruta> asig : mejorPlan.getAsignaciones().entrySet()) {
                        if (asig.getKey().isSLAExpirado(asig.getValue().getHoraLlegadaFinal())) {
                            colapso = true;
                            fechaColapso = TimeUtils.dayIndexToDate(dayIndex);
                            maletaColapso = asig.getKey().toString();
                            break;
                        }
                    }
                    // Verificar maletas no asignadas (tampoco pueden entregarse a tiempo)
                    if (!colapso && !mejorPlan.getMaletasNoAsignadas().isEmpty()) {
                        colapso = true;
                        fechaColapso = TimeUtils.dayIndexToDate(dayIndex);
                        Maleta primera = mejorPlan.getMaletasNoAsignadas().get(0);
                        maletaColapso = primera.toString();
                    }

                    if (colapso) {
                        System.out.printf("│ *** COLAPSO DETECTADO ***%n");
                        System.out.printf("│ Fecha: %s%n", fechaColapso);
                        System.out.printf("│ Maleta: %s%n", maletaColapso);
                        System.out.println("└──────────────────────────────────────────────────────────┘");
                        System.out.println();

                        // Acumular estadísticas del último día parcial
                        totalEnviosAsignados += mejorPlan.getTotalMaletasAsignadas();
                        totalMaletasFisicasAsignadas += mejorPlan.getTotalMaletasFisicasAsignadas();
                        costoGlobalAcumulado += CostCalculator.calcularCosto(mejorPlan);
                        diasProcesados++;
                        break; // Detener procesamiento
                    }
                }

                // ── Acumular estadísticas (daily/periodo: contar violaciones sin detenerse) ──
                totalEnviosAsignados += mejorPlan.getTotalMaletasAsignadas();
                totalMaletasFisicasAsignadas += mejorPlan.getTotalMaletasFisicasAsignadas();
                costoGlobalAcumulado += CostCalculator.calcularCosto(mejorPlan);

                // Contar violaciones SLA (para daily y periodo)
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

                if (diasProcesados % 50 == 0) {
                    System.out.printf("    *** Progreso: %d/%d días procesados ***%n%n",
                            diasProcesados, diasAProcesar.size());
                    System.gc();
                }
            }

            long tiempoGlobalFin = System.currentTimeMillis();

            // ═══════════════════════════════════════════════════════════
            // FASE 4: Reporte Global Final
            // ═══════════════════════════════════════════════════════════
            if (modo == Modo.COLLAPSE && colapso) {
                imprimirReporteColapso(diasProcesados, fechaColapso, maletaColapso,
                        totalEnviosProcesados, totalEnviosAsignados,
                        totalMaletasFisicasAsignadas, maletasFisicasRango,
                        costoGlobalAcumulado, tiempoGlobalFin - tiempoGlobalInicio);
            } else {
                imprimirReporteGlobal(modo, diasProcesados, totalEnviosProcesados, totalEnviosAsignados,
                        totalMaletasFisicasAsignadas, maletasFisicasRango,
                        costoGlobalAcumulado, totalViolacionesSLA, enviosArrastre.size(),
                        tiempoGlobalFin - tiempoGlobalInicio);
            }

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
     * Imprime instrucciones de uso.
     */
    private static void imprimirUso() {
        System.out.println("Uso:");
        System.out.println("  java Main daily    <yyyymmdd>                         → un solo día");
        System.out.println("  java Main periodo  <yyyymmdd_inicio> <yyyymmdd_fin>   → rango de fechas");
        System.out.println("  java Main collapse <yyyymmdd>                         → desde fecha hasta colapso");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  java Main daily    20260606");
        System.out.println("  java Main periodo  20260606 20260610");
        System.out.println("  java Main collapse 20260606");
    }

    /**
     * Imprime el reporte global para modos daily y periodo.
     */
    private static void imprimirReporteGlobal(Modo modo, int dias, int totalProcesados, int totalAsignados,
                                               int maletasFisicasAsignadas, long maletasFisicasTotal,
                                               double costoAcumulado, int violacionesSLA,
                                               int noAsignadosFinales, long tiempoMs) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.printf( "║            REPORTE GLOBAL FINAL — Modo: %-10s             ║%n", modo);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ Días procesados:              %,10d                        ║%n", dias);
        System.out.printf( "║ Total envíos procesados:      %,10d                        ║%n", totalProcesados);
        System.out.printf( "║ Total envíos asignados:       %,10d                        ║%n", totalAsignados);
        System.out.printf( "║ Tasa de asignación:           %9.2f%%                        ║%n",
                totalProcesados > 0 ? (double) totalAsignados / totalProcesados * 100 : 0);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ Maletas físicas asignadas:    %,10d                        ║%n", maletasFisicasAsignadas);
        System.out.printf( "║ Maletas físicas totales:      %,10d                        ║%n", maletasFisicasTotal);
        System.out.printf( "║ Tasa de maletas:              %9.2f%%                        ║%n",
                maletasFisicasTotal > 0 ? (double) maletasFisicasAsignadas / maletasFisicasTotal * 100 : 0);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ Costo global acumulado:       %,14.2f                    ║%n", costoAcumulado);
        System.out.printf( "║ Costo promedio por día:       %,14.2f                    ║%n",
                dias > 0 ? costoAcumulado / dias : 0);
        System.out.printf( "║ Violaciones SLA:              %,10d                        ║%n", violacionesSLA);
        System.out.printf( "║ Envíos no asignados (final):  %,10d                        ║%n", noAsignadosFinales);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ Tiempo total de ejecución:    %10.2f segundos               ║%n", tiempoMs / 1000.0);
        System.out.printf( "║ Tiempo promedio por día:      %10.2f segundos               ║%n",
                dias > 0 ? tiempoMs / 1000.0 / dias : 0);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Imprime el reporte de colapso para modo collapse.
     */
    private static void imprimirReporteColapso(int diasProcesados, String fechaColapso, String maletaColapso,
                                                int totalProcesados, int totalAsignados,
                                                int maletasFisicasAsignadas, long maletasFisicasTotal,
                                                double costoAcumulado, long tiempoMs) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          ⚠  REPORTE DE COLAPSO — Modo: COLLAPSE                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ *** SISTEMA COLAPSÓ en: %-38s ║%n", fechaColapso);
        System.out.printf( "║ Maleta causante: %-45s ║%n",
                maletaColapso.length() > 45 ? maletaColapso.substring(0, 42) + "..." : maletaColapso);
        System.out.printf( "║ Días procesados antes del colapso: %,5d                        ║%n", diasProcesados);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ Total envíos procesados:      %,10d                        ║%n", totalProcesados);
        System.out.printf( "║ Total envíos asignados:       %,10d                        ║%n", totalAsignados);
        System.out.printf( "║ Tasa de asignación:           %9.2f%%                        ║%n",
                totalProcesados > 0 ? (double) totalAsignados / totalProcesados * 100 : 0);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ Maletas físicas asignadas:    %,10d                        ║%n", maletasFisicasAsignadas);
        System.out.printf( "║ Maletas físicas totales:      %,10d                        ║%n", maletasFisicasTotal);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ Costo global acumulado:       %,14.2f                    ║%n", costoAcumulado);
        System.out.printf( "║ Tiempo total de ejecución:    %10.2f segundos               ║%n", tiempoMs / 1000.0);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    }
}
