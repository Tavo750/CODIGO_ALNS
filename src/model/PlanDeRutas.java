package model;

import java.util.*;

/**
 * Representa una solución completa del problema: la asignación de cada envío a una ruta
 * dentro de la red de vuelos. Gestiona también la ocupación de vuelos y almacenes.
 * La capacidad se maneja por cantidad de maletas (no conteo de envíos).
 */
public class PlanDeRutas {
    private final Map<Maleta, Ruta> asignaciones;         // Envío → Ruta asignada
    private final List<Maleta> maletasNoAsignadas;         // Envíos sin ruta
    private final Map<String, Vuelo> vuelosMap;            // ID vuelo → Vuelo (con tracking)
    private final Map<String, Almacen> almacenesMap;       // Aeropuerto → Almacén
    private double costoCalculado = -1;                    // Cache de costo

    public PlanDeRutas() {
        this.asignaciones = new LinkedHashMap<>();
        this.maletasNoAsignadas = new ArrayList<>();
        this.vuelosMap = new LinkedHashMap<>();
        this.almacenesMap = new LinkedHashMap<>();
    }

    /**
     * Crea una copia profunda del plan de rutas.
     * Los vuelos se copian para que las asignaciones de maletas sean independientes.
     */
    public PlanDeRutas copiar() {
        PlanDeRutas copia = new PlanDeRutas();

        // Copiar vuelos (con sus listas de maletas independientes)
        Map<String, Vuelo> vuelosCopia = new LinkedHashMap<>();
        for (Map.Entry<String, Vuelo> entry : this.vuelosMap.entrySet()) {
            vuelosCopia.put(entry.getKey(), entry.getValue().copiar());
        }
        copia.vuelosMap.putAll(vuelosCopia);

        // Copiar almacenes
        for (Map.Entry<String, Almacen> entry : this.almacenesMap.entrySet()) {
            copia.almacenesMap.put(entry.getKey(), entry.getValue().copiar());
        }

        // Copiar asignaciones
        for (Map.Entry<Maleta, Ruta> entry : this.asignaciones.entrySet()) {
            copia.asignaciones.put(entry.getKey(), entry.getValue().copiar());
        }

        // Copiar envíos no asignados
        copia.maletasNoAsignadas.addAll(this.maletasNoAsignadas);

        return copia;
    }

    /**
     * Registra un vuelo en el plan.
     */
    public void registrarVuelo(Vuelo vuelo) {
        vuelosMap.put(vuelo.getId(), vuelo);
    }

    /**
     * Registra un almacén en el plan.
     */
    public void registrarAlmacen(Almacen almacen) {
        almacenesMap.put(almacen.getAeropuerto(), almacen);
    }

    /**
     * Asigna un envío a una ruta, actualizando la ocupación de los vuelos involucrados.
     * Verifica que todos los vuelos tengan espacio suficiente para la cantidad del envío.
     * Retorna true si la asignación fue exitosa.
     */
    public boolean asignarMaleta(Maleta maleta, Ruta ruta) {
        // Verificar que todos los vuelos de la ruta tengan espacio para la cantidad
        for (Vuelo vueloRuta : ruta.getVuelos()) {
            Vuelo vueloPlan = vuelosMap.get(vueloRuta.getId());
            if (vueloPlan != null && !vueloPlan.tieneEspacioPara(maleta.getCantidad())) {
                return false;
            }
        }

        // Asignar envío a cada vuelo de la ruta
        for (Vuelo vueloRuta : ruta.getVuelos()) {
            Vuelo vueloPlan = vuelosMap.get(vueloRuta.getId());
            if (vueloPlan != null) {
                vueloPlan.asignarMaleta(maleta);
            }
        }

        asignaciones.put(maleta, ruta);
        maletasNoAsignadas.remove(maleta);
        invalidarCosto();
        return true;
    }

    /**
     * Desasigna un envío de su ruta, liberando espacio en los vuelos.
     */
    public void desasignarMaleta(Maleta maleta) {
        Ruta ruta = asignaciones.remove(maleta);
        if (ruta != null) {
            for (Vuelo vueloRuta : ruta.getVuelos()) {
                Vuelo vueloPlan = vuelosMap.get(vueloRuta.getId());
                if (vueloPlan != null) {
                    vueloPlan.removerMaleta(maleta);
                }
            }
        }
        if (!maletasNoAsignadas.contains(maleta)) {
            maletasNoAsignadas.add(maleta);
        }
        invalidarCosto();
    }

    /**
     * Agrega un envío como no asignado (para inicialización).
     */
    public void agregarMaletaNoAsignada(Maleta maleta) {
        if (!maletasNoAsignadas.contains(maleta) && !asignaciones.containsKey(maleta)) {
            maletasNoAsignadas.add(maleta);
        }
    }

    /**
     * Verifica si el plan respeta todas las restricciones de capacidad.
     */
    public boolean esFactible() {
        for (Vuelo vuelo : vuelosMap.values()) {
            if (vuelo.getCapacidadUsada() > vuelo.getCapacidad()) {
                return false;
            }
        }
        for (Almacen almacen : almacenesMap.values()) {
            if (almacen.getCapacidadUsada() > almacen.getCapacidad()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retorna la cantidad total de maletas físicas asignadas (suma de cantidades).
     */
    public int getTotalMaletasFisicasAsignadas() {
        int total = 0;
        for (Maleta m : asignaciones.keySet()) {
            total += m.getCantidad();
        }
        return total;
    }

    /**
     * Retorna la cantidad total de maletas físicas (asignadas + no asignadas).
     */
    public int getTotalMaletasFisicas() {
        int total = getTotalMaletasFisicasAsignadas();
        for (Maleta m : maletasNoAsignadas) {
            total += m.getCantidad();
        }
        return total;
    }

    /**
     * Invalida el cache de costo cuando cambia la solución.
     */
    private void invalidarCosto() {
        costoCalculado = -1;
    }

    /**
     * Retorna el costo cacheado, o -1 si no ha sido calculado.
     */
    public double getCostoCalculado() {
        return costoCalculado;
    }

    public void setCostoCalculado(double costo) {
        this.costoCalculado = costo;
    }

    // === Getters ===
    public Map<Maleta, Ruta> getAsignaciones() { return Collections.unmodifiableMap(asignaciones); }
    public List<Maleta> getMaletasNoAsignadas() { return Collections.unmodifiableList(maletasNoAsignadas); }
    public Map<String, Vuelo> getVuelosMap() { return Collections.unmodifiableMap(vuelosMap); }
    public Map<String, Almacen> getAlmacenesMap() { return Collections.unmodifiableMap(almacenesMap); }
    public int getTotalMaletasAsignadas() { return asignaciones.size(); }
    public int getTotalMaletas() { return asignaciones.size() + maletasNoAsignadas.size(); }

    /**
     * Obtiene el vuelo del plan por su ID.
     */
    public Vuelo getVuelo(String vueloId) {
        return vuelosMap.get(vueloId);
    }

    @Override
    public String toString() {
        return "PlanDeRutas{envios=" + asignaciones.size() +
               ", noAsignados=" + maletasNoAsignadas.size() +
               ", vuelos=" + vuelosMap.size() +
               ", costo=" + String.format("%.2f", costoCalculado) + "}";
    }
}
