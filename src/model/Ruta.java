package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa la secuencia de vuelos que sigue un envío desde su origen hasta su destino.
 * Los tiempos se manejan en minutos absolutos UTC.
 */
public class Ruta {
    private final List<Vuelo> vuelos;

    public Ruta() {
        this.vuelos = new ArrayList<>();
    }

    public Ruta(List<Vuelo> vuelos) {
        this.vuelos = new ArrayList<>(vuelos);
    }

    /**
     * Crea una copia de esta ruta (referencias a los mismos vuelos).
     */
    public Ruta copiar() {
        return new Ruta(this.vuelos);
    }

    /**
     * Agrega un vuelo al final de la ruta.
     */
    public void agregarVuelo(Vuelo vuelo) {
        vuelos.add(vuelo);
    }

    /**
     * Calcula el tiempo total de transporte de la ruta en minutos
     * (desde la salida del primer vuelo hasta la llegada del último vuelo).
     */
    public long getTiempoTotal() {
        if (vuelos.isEmpty()) return 0;
        return vuelos.get(vuelos.size() - 1).getHoraLlegada() - vuelos.get(0).getHoraSalida();
    }

    /**
     * Retorna el tiempo absoluto UTC de llegada al destino final.
     */
    public long getHoraLlegadaFinal() {
        if (vuelos.isEmpty()) return 0;
        return vuelos.get(vuelos.size() - 1).getHoraLlegada();
    }

    /**
     * Retorna el tiempo absoluto UTC de salida del primer vuelo.
     */
    public long getHoraSalidaInicial() {
        if (vuelos.isEmpty()) return 0;
        return vuelos.get(0).getHoraSalida();
    }

    /**
     * Calcula el tiempo total de espera en tránsito (tiempo entre vuelos consecutivos).
     */
    public long getTiempoEspera() {
        long espera = 0;
        for (int i = 1; i < vuelos.size(); i++) {
            espera += vuelos.get(i).getHoraSalida() - vuelos.get(i - 1).getHoraLlegada();
        }
        return espera;
    }

    /**
     * Retorna el número de conexiones (escalas) en la ruta.
     */
    public int getNumeroConexiones() {
        return Math.max(0, vuelos.size() - 1);
    }

    /**
     * Verifica que la ruta sea factible: cada vuelo siguiente sale desde donde llega el anterior,
     * y las horas de conexión son coherentes (al menos 30 min de tránsito).
     */
    public boolean esFactible() {
        for (int i = 1; i < vuelos.size(); i++) {
            Vuelo anterior = vuelos.get(i - 1);
            Vuelo siguiente = vuelos.get(i);
            // El destino del vuelo anterior debe ser el origen del siguiente
            if (!anterior.getDestino().equals(siguiente.getOrigen())) {
                return false;
            }
            // Debe haber tiempo suficiente para la conexión (al menos 30 min de tránsito)
            if (siguiente.getHoraSalida() < anterior.getHoraLlegada() + 30) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retorna el aeropuerto de origen de la ruta.
     */
    public String getOrigen() {
        return vuelos.isEmpty() ? null : vuelos.get(0).getOrigen();
    }

    /**
     * Retorna el aeropuerto de destino final de la ruta.
     */
    public String getDestino() {
        return vuelos.isEmpty() ? null : vuelos.get(vuelos.size() - 1).getDestino();
    }

    public List<Vuelo> getVuelos() { return Collections.unmodifiableList(vuelos); }
    public int getNumeroVuelos() { return vuelos.size(); }
    public boolean estaVacia() { return vuelos.isEmpty(); }

    @Override
    public String toString() {
        if (vuelos.isEmpty()) return "Ruta{vacía}";
        StringBuilder sb = new StringBuilder();
        sb.append(vuelos.get(0).getOrigen());
        for (Vuelo v : vuelos) {
            sb.append(" → ").append(v.getDestino()).append(" (").append(v.getId()).append(")");
        }
        return sb.toString();
    }
}
