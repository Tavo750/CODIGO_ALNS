package model;

/**
 * Representa un envío (grupo de maletas) en el sistema logístico aeroportuario.
 * Cada envío tiene un origen, destino, fecha de creación, cantidad de maletas,
 * límite de SLA y prioridad derivada.
 * 
 * Formato de datos: id_envío-aaaammdd-hh-mm-dest-###-IdClien
 * Ejemplo: 000000001-20260102-00-55-SPIM-002-0019169
 */
public class Maleta {
    private final String id;                  // ID del envío (9 dígitos)
    private final String aeropuertoOrigen;    // Código ICAO del aeropuerto origen
    private final String aeropuertoDestino;   // Código ICAO del aeropuerto destino
    private final long fechaCreacionUTC;      // Minutos absolutos UTC desde epoch (2026-01-01 00:00 UTC)
    private final int slaLimite;              // Deadline en minutos absolutos UTC
    private final int prioridad;              // 1 = alta, 2 = media, 3 = baja (derivada de cantidad)
    private final int cantidad;               // Cantidad de maletas físicas (1-999)
    private final String idCliente;           // ID del cliente (7 dígitos)

    public Maleta(String id, String aeropuertoOrigen, String aeropuertoDestino,
                  long fechaCreacionUTC, int slaLimite, int prioridad,
                  int cantidad, String idCliente) {
        this.id = id;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.fechaCreacionUTC = fechaCreacionUTC;
        this.slaLimite = slaLimite;
        this.prioridad = prioridad;
        this.cantidad = cantidad;
        this.idCliente = idCliente;
    }

    /**
     * Verifica si el SLA del envío habría expirado dado un tiempo de entrega absoluto UTC.
     */
    public boolean isSLAExpirado(long tiempoEntregaUTC) {
        return tiempoEntregaUTC > slaLimite;
    }

    /**
     * Calcula cuántos minutos de holgura quedan antes de que el SLA expire,
     * dado un tiempo de entrega absoluto UTC.
     * Valores negativos indican que el SLA ya fue violado.
     */
    public long getHolguraSLA(long tiempoEntregaUTC) {
        return slaLimite - tiempoEntregaUTC;
    }

    /**
     * Retorna el tiempo de transporte en minutos para una ruta dada.
     * Usa la hora de llegada del último vuelo menos la fecha de creación del envío.
     */
    public long getTiempoTransporte(long horaLlegadaUTC) {
        return horaLlegadaUTC - fechaCreacionUTC;
    }

    // === Getters ===
    public String getId() { return id; }
    public String getAeropuertoOrigen() { return aeropuertoOrigen; }
    public String getAeropuertoDestino() { return aeropuertoDestino; }
    public long getFechaCreacionUTC() { return fechaCreacionUTC; }
    public int getSlaLimite() { return slaLimite; }
    public int getPrioridad() { return prioridad; }
    public int getCantidad() { return cantidad; }
    public String getIdCliente() { return idCliente; }

    @Override
    public String toString() {
        return "Maleta{" + id + ", " + aeropuertoOrigen + "→" + aeropuertoDestino +
               ", Cant=" + cantidad + ", SLA=" + slaLimite + ", P=" + prioridad + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Maleta maleta = (Maleta) o;
        return id.equals(maleta.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
