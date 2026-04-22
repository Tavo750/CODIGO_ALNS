package util;

import model.Vuelo;

import java.util.*;

/**
 * Índice de vuelos para búsqueda rápida.
 * Organiza los vuelos por aeropuerto de origen, permitiendo búsquedas O(1)
 * en lugar de iterar todos los vuelos O(n).
 */
public class FlightIndex {
    // Aeropuerto origen → lista de vuelos ordenados por hora de salida
    private final Map<String, List<Vuelo>> vuelosPorOrigen;

    public FlightIndex() {
        this.vuelosPorOrigen = new HashMap<>();
    }

    /**
     * Construye el índice a partir de una lista de vuelos.
     */
    public FlightIndex(List<Vuelo> vuelos) {
        this.vuelosPorOrigen = new HashMap<>();
        for (Vuelo vuelo : vuelos) {
            vuelosPorOrigen.computeIfAbsent(vuelo.getOrigen(), k -> new ArrayList<>()).add(vuelo);
        }
        // Ordenar cada lista por hora de salida
        for (List<Vuelo> lista : vuelosPorOrigen.values()) {
            lista.sort(Comparator.comparingLong(Vuelo::getHoraSalida));
        }
    }

    /**
     * Busca todos los vuelos que salen de un aeropuerto después de cierta hora UTC,
     * que tengan espacio suficiente para la cantidad indicada.
     *
     * @param origen   Código ICAO del aeropuerto de origen
     * @param despuesUTC Tiempo mínimo de salida (minutos UTC absolutos)
     * @param cantidad Cantidad de maletas que se necesita transportar
     * @return Lista de vuelos que cumplen los criterios
     */
    public List<Vuelo> buscarVuelosDesde(String origen, long despuesUTC, int cantidad) {
        List<Vuelo> resultado = new ArrayList<>();
        List<Vuelo> vuelosOrigen = vuelosPorOrigen.get(origen);

        if (vuelosOrigen == null) return resultado;

        // Búsqueda binaria para el primer vuelo con horaSalida >= despuesUTC
        int idx = busquedaBinaria(vuelosOrigen, despuesUTC);

        for (int i = idx; i < vuelosOrigen.size(); i++) {
            Vuelo vuelo = vuelosOrigen.get(i);
            if (vuelo.tieneEspacioPara(cantidad)) {
                resultado.add(vuelo);
            }
        }

        return resultado;
    }

    /**
     * Busca todos los vuelos que salen de un aeropuerto después de cierta hora UTC
     * y antes de un deadline, que tengan espacio suficiente.
     *
     * @param origen     Código ICAO del aeropuerto de origen
     * @param despuesUTC Tiempo mínimo de salida
     * @param antesUTC   Tiempo máximo de salida (deadline SLA)
     * @param cantidad   Cantidad de maletas requerida
     * @return Lista de vuelos filtrados
     */
    public List<Vuelo> buscarVuelosDesdeHasta(String origen, long despuesUTC, long antesUTC, int cantidad) {
        List<Vuelo> resultado = new ArrayList<>();
        List<Vuelo> vuelosOrigen = vuelosPorOrigen.get(origen);

        if (vuelosOrigen == null) return resultado;

        int idx = busquedaBinaria(vuelosOrigen, despuesUTC);

        for (int i = idx; i < vuelosOrigen.size(); i++) {
            Vuelo vuelo = vuelosOrigen.get(i);
            if (vuelo.getHoraSalida() > antesUTC) break;  // Ya pasó el deadline
            if (vuelo.tieneEspacioPara(cantidad)) {
                resultado.add(vuelo);
            }
        }

        return resultado;
    }

    /**
     * Retorna todos los vuelos indexados como una lista plana.
     */
    public List<Vuelo> getTodosLosVuelos() {
        List<Vuelo> todos = new ArrayList<>();
        for (List<Vuelo> lista : vuelosPorOrigen.values()) {
            todos.addAll(lista);
        }
        return todos;
    }

    /**
     * Retorna los aeropuertos que tienen vuelos de salida.
     */
    public Set<String> getAeropuertosConSalida() {
        return vuelosPorOrigen.keySet();
    }

    /**
     * Retorna la cantidad total de vuelos indexados.
     */
    public int getTotalVuelos() {
        int total = 0;
        for (List<Vuelo> lista : vuelosPorOrigen.values()) {
            total += lista.size();
        }
        return total;
    }

    /**
     * Búsqueda binaria para encontrar el primer vuelo con horaSalida >= target.
     */
    private int busquedaBinaria(List<Vuelo> vuelos, long target) {
        int lo = 0, hi = vuelos.size();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (vuelos.get(mid).getHoraSalida() < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
