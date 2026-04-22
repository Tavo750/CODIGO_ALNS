package operators;

import model.PlanDeRutas;

/**
 * Interfaz para operadores de destrucción del ALNS.
 * Un operador de destrucción remueve un porcentaje de envíos del plan actual,
 * creando un plan parcial que luego será reparado.
 */
public interface DestructionOperator {

    /**
     * Aplica la destrucción al plan de rutas, removiendo un porcentaje de envíos.
     *
     * @param plan               El plan de rutas actual (se trabaja sobre una copia)
     * @param porcentajeRemocion Porcentaje de envíos a remover (0.10 a 0.40)
     * @return Un nuevo plan con los envíos removidos (pasan a no asignados)
     */
    PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion);

    /**
     * Retorna el nombre descriptivo del operador.
     */
    String getNombre();
}
