package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa un vuelo en la red aeroportuaria.
 * La capacidad se maneja como la suma de cantidades de los envíos asignados,
 * no como conteo de objetos.
 * 
 * Los tiempos (horaSalida, horaLlegada) son minutos absolutos UTC desde epoch.
 */
public class Vuelo {
    private final String id;
    private final String origen;
    private final String destino;
    private final int capacidad;            // Capacidad total en maletas individuales
    private final long horaSalida;          // Minutos absolutos UTC desde epoch
    private final long horaLlegada;         // Minutos absolutos UTC desde epoch
    private final List<Maleta> maletasAsignadas;
    private int capacidadUsada;             // Suma de cantidades de envíos asignados

    public Vuelo(String id, String origen, String destino, int capacidad,
                 long horaSalida, long horaLlegada) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.capacidad = capacidad;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.maletasAsignadas = new ArrayList<>();
        this.capacidadUsada = 0;
    }

    /**
     * Crea una copia profunda del vuelo con su lista de maletas independiente.
     */
    public Vuelo copiar() {
        Vuelo copia = new Vuelo(id, origen, destino, capacidad, horaSalida, horaLlegada);
        copia.maletasAsignadas.addAll(this.maletasAsignadas);
        copia.capacidadUsada = this.capacidadUsada;
        return copia;
    }

    /**
     * Intenta asignar una maleta (envío) al vuelo.
     * Verifica que haya espacio suficiente para la cantidad del envío.
     * Retorna false si no hay capacidad.
     */
    public boolean asignarMaleta(Maleta maleta) {
        if (capacidadUsada + maleta.getCantidad() > capacidad) {
            return false;
        }
        if (!maletasAsignadas.contains(maleta)) {
            maletasAsignadas.add(maleta);
            capacidadUsada += maleta.getCantidad();
        }
        return true;
    }

    /**
     * Remueve una maleta (envío) del vuelo, liberando su capacidad.
     */
    public boolean removerMaleta(Maleta maleta) {
        boolean removed = maletasAsignadas.remove(maleta);
        if (removed) {
            capacidadUsada -= maleta.getCantidad();
        }
        return removed;
    }

    /**
     * Retorna el porcentaje de ocupación del vuelo (0.0 a 1.0).
     */
    public double getOcupacion() {
        return (double) capacidadUsada / capacidad;
    }

    /**
     * Verifica si el vuelo tiene al menos 1 slot disponible.
     */
    public boolean tieneEspacio() {
        return capacidadUsada < capacidad;
    }

    /**
     * Verifica si el vuelo tiene espacio para una cantidad específica de maletas.
     */
    public boolean tieneEspacioPara(int cantidad) {
        return capacidadUsada + cantidad <= capacidad;
    }

    /**
     * Retorna el número de espacios libres.
     */
    public int getEspaciosLibres() {
        return capacidad - capacidadUsada;
    }

    /**
     * Retorna la duración del vuelo en minutos.
     */
    public long getDuracion() {
        return horaLlegada - horaSalida;
    }

    // === Getters ===
    public String getId() { return id; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public int getCapacidad() { return capacidad; }
    public long getHoraSalida() { return horaSalida; }
    public long getHoraLlegada() { return horaLlegada; }
    public List<Maleta> getMaletasAsignadas() { return Collections.unmodifiableList(maletasAsignadas); }
    public int getCantidadMaletas() { return maletasAsignadas.size(); }
    public int getCapacidadUsada() { return capacidadUsada; }

    @Override
    public String toString() {
        return "Vuelo{" + id + ", " + origen + "→" + destino +
               ", Cap=" + capacidadUsada + "/" + capacidad +
               ", Sal=" + horaSalida + ", Lleg=" + horaLlegada + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vuelo vuelo = (Vuelo) o;
        return id.equals(vuelo.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
