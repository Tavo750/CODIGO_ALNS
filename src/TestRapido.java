import model.*;
import util.*;
import java.util.*;
import java.io.*;

/**
 * Test estadístico: 350 envíos, ALNS 250 y 500 iteraciones.
 * 10 réplicas independientes por configuración.
 * Calcula: Media, Desv. Estándar, IC 95%, Shapiro-Wilk (W, p-value).
 */
public class TestRapido {

    static final int NUM_REPLICAS = 10;
    static final int NUM_ENVIOS = 350;
    static final int[] ITERACIONES = {250, 500};

    public static void main(String[] args) throws Exception {
        String dataDir = "data";

        System.out.println("=== TEST ESTADISTICO: 350 Envios | 10 Replicas ===\n");

        // 1. Cargar datos una sola vez
        Map<String, Aeropuerto> aeropuertos = DataParser.parseAeropuertos(dataDir + "/aeropuertos.txt");
        List<FlightTemplate> templates = DataParser.parsePlanesVuelo(dataDir + "/planes_vuelo.txt");
        List<Maleta> todosEnvios = DataParser.parseEnvios(dataDir + "/envios_preliminar", aeropuertos);
        System.out.println("Total envios: " + todosEnvios.size());

        // Agrupar por dia y tomar el dia con mas envios
        Map<Integer, List<Maleta>> porDiaAll = DataParser.agruparEnviosPorDia(todosEnvios);
        Map.Entry<Integer, List<Maleta>> mejorDia = null;
        for (Map.Entry<Integer, List<Maleta>> e : porDiaAll.entrySet())
            if (mejorDia == null || e.getValue().size() > mejorDia.getValue().size()) mejorDia = e;

        int dayIndex = mejorDia.getKey();
        List<Maleta> enviosDia = new ArrayList<>(mejorDia.getValue());
        System.out.printf("Dia seleccionado: %s con %d envios%n", TimeUtils.dayIndexToDate(dayIndex), enviosDia.size());

        // Mezclar para diversidad de origenes y tomar 350
        Collections.shuffle(enviosDia, new Random(42));
        List<Maleta> enviosTest = new ArrayList<>(enviosDia.subList(0, Math.min(NUM_ENVIOS, enviosDia.size())));

        // Instanciar vuelos solo para el dia seleccionado + 2 dias
        List<Vuelo> vuelos = DataParser.instanciarVuelos(templates, dayIndex, dayIndex + 2, aeropuertos);
        FlightIndex flightIndex = new FlightIndex(vuelos);

        System.out.printf("Envios: %d | Vuelos: %d | Replicas: %d%n%n", enviosTest.size(), vuelos.size(), NUM_REPLICAS);

        // 2. Ejecutar replicas
        StringBuilder json = new StringBuilder("{\n");

        // Datos del dataset para graficos
        Map<Integer, List<Maleta>> porDia = DataParser.agruparEnviosPorDia(enviosTest);
        json.append("  \"enviosPorDia\": [");
        boolean first = true;
        for (Map.Entry<Integer, List<Maleta>> e : porDia.entrySet()) {
            if (!first) json.append(",");
            json.append("{\"fecha\":\"").append(TimeUtils.dayIndexToDate(e.getKey()))
                .append("\",\"cantidad\":").append(e.getValue().size()).append("}");
            first = false;
        }
        json.append("],\n");

        // Cantidades
        json.append("  \"cantidades\": [");
        first = true;
        for (Maleta m : enviosTest) {
            if (!first) json.append(",");
            json.append(m.getCantidad());
            first = false;
        }
        json.append("],\n");

        // Prioridades
        int[] pc = new int[4];
        for (Maleta m : enviosTest) pc[m.getPrioridad()]++;
        json.append("  \"prioridades\":{\"alta\":").append(pc[1])
            .append(",\"media\":").append(pc[2]).append(",\"baja\":").append(pc[3]).append("},\n");

        // Destinos
        Map<String, Integer> dc = new TreeMap<>();
        for (Maleta m : enviosTest) dc.merge(m.getAeropuertoDestino(), 1, Integer::sum);
        json.append("  \"destinos\":{");
        first = true;
        for (Map.Entry<String, Integer> e : dc.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        json.append("},\n");

        json.append("  \"resultados\": [\n");

        for (int c = 0; c < ITERACIONES.length; c++) {
            int numIter = ITERACIONES[c];
            double[] fitnessArr = new double[NUM_REPLICAS];
            double[] tiempoArr = new double[NUM_REPLICAS];
            int[] asignadosArr = new int[NUM_REPLICAS];
            int[] totalArr = new int[NUM_REPLICAS];
            List<List<Long>> tiemposTransporteAll = new ArrayList<>();

            System.out.printf("=== ALNS %d iteraciones ===%n", numIter);

            for (int r = 0; r < NUM_REPLICAS; r++) {
                PlanDeRutas plan = SolutionGenerator.generarPlanInicial(enviosTest, flightIndex, aeropuertos);
                alns.ALNSEngine engine = new alns.ALNSEngine(
                    numIter, 0.10, 0.40, 100.0, 0.995, 0.3, 100, flightIndex);

                long t0 = System.currentTimeMillis();
                PlanDeRutas mejor = engine.ejecutar(plan);
                long t1 = System.currentTimeMillis();

                fitnessArr[r] = CostCalculator.calcularCosto(mejor);
                tiempoArr[r] = (t1 - t0) / 1000.0;
                asignadosArr[r] = mejor.getTotalMaletasAsignadas();
                totalArr[r] = mejor.getTotalMaletas();

                List<Long> tt = new ArrayList<>();
                for (Map.Entry<Maleta, Ruta> entry : mejor.getAsignaciones().entrySet())
                    tt.add(entry.getValue().getTiempoTotal());
                Collections.sort(tt);
                tiemposTransporteAll.add(tt);

                System.out.printf("  Replica %2d: Fitness=%.2f  Tiempo=%.3fs  Asig=%d/%d%n",
                    r+1, fitnessArr[r], tiempoArr[r], asignadosArr[r], totalArr[r]);
            }

            // Estadisticas
            double mean = mean(fitnessArr);
            double stdDev = stdDev(fitnessArr, mean);
            double[] ci95 = confidenceInterval95(fitnessArr, mean, stdDev);
            double[] sw = shapiroWilk(fitnessArr);

            double meanTime = mean(tiempoArr);
            double meanAsig = 0;
            for (int a : asignadosArr) meanAsig += a;
            meanAsig /= NUM_REPLICAS;
            double meanPct = 0;
            for (int r = 0; r < NUM_REPLICAS; r++)
                meanPct += totalArr[r] > 0 ? asignadosArr[r]*100.0/totalArr[r] : 0;
            meanPct /= NUM_REPLICAS;

            System.out.printf("%n  --- Estadisticas %d iter ---%n", numIter);
            System.out.printf("  Media Fitness:    %.4f%n", mean);
            System.out.printf("  Desv Estandar:    %.4f%n", stdDev);
            System.out.printf("  IC 95%%:           [%.4f, %.4f]%n", ci95[0], ci95[1]);
            System.out.printf("  Shapiro-Wilk W:   %.6f%n", sw[0]);
            System.out.printf("  Shapiro-Wilk p:   %.6f%n", sw[1]);
            System.out.printf("  Normal (p>0.05):  %s%n%n", sw[1] > 0.05 ? "SI" : "NO");

            // JSON
            json.append("    {\n");
            json.append("      \"iteraciones\":").append(numIter).append(",\n");
            json.append("      \"replicas\":").append(NUM_REPLICAS).append(",\n");
            json.append("      \"fitnessValues\":[");
            for (int r = 0; r < NUM_REPLICAS; r++) {
                if (r > 0) json.append(",");
                json.append(String.format("%.4f", fitnessArr[r]));
            }
            json.append("],\n");
            json.append("      \"tiempoValues\":[");
            for (int r = 0; r < NUM_REPLICAS; r++) {
                if (r > 0) json.append(",");
                json.append(String.format("%.3f", tiempoArr[r]));
            }
            json.append("],\n");
            json.append("      \"asignadosValues\":[");
            for (int r = 0; r < NUM_REPLICAS; r++) {
                if (r > 0) json.append(",");
                json.append(asignadosArr[r]);
            }
            json.append("],\n");
            json.append("      \"totalValues\":[");
            for (int r = 0; r < NUM_REPLICAS; r++) {
                if (r > 0) json.append(",");
                json.append(totalArr[r]);
            }
            json.append("],\n");
            json.append(String.format("      \"mean\":%.4f,\n", mean));
            json.append(String.format("      \"stdDev\":%.4f,\n", stdDev));
            json.append(String.format("      \"ci95Lower\":%.4f,\n", ci95[0]));
            json.append(String.format("      \"ci95Upper\":%.4f,\n", ci95[1]));
            json.append(String.format("      \"shapiroW\":%.6f,\n", sw[0]));
            json.append(String.format("      \"shapiroP\":%.6f,\n", sw[1]));
            json.append(String.format("      \"meanTime\":%.3f,\n", meanTime));
            json.append(String.format("      \"meanAsig\":%.1f,\n", meanAsig));
            json.append(String.format("      \"meanPct\":%.2f,\n", meanPct));

            // Tiempos transporte de la ultima replica para boxplot
            List<Long> lastTT = tiemposTransporteAll.get(NUM_REPLICAS - 1);
            json.append("      \"tiemposTransporte\":[");
            for (int j = 0; j < lastTT.size(); j++) {
                if (j > 0) json.append(",");
                json.append(lastTT.get(j));
            }
            json.append("]\n");
            json.append("    }").append(c < ITERACIONES.length - 1 ? "," : "").append("\n");
        }
        json.append("  ]\n}\n");

        try (PrintWriter pw = new PrintWriter(new FileWriter("data/test_results.json"))) {
            pw.print(json.toString());
        }
        System.out.println(">>> Datos exportados a data/test_results.json");
        System.out.println("=== TEST COMPLETADO ===");
    }

    // ===================== ESTADISTICAS =====================

    static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    static double stdDev(double[] x, double mean) {
        double ss = 0;
        for (double v : x) ss += (v - mean) * (v - mean);
        return Math.sqrt(ss / (x.length - 1));
    }

    /** IC 95% usando t de Student (t_{0.025, n-1}) */
    static double[] confidenceInterval95(double[] x, double mean, double stdDev) {
        // Valores t para alpha/2=0.025 con df=n-1
        double[] tTable = {0, 0, 0, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042};
        int df = x.length - 1;
        double t = df < tTable.length ? tTable[df] : 1.96;
        double margin = t * stdDev / Math.sqrt(x.length);
        return new double[]{mean - margin, mean + margin};
    }

    // ===================== SHAPIRO-WILK =====================

    /** Shapiro-Wilk test. Returns [W, p-value]. */
    static double[] shapiroWilk(double[] x) {
        int n = x.length;
        if (n < 3) return new double[]{1.0, 1.0};

        double[] sorted = x.clone();
        Arrays.sort(sorted);

        double mean = mean(sorted);

        // SS total
        double ss = 0;
        for (double v : sorted) ss += (v - mean) * (v - mean);
        if (ss < 1e-30) return new double[]{1.0, 1.0}; // Todos iguales

        // Compute expected normal order statistics m_i
        double[] m = new double[n];
        for (int i = 0; i < n; i++) {
            m[i] = qnorm((i + 1 - 0.375) / (n + 0.25));
        }

        // Compute coefficients a_i = m_i / ||m||
        double mNorm = 0;
        for (double v : m) mNorm += v * v;
        mNorm = Math.sqrt(mNorm);

        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = m[i] / mNorm;

        // Apply Royston correction for a[n-1] and a[n-2] if n > 5
        if (n > 5) {
            double u = 1.0 / Math.sqrt(n);
            // Polynomial correction for a[n-1] (Royston 1992)
            double an1 = a[n - 1]; // keep approx
            // Correction coefficients for n > 5
            double corr_an = -2.706056 + 4.434685 * u - 2.07119 * u * u
                            - 0.147981 * u * u * u + 0.221157 * u * u * u * u;
            // Apply bounded correction
            if (Math.abs(corr_an) < Math.abs(an1) * 2) {
                a[n - 1] = corr_an;
                a[0] = -corr_an;
            }
        }

        // Renormalize a so that sum(a^2) = 1
        double aNorm = 0;
        for (double v : a) aNorm += v * v;
        aNorm = Math.sqrt(aNorm);
        for (int i = 0; i < n; i++) a[i] /= aNorm;

        // Compute W
        double b = 0;
        for (int i = 0; i < n; i++) b += a[i] * sorted[i];
        double W = (b * b) / ss;

        // P-value using Royston (1992) normalizing transformation
        double pValue = shapiroWilkPValue(W, n);

        return new double[]{W, pValue};
    }

    /** Approximate p-value for Shapiro-Wilk W statistic using Royston (1992). */
    static double shapiroWilkPValue(double W, int n) {
        if (W >= 1.0) return 1.0;
        if (W <= 0.0) return 0.0;

        double y, mu, sigma, z;

        if (n <= 11) {
            // Small sample: use pre-tabulated mu and sigma for -ln(1-W) transform
            // From Royston (1992) Table 1
            double[] muTab  = {0, 0, 0, -1.909, -1.449, -1.087, -0.794, -0.548, -0.339, -0.159, 0.000, 0.143};
            double[] sigTab = {0, 0, 0,  1.135,  1.062,  0.976,  0.905,  0.845,  0.795,  0.752, 0.715, 0.683};
            y = -Math.log(1 - W);
            mu = muTab[n];
            sigma = sigTab[n];
            z = (y - mu) / sigma;
        } else {
            // Larger sample: Royston (1992) log-normal approximation
            double ln_n = Math.log(n);
            mu = -1.2725 + 1.0521 * ln_n;
            sigma = Math.exp(-1.4434 - 0.26758 * ln_n);
            y = Math.log(1.0 - W);
            z = (y - mu) / sigma;
        }

        // p = 1 - Phi(z)
        return 1.0 - pnorm(z);
    }

    // ===================== NORMAL DISTRIBUTION =====================

    /** Inverse normal CDF (probit function) using Beasley-Springer-Moro algorithm. */
    static double qnorm(double p) {
        if (p <= 0) return -8;
        if (p >= 1) return 8;
        if (Math.abs(p - 0.5) < 1e-15) return 0;

        double t;
        if (p < 0.5) {
            t = Math.sqrt(-2.0 * Math.log(p));
            return -(2.515517 + t * (0.802853 + t * 0.010328))
                    / (1.0 + t * (1.432788 + t * (0.189269 + t * 0.001308)));
        } else {
            t = Math.sqrt(-2.0 * Math.log(1.0 - p));
            return (2.515517 + t * (0.802853 + t * 0.010328))
                    / (1.0 + t * (1.432788 + t * (0.189269 + t * 0.001308)));
        }
    }

    /** Standard normal CDF using Abramowitz & Stegun approximation. */
    static double pnorm(double x) {
        if (x < -8) return 0;
        if (x > 8) return 1;

        double sign = 1;
        if (x < 0) { sign = -1; x = -x; }

        double t = 1.0 / (1.0 + 0.2316419 * x);
        double poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937
                    + t * (-1.821255978 + t * 1.330274429))));
        double pdf = Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
        double cdf = 1.0 - pdf * poly;

        return sign < 0 ? 1.0 - cdf : cdf;
    }
}
