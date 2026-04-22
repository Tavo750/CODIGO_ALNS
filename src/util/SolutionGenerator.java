package util;

import model.*;
import java.util.*;

/**
 * Genera una solución inicial factible para el problema de asignación de envíos.
 * Utiliza FlightIndex para búsqueda eficiente de rutas y un enfoque greedy:
 * para cada envío, busca la ruta más rápida desde su origen hasta su destino.
 */
public class SolutionGenerator {

    /**
     * Genera un plan inicial factible asignando envíos a rutas mediante BFS.
     *
     * @param maletas      Lista de envíos a asignar
     * @param flightIndex  Índice de vuelos para búsqueda rápida
     * @param aeropuertos  Mapa de aeropuertos (para crear almacenes)
     * @return Un plan de rutas con el máximo de envíos asignados factiblemente
     */
    public static PlanDeRutas generarPlanInicial(List<Maleta> maletas, FlightIndex flightIndex,
                                                  Map<String, Aeropuerto> aeropuertos) {
        PlanDeRutas plan = new PlanDeRutas();

        // Registrar vuelos en el plan (copias independientes)
        for (Vuelo vuelo : flightIndex.getTodosLosVuelos()) {
            plan.registrarVuelo(vuelo.copiar());
        }

        // Registrar almacenes desde los aeropuertos
        for (Aeropuerto aero : aeropuertos.values()) {
            plan.registrarAlmacen(new Almacen(aero.getCodigoICAO(), aero.getCapacidadAlmacen()));
        }

        // Ordenar envíos por prioridad y SLA (más urgentes primero)
        List<Maleta> maletasOrdenadas = new ArrayList<>(maletas);
        maletasOrdenadas.sort((m1, m2) -> {
            int cmp = Integer.compare(m1.getPrioridad(), m2.getPrioridad());
            if (cmp != 0) return cmp;
            return Long.compare(m1.getSlaLimite(), m2.getSlaLimite());
        });

        // Asignar cada envío a la mejor ruta disponible
        int asignadas = 0;
        for (Maleta maleta : maletasOrdenadas) {
            Ruta mejorRuta = encontrarMejorRuta(maleta, plan, flightIndex);
            if (mejorRuta != null) {
                boolean ok = plan.asignarMaleta(maleta, mejorRuta);
                if (ok) asignadas++;
                else plan.agregarMaletaNoAsignada(maleta);
            } else {
                plan.agregarMaletaNoAsignada(maleta);
            }
        }

        // Calcular costo inicial
        CostCalculator.calcularCosto(plan);

        return plan;
    }

    /**
     * Encuentra la mejor ruta para un envío usando BFS acotado por tiempo.
     */
    private static Ruta encontrarMejorRuta(Maleta maleta, PlanDeRutas plan, FlightIndex flightIndex) {
        String origen = maleta.getAeropuertoOrigen();
        String destino = maleta.getAeropuertoDestino();
        long despuesUTC = maleta.getFechaCreacionUTC();
        long deadlineUTC = maleta.getSlaLimite();
        int cantidad = maleta.getCantidad();

        List<Ruta> rutasEncontradas = new ArrayList<>();
        Queue<Ruta> cola = new LinkedList<>();

        // Iniciar BFS desde el origen
        List<Vuelo> vuelosIniciales = flightIndex.buscarVuelosDesdeHasta(
                origen, despuesUTC, deadlineUTC, cantidad);

        for (Vuelo vuelo : vuelosIniciales) {
            Vuelo vueloPlan = plan.getVuelo(vuelo.getId());
            if (vueloPlan == null || !vueloPlan.tieneEspacioPara(cantidad)) continue;

            Ruta ruta = new Ruta();
            ruta.agregarVuelo(vuelo);

            if (vuelo.getDestino().equals(destino)) {
                rutasEncontradas.add(ruta);
            } else {
                cola.add(ruta);
            }
        }

        // Expandir BFS (máximo 3 vuelos)
        while (!cola.isEmpty() && rutasEncontradas.size() < 5) {
            Ruta actual = cola.poll();
            if (actual.getNumeroVuelos() >= 3) continue;

            Vuelo ultimo = actual.getVuelos().get(actual.getNumeroVuelos() - 1);

            // Buscar conexiones con al menos 30 min de tránsito
            long tiempoMinSalida = ultimo.getHoraLlegada() + 30;
            List<Vuelo> siguientes = flightIndex.buscarVuelosDesdeHasta(
                    ultimo.getDestino(), tiempoMinSalida, deadlineUTC, cantidad);

            for (Vuelo siguiente : siguientes) {
                Vuelo siguientePlan = plan.getVuelo(siguiente.getId());
                if (siguientePlan == null || !siguientePlan.tieneEspacioPara(cantidad)) continue;

                // Evitar ciclos
                boolean ciclo = false;
                for (Vuelo v : actual.getVuelos()) {
                    if (v.getOrigen().equals(siguiente.getDestino())) {
                        ciclo = true;
                        break;
                    }
                }
                if (ciclo) continue;

                Ruta nuevaRuta = actual.copiar();
                nuevaRuta.agregarVuelo(siguiente);

                if (siguiente.getDestino().equals(destino)) {
                    rutasEncontradas.add(nuevaRuta);
                } else if (nuevaRuta.getNumeroVuelos() < 3) {
                    cola.add(nuevaRuta);
                }
            }
        }

        // Seleccionar la mejor ruta (menor tiempo total de transporte)
        if (rutasEncontradas.isEmpty()) return null;

        rutasEncontradas.sort(Comparator.comparingLong(Ruta::getTiempoTotal));
        return rutasEncontradas.get(0);
    }
}
