package alns;

import java.util.*;

/**
 * Gestiona los pesos adaptativos de los operadores del ALNS.
 * 
 * Cada operador tiene un peso que determina su probabilidad de ser seleccionado.
 * Los pesos se actualizan periódicamente basándose en el rendimiento de cada operador.
 * 
 * Selección: Ruleta ponderada.
 * Actualización: w_new = (1 - r) * w_old + r * (score / uses)
 */
public class AdaptiveWeightManager {

    // Premios por rendimiento
    public static final int SIGMA_1 = 3; // Nueva mejor solución global
    public static final int SIGMA_2 = 2; // Mejora sobre la solución actual
    public static final int SIGMA_3 = 1; // Aceptada por SA (pero no mejora)

    private final double tasaReaccion; // r ∈ [0.1, 0.5]

    // Pesos y estadísticas de operadores de destrucción
    private final List<String> nombresDestruccion;
    private final double[] pesosDestruccion;
    private final double[] puntajesDestruccion;
    private final int[] usosDestruccion;

    // Pesos y estadísticas de operadores de reparación
    private final List<String> nombresReparacion;
    private final double[] pesosReparacion;
    private final double[] puntajesReparacion;
    private final int[] usosReparacion;

    private final Random random;
    private int iteracionesDesdeUltimaActualizacion;
    private final int periodoActualizacion; // Cada cuántas iteraciones recalcular pesos

    public AdaptiveWeightManager(List<String> nombresDestruccion, List<String> nombresReparacion,
                                  double tasaReaccion, int periodoActualizacion) {
        this.nombresDestruccion = new ArrayList<>(nombresDestruccion);
        this.nombresReparacion = new ArrayList<>(nombresReparacion);
        this.tasaReaccion = tasaReaccion;
        this.periodoActualizacion = periodoActualizacion;
        this.random = new Random();

        int nD = nombresDestruccion.size();
        int nR = nombresReparacion.size();

        // Inicializar pesos iguales
        this.pesosDestruccion = new double[nD];
        this.puntajesDestruccion = new double[nD];
        this.usosDestruccion = new int[nD];
        Arrays.fill(pesosDestruccion, 1.0 / nD);

        this.pesosReparacion = new double[nR];
        this.puntajesReparacion = new double[nR];
        this.usosReparacion = new int[nR];
        Arrays.fill(pesosReparacion, 1.0 / nR);

        this.iteracionesDesdeUltimaActualizacion = 0;
    }

    /**
     * Selecciona un operador de destrucción por ruleta ponderada.
     * @return Índice del operador seleccionado
     */
    public int seleccionarDestruccion() {
        return seleccionarPorRuleta(pesosDestruccion);
    }

    /**
     * Selecciona un operador de reparación por ruleta ponderada.
     * @return Índice del operador seleccionado
     */
    public int seleccionarReparacion() {
        return seleccionarPorRuleta(pesosReparacion);
    }

    /**
     * Selección por ruleta ponderada.
     */
    private int seleccionarPorRuleta(double[] pesos) {
        double total = 0;
        for (double p : pesos) total += p;

        double r = random.nextDouble() * total;
        double acumulado = 0;

        for (int i = 0; i < pesos.length; i++) {
            acumulado += pesos[i];
            if (r <= acumulado) return i;
        }
        return pesos.length - 1; // Fallback
    }

    /**
     * Registra el puntaje obtenido por los operadores utilizados en esta iteración.
     * 
     * @param indiceDestruccion Índice del operador de destrucción usado
     * @param indiceReparacion  Índice del operador de reparación usado
     * @param sigma             Puntaje obtenido (SIGMA_1, SIGMA_2, SIGMA_3, o 0)
     */
    public void registrarRendimiento(int indiceDestruccion, int indiceReparacion, int sigma) {
        puntajesDestruccion[indiceDestruccion] += sigma;
        usosDestruccion[indiceDestruccion]++;

        puntajesReparacion[indiceReparacion] += sigma;
        usosReparacion[indiceReparacion]++;

        iteracionesDesdeUltimaActualizacion++;

        // Recalcular pesos periódicamente
        if (iteracionesDesdeUltimaActualizacion >= periodoActualizacion) {
            recalcularPesos();
            iteracionesDesdeUltimaActualizacion = 0;
        }
    }

    /**
     * Recalcula los pesos de todos los operadores según su rendimiento acumulado.
     * Fórmula: w_new = (1 - r) * w_old + r * (score / uses)
     */
    private void recalcularPesos() {
        recalcularPesosArray(pesosDestruccion, puntajesDestruccion, usosDestruccion);
        recalcularPesosArray(pesosReparacion, puntajesReparacion, usosReparacion);
    }

    private void recalcularPesosArray(double[] pesos, double[] puntajes, int[] usos) {
        for (int i = 0; i < pesos.length; i++) {
            if (usos[i] > 0) {
                double rendimiento = puntajes[i] / usos[i];
                pesos[i] = (1 - tasaReaccion) * pesos[i] + tasaReaccion * rendimiento;
            }
            // Reset contadores para el siguiente periodo
            puntajes[i] = 0;
            usos[i] = 0;
        }

        // Normalizar pesos para que sumen 1
        double total = 0;
        for (double p : pesos) total += p;
        if (total > 0) {
            for (int i = 0; i < pesos.length; i++) {
                pesos[i] /= total;
            }
        }
    }

    /**
     * Retorna los pesos actuales de los operadores de destrucción.
     */
    public Map<String, Double> getPesosDestruccion() {
        Map<String, Double> mapa = new LinkedHashMap<>();
        for (int i = 0; i < nombresDestruccion.size(); i++) {
            mapa.put(nombresDestruccion.get(i), pesosDestruccion[i]);
        }
        return mapa;
    }

    /**
     * Retorna los pesos actuales de los operadores de reparación.
     */
    public Map<String, Double> getPesosReparacion() {
        Map<String, Double> mapa = new LinkedHashMap<>();
        for (int i = 0; i < nombresReparacion.size(); i++) {
            mapa.put(nombresReparacion.get(i), pesosReparacion[i]);
        }
        return mapa;
    }

    public String getNombreDestruccion(int indice) { return nombresDestruccion.get(indice); }
    public String getNombreReparacion(int indice) { return nombresReparacion.get(indice); }
}
