import model.*;
import util.*;
import java.util.*;

/**
 * Test rápido del pipeline completo con un subconjunto pequeño de datos.
 */
public class TestRapido {
    public static void main(String[] args) throws Exception {
        String dataDir = "data";

        System.out.println("=== TEST RAPIDO: Pipeline Completo ===\n");

        // 1. Aeropuertos
        Map<String, Aeropuerto> aeropuertos = DataParser.parseAeropuertos(dataDir + "/aeropuertos.txt");
        System.out.println("Aeropuertos: " + aeropuertos.size());

        // 2. Vuelos
        List<FlightTemplate> templates = DataParser.parsePlanesVuelo(dataDir + "/planes_vuelo.txt");
        System.out.println("Templates de vuelo: " + templates.size());

        // 3. Envios (subset)
        List<Maleta> envios = DataParser.parseEnvios(dataDir + "/test_envios", aeropuertos);
        System.out.println("Envios cargados: " + envios.size());

        if (envios.isEmpty()) {
            System.out.println("ERROR: No se cargaron envios");
            return;
        }

        // Mostrar algunos envios
        System.out.println("\nPrimeros 5 envios:");
        for (int i = 0; i < Math.min(5, envios.size()); i++) {
            Maleta m = envios.get(i);
            System.out.printf("  %s: %s→%s Cant=%d SLA=%d Creacion=%s%n",
                m.getId(), m.getAeropuertoOrigen(), m.getAeropuertoDestino(),
                m.getCantidad(), m.getSlaLimite(),
                TimeUtils.minutosUTCToString(m.getFechaCreacionUTC()));
        }

        // 4. Agrupar por dia
        Map<Integer, List<Maleta>> porDia = DataParser.agruparEnviosPorDia(envios);
        System.out.println("\nDias con envios: " + porDia.size());

        // Tomar primer dia
        Map.Entry<Integer, List<Maleta>> primerDia = porDia.entrySet().iterator().next();
        int dayIndex = primerDia.getKey();
        List<Maleta> enviosDia = primerDia.getValue();

        System.out.printf("\nProcesando dia %s (dayIndex=%d), %d envios%n",
            TimeUtils.dayIndexToDate(dayIndex), dayIndex, enviosDia.size());

        // 5. Instanciar vuelos
        List<Vuelo> vuelosDia = DataParser.instanciarVuelos(templates, dayIndex, dayIndex + 2, aeropuertos);
        System.out.println("Vuelos instanciados: " + vuelosDia.size());

        // 6. Flight Index
        FlightIndex flightIndex = new FlightIndex(vuelosDia);
        System.out.println("FlightIndex aeropuertos con salida: " + flightIndex.getAeropuertosConSalida().size());

        // 7. Solucion inicial
        PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(enviosDia, flightIndex, aeropuertos);
        double costoInicial = CostCalculator.calcularCosto(planInicial);
        System.out.printf("\nSolucion Inicial:%n");
        System.out.printf("  Envios asignados: %d/%d%n", planInicial.getTotalMaletasAsignadas(), planInicial.getTotalMaletas());
        System.out.printf("  Maletas fisicas asignadas: %d/%d%n",
            planInicial.getTotalMaletasFisicasAsignadas(), planInicial.getTotalMaletasFisicas());
        System.out.printf("  Costo: %.2f%n", costoInicial);

        // 8. ALNS
        if (planInicial.getTotalMaletasAsignadas() > 0) {
            System.out.println("\n=== Ejecutando ALNS (200 iter) ===");
            alns.ALNSEngine engine = new alns.ALNSEngine(
                200, 0.10, 0.40, 100.0, 0.995, 0.3, 100, flightIndex);
            PlanDeRutas mejorPlan = engine.ejecutar(planInicial);

            double costoFinal = CostCalculator.calcularCosto(mejorPlan);
            System.out.printf("\nResultado ALNS:%n");
            System.out.printf("  Envios asignados: %d/%d%n", mejorPlan.getTotalMaletasAsignadas(), mejorPlan.getTotalMaletas());
            System.out.printf("  Costo: %.2f -> %.2f (%.1f%% mejora)%n",
                costoInicial, costoFinal,
                costoInicial > 0 ? (1 - costoFinal/costoInicial) * 100 : 0);

            engine.imprimirReporteFinal();
        }

        System.out.println("\n=== TEST RAPIDO COMPLETADO ===");
    }
}
