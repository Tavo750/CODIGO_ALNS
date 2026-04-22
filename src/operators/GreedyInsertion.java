package operators;

import model.*;
import util.FlightIndex;

import java.util.*;

/**
 * Operador de reparación Greedy (ávido) con perturbación aleatoria.
 * 
 * Estrategia: Para cada envío no asignado, encuentra la ruta de menor costo
 * incremental (con ruido aleatorio) y la inserta inmediatamente.
 * Usa FlightIndex para búsqueda eficiente de vuelos.
 */
public class GreedyInsertion implements RepairOperator {
    private final Random random = new Random();
    private static final double NOISE_FACTOR = 0.15; // 15% de ruido en evaluación

    @Override
    public PlanDeRutas repair(PlanDeRutas planParcial, FlightIndex flightIndex) {
        PlanDeRutas plan = planParcial.copiar();

        // Obtener envíos a reinsertar
        List<Maleta> maletasPendientes = new ArrayList<>(plan.getMaletasNoAsignadas());

        // Ordenar por prioridad con perturbación aleatoria en el orden
        Collections.shuffle(maletasPendientes, random);
        maletasPendientes.sort((m1, m2) -> {
            int cmp = Integer.compare(m1.getPrioridad(), m2.getPrioridad());
            if (cmp != 0) return cmp;
            return Long.compare(m1.getSlaLimite(), m2.getSlaLimite());
        });

        for (Maleta maleta : maletasPendientes) {
            Ruta mejorRuta = null;
            double mejorCosto = Double.MAX_VALUE;

            // Encontrar todas las rutas factibles para este envío
            List<Ruta> rutasFactibles = encontrarRutasFactibles(maleta, plan, flightIndex);

            for (Ruta ruta : rutasFactibles) {
                // Agregar ruido aleatorio al costo para diversificación
                double costo = evaluarCostoInsercion(maleta, ruta);
                double noise = costo * NOISE_FACTOR * (random.nextDouble() * 2 - 1);
                double costoConRuido = costo + noise;

                if (costoConRuido < mejorCosto) {
                    mejorCosto = costoConRuido;
                    mejorRuta = ruta;
                }
            }

            if (mejorRuta != null) {
                plan.asignarMaleta(maleta, mejorRuta);
            }
        }

        return plan;
    }

    /**
     * Encuentra rutas factibles para un envío usando FlightIndex.
     * Usa BFS acotado por tiempo (desde creación del envío hasta su SLA deadline).
     * Máximo 3 vuelos / 2 escalas.
     */
    public List<Ruta> encontrarRutasFactibles(Maleta maleta, PlanDeRutas plan, FlightIndex flightIndex) {
        List<Ruta> rutas = new ArrayList<>();
        String origen = maleta.getAeropuertoOrigen();
        String destino = maleta.getAeropuertoDestino();
        long despuesUTC = maleta.getFechaCreacionUTC();
        long deadlineUTC = maleta.getSlaLimite();
        int cantidad = maleta.getCantidad();

        // BFS para encontrar rutas (máximo 3 vuelos / 2 escalas)
        Queue<Ruta> cola = new LinkedList<>();

        // Iniciar con vuelos que salen del origen después de la creación del envío
        List<Vuelo> vuelosIniciales = flightIndex.buscarVuelosDesdeHasta(
                origen, despuesUTC, deadlineUTC, cantidad);

        for (Vuelo vuelo : vuelosIniciales) {
            Vuelo vueloPlan = plan.getVuelo(vuelo.getId());
            if (vueloPlan == null || !vueloPlan.tieneEspacioPara(cantidad)) continue;

            Ruta ruta = new Ruta();
            ruta.agregarVuelo(vuelo);

            if (vuelo.getDestino().equals(destino)) {
                rutas.add(ruta);
                if (rutas.size() >= 15) return rutas;
            } else {
                cola.add(ruta);
            }
        }

        // Expandir BFS con conexiones (máximo 2 escalas)
        while (!cola.isEmpty() && rutas.size() < 15) {
            Ruta rutaActual = cola.poll();
            if (rutaActual.getNumeroVuelos() >= 3) continue;

            Vuelo ultimoVuelo = rutaActual.getVuelos().get(rutaActual.getNumeroVuelos() - 1);

            // Buscar vuelos desde el destino del último vuelo, con tiempo de conexión mínimo
            long tiempoMinSalida = ultimoVuelo.getHoraLlegada() + 30;
            List<Vuelo> siguientes = flightIndex.buscarVuelosDesdeHasta(
                    ultimoVuelo.getDestino(), tiempoMinSalida, deadlineUTC, cantidad);

            for (Vuelo siguienteVuelo : siguientes) {
                Vuelo siguientePlan = plan.getVuelo(siguienteVuelo.getId());
                if (siguientePlan == null || !siguientePlan.tieneEspacioPara(cantidad)) continue;

                // Evitar ciclos
                boolean ciclo = false;
                for (Vuelo v : rutaActual.getVuelos()) {
                    if (v.getOrigen().equals(siguienteVuelo.getDestino())) {
                        ciclo = true;
                        break;
                    }
                }
                if (ciclo) continue;

                Ruta nuevaRuta = rutaActual.copiar();
                nuevaRuta.agregarVuelo(siguienteVuelo);

                if (siguienteVuelo.getDestino().equals(destino)) {
                    rutas.add(nuevaRuta);
                    if (rutas.size() >= 15) return rutas;
                } else if (nuevaRuta.getNumeroVuelos() < 3) {
                    cola.add(nuevaRuta);
                }
            }
        }

        return rutas;
    }

    /**
     * Evalúa el costo de insertar un envío en una ruta específica.
     * Considera tiempo de transporte, conexiones, espera, SLA y prioridad.
     */
    public double evaluarCostoInsercion(Maleta maleta, Ruta ruta) {
        double costo = 0;

        // Costo por tiempo de transporte
        costo += ruta.getTiempoTotal() * 0.5;

        // Costo por número de conexiones (penalizar escalas)
        costo += ruta.getNumeroConexiones() * 50.0;

        // Costo por tiempo de espera en tránsito
        costo += ruta.getTiempoEspera() * 0.3;

        // Penalización si excede SLA
        long horaLlegada = ruta.getHoraLlegadaFinal();
        if (maleta.isSLAExpirado(horaLlegada)) {
            long exceso = horaLlegada - maleta.getSlaLimite();
            costo += exceso * 5.0;
        }

        // Ajuste por prioridad
        if (maleta.getPrioridad() == 1) {
            costo *= 1.5;
        } else if (maleta.getPrioridad() == 2) {
            costo *= 1.2;
        }

        // Ajuste por cantidad (envíos grandes son más costosos de reubicar)
        costo *= (1.0 + maleta.getCantidad() * 0.05);

        return costo;
    }

    @Override
    public String getNombre() {
        return "Greedy";
    }
}
