package alns;

import java.util.Random;

/**
 * Criterio de aceptación basado en Simulated Annealing (SA).
 * 
 * Una nueva solución es aceptada si:
 * - Es mejor que la actual (Δcosto < 0), o
 * - Con probabilidad exp(-Δcosto / T), donde T es la temperatura actual
 * 
 * La temperatura se reduce geométricamente: T = T * α
 */
public class SimulatedAnnealing {
    private double temperatura;
    private final double tasaEnfriamiento; // α ≈ 0.995
    private final Random random;

    public SimulatedAnnealing(double temperaturaInicial, double tasaEnfriamiento) {
        this.temperatura = temperaturaInicial;
        this.tasaEnfriamiento = tasaEnfriamiento;
        this.random = new Random();
    }

    /**
     * Determina si se acepta la nueva solución basándose en el criterio de SA.
     *
     * @param costoNuevo  Costo de la nueva solución candidata
     * @param costoActual Costo de la solución actual
     * @return true si la nueva solución debe ser aceptada
     */
    public boolean aceptar(double costoNuevo, double costoActual) {
        double delta = costoNuevo - costoActual;

        // Si la nueva solución es mejor, siempre aceptar
        if (delta < 0) {
            return true;
        }

        // Si es peor, aceptar con probabilidad exp(-delta / T)
        if (temperatura <= 0) {
            return false;
        }

        double probabilidad = Math.exp(-delta / temperatura);
        return random.nextDouble() < probabilidad;
    }

    /**
     * Enfría la temperatura según el esquema geométrico.
     */
    public void enfriar() {
        temperatura *= tasaEnfriamiento;
    }

    /**
     * Retorna la temperatura actual.
     */
    public double getTemperatura() {
        return temperatura;
    }

    /**
     * Verifica si la temperatura ha caído a un nivel mínimo.
     */
    public boolean estaFrio() {
        return temperatura < 0.01;
    }

    @Override
    public String toString() {
        return String.format("SA{T=%.4f, α=%.4f}", temperatura, tasaEnfriamiento);
    }
}
