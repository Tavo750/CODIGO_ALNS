package model;

/**
 * Plantilla de vuelo que se instancia diariamente.
 * Representa una ruta recurrente con horarios locales fijos.
 * 
 * Formato de datos: ORIG-DEST-HH:MM-HH:MM-CCCC
 * Ejemplo: SKBO-SEQM-03:34-04:21-0300
 */
public class FlightTemplate {
    private final String origen;            // Código ICAO de origen (e.g., "SKBO")
    private final String destino;           // Código ICAO de destino (e.g., "SEQM")
    private final int horaSalidaLocal;      // Minutos desde medianoche local del origen
    private final int horaLlegadaLocal;     // Minutos desde medianoche local del destino
    private final int capacidad;            // Capacidad en maletas (e.g., 300)
    private final int indiceTemplate;       // Índice único de la plantilla

    public FlightTemplate(int indiceTemplate, String origen, String destino,
                          int horaSalidaLocal, int horaLlegadaLocal, int capacidad) {
        this.indiceTemplate = indiceTemplate;
        this.origen = origen;
        this.destino = destino;
        this.horaSalidaLocal = horaSalidaLocal;
        this.horaLlegadaLocal = horaLlegadaLocal;
        this.capacidad = capacidad;
    }

    /**
     * Instancia esta plantilla para un día específico, convirtiendo a tiempos UTC absolutos.
     *
     * @param dayIndex   Índice del día (0 = 2026-01-01)
     * @param gmtOrigen  GMT offset del aeropuerto de origen
     * @param gmtDestino GMT offset del aeropuerto de destino
     * @return Un objeto Vuelo con tiempos absolutos en UTC
     */
    public Vuelo instanciar(int dayIndex, int gmtOrigen, int gmtDestino) {
        // Convertir hora de salida local a UTC absoluto
        // UTC = local - gmtOffset (e.g., local 03:34 con GMT-5 → UTC 08:34)
        long depUTC = (long) dayIndex * 1440 + horaSalidaLocal - (gmtOrigen * 60);

        // Convertir hora de llegada local a UTC absoluto
        long arrUTC = (long) dayIndex * 1440 + horaLlegadaLocal - (gmtDestino * 60);

        // Si la llegada UTC es antes o igual que la salida UTC, cruza medianoche
        // Puede necesitar múltiples ajustes en vuelos cross-timezone extremos
        while (arrUTC <= depUTC) {
            arrUTC += 1440; // Siguiente día
        }

        // Generar ID único: TEMPLATE_INDEX-DAY_INDEX
        String id = "V" + indiceTemplate + "-D" + dayIndex;

        return new Vuelo(id, origen, destino, capacidad, depUTC, arrUTC);
    }

    // === Getters ===
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public int getHoraSalidaLocal() { return horaSalidaLocal; }
    public int getHoraLlegadaLocal() { return horaLlegadaLocal; }
    public int getCapacidad() { return capacidad; }
    public int getIndiceTemplate() { return indiceTemplate; }

    @Override
    public String toString() {
        return "FlightTemplate{" + origen + "→" + destino +
               ", Sal=" + (horaSalidaLocal / 60) + ":" + String.format("%02d", horaSalidaLocal % 60) +
               ", Lleg=" + (horaLlegadaLocal / 60) + ":" + String.format("%02d", horaLlegadaLocal % 60) +
               ", Cap=" + capacidad + "}";
    }
}
