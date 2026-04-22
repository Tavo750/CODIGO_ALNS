package operators;

import model.PlanDeRutas;
import util.FlightIndex;

/**
 * Interfaz para operadores de reparación del ALNS.
 * Un operador de reparación reinserta los envíos no asignados al plan,
 * respetando las restricciones de capacidad (basada en cantidad de maletas).
 */
public interface RepairOperator {

    /**
     * Repara el plan parcial reinsertando los envíos no asignados.
     *
     * @param planParcial  El plan con envíos no asignados
     * @param flightIndex  Índice de vuelos para búsqueda rápida
     * @return El plan reparado con el máximo de envíos reinsertados
     */
    PlanDeRutas repair(PlanDeRutas planParcial, FlightIndex flightIndex);

    /**
     * Retorna el nombre descriptivo del operador.
     */
    String getNombre();
}
