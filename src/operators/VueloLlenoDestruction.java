package operators;

import model.*;
import java.util.*;

/**
 * Operador de destrucción que prioriza la remoción de envíos de vuelos
 * que están al límite de su capacidad (saturados).
 * 
 * Estrategia: Identifica los vuelos con mayor ocupación (basada en cantidad)
 * y remueve envíos de esos vuelos, liberando espacio para mejor redistribución.
 */
public class VueloLlenoDestruction implements DestructionOperator {

    @Override
    public PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion) {
        PlanDeRutas planCopia = plan.copiar();

        int totalAsignadas = planCopia.getTotalMaletasAsignadas();
        int cantidadARemover = Math.max(1, (int) (totalAsignadas * porcentajeRemocion));

        // Obtener vuelos ordenados por ocupación descendente (basada en cantidad)
        List<Vuelo> vuelosOrdenados = new ArrayList<>(planCopia.getVuelosMap().values());
        vuelosOrdenados.sort((v1, v2) -> Double.compare(v2.getOcupacion(), v1.getOcupacion()));

        Set<Maleta> maletasARemover = new LinkedHashSet<>();

        // Recorrer vuelos más llenos y seleccionar envíos a remover
        for (Vuelo vuelo : vuelosOrdenados) {
            if (maletasARemover.size() >= cantidadARemover) break;
            if (vuelo.getOcupacion() < 0.5) break; // Solo considerar vuelos con >50% de ocupación

            List<Maleta> maletasDelVuelo = new ArrayList<>(vuelo.getMaletasAsignadas());
            Collections.shuffle(maletasDelVuelo);

            for (Maleta maleta : maletasDelVuelo) {
                if (maletasARemover.size() >= cantidadARemover) break;
                maletasARemover.add(maleta);
            }
        }

        // Si no se alcanzó el objetivo, agregar envíos aleatorios
        if (maletasARemover.size() < cantidadARemover) {
            List<Maleta> todasAsignadas = new ArrayList<>(planCopia.getAsignaciones().keySet());
            Collections.shuffle(todasAsignadas);
            for (Maleta m : todasAsignadas) {
                if (maletasARemover.size() >= cantidadARemover) break;
                maletasARemover.add(m);
            }
        }

        // Ejecutar remoción
        for (Maleta maleta : maletasARemover) {
            planCopia.desasignarMaleta(maleta);
        }

        return planCopia;
    }

    @Override
    public String getNombre() {
        return "Vuelo-Lleno";
    }
}
