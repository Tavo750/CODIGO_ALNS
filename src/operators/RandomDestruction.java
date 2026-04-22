package operators;

import model.*;
import java.util.*;

/**
 * Operador de destrucción aleatorio.
 * Selecciona envíos al azar para remover del plan actual.
 * 
 * Estrategia: Diversificación pura — permite explorar regiones del espacio
 * de soluciones que los operadores dirigidos podrían no alcanzar.
 */
public class RandomDestruction implements DestructionOperator {
    private final Random random;

    public RandomDestruction() {
        this.random = new Random();
    }

    public RandomDestruction(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion) {
        PlanDeRutas planCopia = plan.copiar();

        List<Maleta> asignadas = new ArrayList<>(planCopia.getAsignaciones().keySet());
        int cantidadARemover = Math.max(1, (int) (asignadas.size() * porcentajeRemocion));

        // Selección aleatoria
        Collections.shuffle(asignadas, random);

        for (int i = 0; i < cantidadARemover && i < asignadas.size(); i++) {
            planCopia.desasignarMaleta(asignadas.get(i));
        }

        return planCopia;
    }

    @Override
    public String getNombre() {
        return "Random";
    }
}
