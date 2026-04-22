package util;

import model.*;
import model.Aeropuerto.Continente;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parser principal que lee los tres archivos de datos reales:
 * - aeropuertos.txt: información de aeropuertos
 * - planes_vuelo.txt: plantillas de vuelos recurrentes
 * - envios_preliminar/*.txt: envíos con maletas a transportar
 */
public class DataParser {

    /**
     * Parsea el archivo de aeropuertos.
     * Detecta el continente por las líneas de encabezado de sección.
     *
     * Formato: NN   ICAO   Ciudad   País   alias   GMT   CAPACIDAD   ...
     * Ejemplo: 01   SKBO   Bogota   Colombia   bogo   -5   430   ...
     *
     * @param path Ruta al archivo aeropuertos.txt
     * @return Mapa de código ICAO → Aeropuerto
     */
    public static Map<String, Aeropuerto> parseAeropuertos(String path) throws IOException {
        Map<String, Aeropuerto> aeropuertos = new LinkedHashMap<>();

        // Auto-detectar encoding (el archivo puede ser UTF-16 BE)
        Charset charset = detectarEncoding(path);
        System.out.println("  Encoding detectado: " + charset.displayName());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), charset))) {

            String linea;
            Continente continenteActual = null;

            while ((linea = reader.readLine()) != null) {
                String lineaTrim = linea.trim();

                // Detectar secciones de continente
                if (lineaTrim.toLowerCase().contains("america del sur")) {
                    continenteActual = Continente.AMERICA_DEL_SUR;
                    continue;
                } else if (lineaTrim.toLowerCase().contains("europa")) {
                    continenteActual = Continente.EUROPA;
                    continue;
                } else if (lineaTrim.toLowerCase().contains("asia")) {
                    continenteActual = Continente.ASIA;
                    continue;
                }

                // Ignorar líneas de encabezado, vacías, y decorativas
                if (lineaTrim.isEmpty() || lineaTrim.startsWith("*") || lineaTrim.startsWith("PDDS") ||
                    lineaTrim.startsWith("GMT") || lineaTrim.contains("CAPACIDAD")) {
                    continue;
                }

                // Intentar parsear línea de aeropuerto
                if (continenteActual != null && Character.isDigit(lineaTrim.charAt(0))) {
                    try {
                        Aeropuerto aero = parsearLineaAeropuerto(lineaTrim, continenteActual);
                        if (aero != null) {
                            aeropuertos.put(aero.getCodigoICAO(), aero);
                        }
                    } catch (Exception e) {
                        // Ignorar líneas mal formateadas
                        System.err.println("WARN: No se pudo parsear aeropuerto: " + lineaTrim);
                    }
                }
            }
        }

        return aeropuertos;
    }

    /**
     * Parsea una línea individual de aeropuerto.
     * Formato variable con espacios: NN   ICAO   Ciudad   País   alias   GMT   CAPACIDAD   ...
     */
    private static Aeropuerto parsearLineaAeropuerto(String linea, Continente continente) {
        // Dividir por espacios múltiples, luego reagrupar
        String[] tokens = linea.trim().split("\\s+");
        if (tokens.length < 7) return null;

        int numero = Integer.parseInt(tokens[0]);
        String codigoICAO = tokens[1];

        // Ciudad puede ser dos palabras (e.g., "Santiago de Chile", "Buenos Aires", "La Paz")
        // País está después de la ciudad
        // Buscar el GMT offset (empieza con + o -, es un número)
        int gmtIndex = -1;
        for (int i = 3; i < tokens.length; i++) {
            if (tokens[i].matches("[+-]?\\d+") && !tokens[i].matches("\\d{3,}")) {
                // Podría ser GMT offset o capacidad
                // GMT offset generalmente es -5 a +5, capacidad es 400-480
                int val = Integer.parseInt(tokens[i]);
                if (val >= -12 && val <= 14 && (tokens[i].startsWith("+") || tokens[i].startsWith("-") || Math.abs(val) <= 5)) {
                    gmtIndex = i;
                    break;
                }
            }
        }

        if (gmtIndex < 0 || gmtIndex + 1 >= tokens.length) return null;

        // El alias está justo antes del GMT
        String alias = tokens[gmtIndex - 1];

        // Ciudad es desde token[2] hasta antes del país (difícil de determinar exactamente)
        // País es desde después de la ciudad hasta antes del alias
        // Simplificar: ciudad = token[2], país podría ser tokens entre [3] y gmtIndex-2
        StringBuilder ciudad = new StringBuilder(tokens[2]);
        // El país es normalmente un token antes del alias
        // alias está en gmtIndex-1, país en gmtIndex-2 (o antes)
        String pais = "";
        if (gmtIndex - 2 > 2) {
            // Hay tokens entre ciudad y alias
            StringBuilder paisBuilder = new StringBuilder();
            for (int i = 3; i < gmtIndex - 1; i++) {
                if (i > 3) paisBuilder.append(" ");
                paisBuilder.append(tokens[i]);
            }
            pais = paisBuilder.toString();
        } else if (gmtIndex - 2 == 2) {
            pais = tokens[2];
            ciudad = new StringBuilder(tokens[2]);
        }

        // Re-parsear más robustamente: usar posiciones fijas conocidas del formato
        // NN ICAO Ciudad País alias GMT CAPACIDAD
        // token[0]=numero, [1]=ICAO, [2..N-4]=ciudad+pais, [N-3]=alias, [N-2]=GMT, [N-1]=CAPACIDAD
        // Pero hay más tokens después (Latitude, etc.)

        int gmtOffset = Integer.parseInt(tokens[gmtIndex]);
        int capacidad = Integer.parseInt(tokens[gmtIndex + 1]);

        // Reconstruir ciudad y país de forma más robusta
        // Sabemos: tokens[0]=num, [1]=ICAO, luego ciudad, país, alias, GMT, cap, Lat...
        // alias = tokens[gmtIndex-1]
        // El país está entre la ciudad y el alias
        // Patrón típico: "Bogota Colombia bogo -5 430" o "Santiago de Chile Chile sant -3 460"
        int ciudadStart = 2;
        int paisEnd = gmtIndex - 2; // último token del país

        // Buscar dónde termina la ciudad y empieza el país
        // Heurística: el país suele ser un token con primera letra mayúscula
        // justo antes del alias (que es todo minúsculas)
        ciudad = new StringBuilder();
        StringBuilder paisBuilder = new StringBuilder();

        // Agrupar todo entre [2] y [gmtIndex-2] como ciudad+pais
        List<String> middleTokens = new ArrayList<>();
        for (int i = ciudadStart; i <= paisEnd; i++) {
            middleTokens.add(tokens[i]);
        }

        if (middleTokens.size() == 1) {
            ciudad = new StringBuilder(middleTokens.get(0));
            pais = middleTokens.get(0); // mismo token para ambos
        } else if (middleTokens.size() == 2) {
            ciudad = new StringBuilder(middleTokens.get(0));
            pais = middleTokens.get(1);
        } else if (middleTokens.size() >= 3) {
            // Casos como "Santiago de Chile Chile" o "Buenos Aires Argentina"
            // El último token suele ser el país
            pais = middleTokens.get(middleTokens.size() - 1);
            ciudad = new StringBuilder();
            for (int i = 0; i < middleTokens.size() - 1; i++) {
                if (i > 0) ciudad.append(" ");
                ciudad.append(middleTokens.get(i));
            }
        }

        return new Aeropuerto(numero, codigoICAO, ciudad.toString(), pais,
                              alias, continente, gmtOffset, capacidad);
    }

    /**
     * Parsea el archivo de planes de vuelo.
     * Formato: ORIG-DEST-HH:MM-HH:MM-CCCC
     * Ejemplo: SKBO-SEQM-03:34-04:21-0300
     * Split por "-" da: [SKBO, SEQM, 03:34, 04:21, 0300] → 5 partes
     *
     * @param path Ruta al archivo planes_vuelo.txt
     * @return Lista de plantillas de vuelo
     */
    public static List<FlightTemplate> parsePlanesVuelo(String path) throws IOException {
        List<FlightTemplate> templates = new ArrayList<>();
        int index = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {

            String linea;
            while ((linea = reader.readLine()) != null) {
                String lineaTrim = linea.trim();

                // Ignorar líneas vacías y comentarios
                if (lineaTrim.isEmpty() || lineaTrim.startsWith("//") || lineaTrim.startsWith("#")) {
                    continue;
                }

                // Formato: ORIG-DEST-HH:MM-HH:MM-CCCC
                // Split por "-" da: [ORIG, DEST, HH:MM, HH:MM, CCCC]
                String[] partes = lineaTrim.split("-");
                if (partes.length < 5) continue;

                try {
                    String origen = partes[0].trim();
                    String destino = partes[1].trim();

                    // Hora de salida (partes[2] tiene formato HH:MM)
                    int horaSalida = TimeUtils.parseTimeToMinutes(partes[2].trim());

                    // Hora de llegada (partes[3] tiene formato HH:MM)
                    int horaLlegada = TimeUtils.parseTimeToMinutes(partes[3].trim());

                    // Capacidad (partes[4] tiene 4 dígitos, e.g., "0300")
                    int capacidad = Integer.parseInt(partes[4].trim());

                    templates.add(new FlightTemplate(index++, origen, destino,
                                                     horaSalida, horaLlegada, capacidad));
                } catch (Exception e) {
                    System.err.println("WARN: No se pudo parsear vuelo: " + lineaTrim + " - " + e.getMessage());
                }
            }
        }

        return templates;
    }

    /**
     * Parsea todos los archivos de envíos en el directorio envios_preliminar.
     * Cada archivo _envios_XXXX_.txt contiene envíos con origen XXXX.
     *
     * Formato: id_envío-aaaammdd-hh-mm-dest-###-IdClien
     * Ejemplo: 000000001-20260102-00-55-SPIM-002-0019169
     *
     * @param dirPath     Ruta al directorio envios_preliminar
     * @param aeropuertos Mapa de aeropuertos para obtener GMT y calcular SLA
     * @return Lista de todos los envíos parseados
     */
    public static List<Maleta> parseEnvios(String dirPath, Map<String, Aeropuerto> aeropuertos) throws IOException {
        List<Maleta> todosEnvios = new ArrayList<>();

        File dir = new File(dirPath);
        if (!dir.isDirectory()) {
            throw new IOException("No es un directorio: " + dirPath);
        }

        File[] archivos = dir.listFiles((d, name) -> name.startsWith("_envios_") && name.endsWith("_.txt"));
        if (archivos == null || archivos.length == 0) {
            throw new IOException("No se encontraron archivos de envíos en: " + dirPath);
        }

        // Ordenar para procesamiento consistente
        Arrays.sort(archivos);

        for (File archivo : archivos) {
            // Extraer código ICAO del nombre del archivo: _envios_XXXX_.txt
            String nombre = archivo.getName();
            String codigoOrigen = nombre.substring(8, nombre.length() - 5); // _envios_XXXX_.txt → XXXX

            Aeropuerto aeroOrigen = aeropuertos.get(codigoOrigen);
            if (aeroOrigen == null) {
                System.err.println("WARN: Aeropuerto no encontrado para: " + codigoOrigen);
                continue;
            }

            System.out.printf("  Cargando envíos de %s (%s)...%n", codigoOrigen, archivo.getName());

            List<Maleta> enviosArchivo = parsearArchivoEnvios(archivo, codigoOrigen, aeroOrigen, aeropuertos);
            todosEnvios.addAll(enviosArchivo);

            System.out.printf("    → %,d envíos cargados%n", enviosArchivo.size());
        }

        return todosEnvios;
    }

    /**
     * Parsea un archivo individual de envíos.
     */
    private static List<Maleta> parsearArchivoEnvios(File archivo, String codigoOrigen,
                                                      Aeropuerto aeroOrigen,
                                                      Map<String, Aeropuerto> aeropuertos) throws IOException {
        List<Maleta> envios = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {

            String linea;
            boolean primeraLinea = true;
            while ((linea = reader.readLine()) != null) {
                String lineaTrim = linea.trim();
                if (lineaTrim.isEmpty()) continue;

                // Limpiar posible BOM en la primera línea
                if (primeraLinea) {
                    if (lineaTrim.charAt(0) == '\uFEFF') {
                        lineaTrim = lineaTrim.substring(1);
                    }
                    primeraLinea = false;
                }

                try {
                    // Formato: id_envío-aaaammdd-hh-mm-dest-###-IdClien
                    // Ejemplo: 000000001-20260102-00-55-SPIM-002-0019169
                    String[] partes = lineaTrim.split("-");
                    if (partes.length < 7) continue;

                    String id = partes[0];
                    String fecha = partes[1]; // aaaammdd
                    int hora = Integer.parseInt(partes[2]);
                    int minuto = Integer.parseInt(partes[3]);
                    String destino = partes[4];
                    int cantidad = Integer.parseInt(partes[5]);
                    String idCliente = partes[6];

                    // Calcular fecha de creación en UTC
                    int year = Integer.parseInt(fecha.substring(0, 4));
                    int month = Integer.parseInt(fecha.substring(4, 6));
                    int day = Integer.parseInt(fecha.substring(6, 8));
                    long fechaCreacionUTC = TimeUtils.toAbsoluteUTC(year, month, day,
                                                                     hora, minuto, aeroOrigen.getGmtOffset());

                    // Calcular SLA deadline
                    Aeropuerto aeroDestino = aeropuertos.get(destino);
                    int slaDuracion;
                    if (aeroDestino != null) {
                        slaDuracion = aeroOrigen.calcularSLA(aeroDestino);
                    } else {
                        slaDuracion = 2880; // Default: 48h si no se encuentra el destino
                    }
                    int slaDeadline = (int) (fechaCreacionUTC + slaDuracion);

                    // Derivar prioridad de la cantidad
                    int prioridad;
                    if (cantidad >= 5) {
                        prioridad = 1; // Alta
                    } else if (cantidad >= 3) {
                        prioridad = 2; // Media
                    } else {
                        prioridad = 3; // Baja
                    }

                    // Usar ID compuesto para unicidad: ORIGEN-ID
                    String idCompuesto = codigoOrigen + "-" + id;

                    envios.add(new Maleta(idCompuesto, codigoOrigen, destino,
                                          fechaCreacionUTC, slaDeadline, prioridad,
                                          cantidad, idCliente));

                } catch (Exception e) {
                    // Ignorar líneas mal formateadas silenciosamente
                }
            }
        }

        return envios;
    }

    /**
     * Instancia vuelos para un rango de días a partir de las plantillas.
     *
     * @param templates   Plantillas de vuelo
     * @param dayStart    Día inicio (inclusive)
     * @param dayEnd      Día fin (inclusive)
     * @param aeropuertos Mapa de aeropuertos para obtener GMT offsets
     * @return Lista de vuelos instanciados con tiempos UTC absolutos
     */
    public static List<Vuelo> instanciarVuelos(List<FlightTemplate> templates, int dayStart, int dayEnd,
                                                Map<String, Aeropuerto> aeropuertos) {
        List<Vuelo> vuelos = new ArrayList<>();

        for (int day = dayStart; day <= dayEnd; day++) {
            for (FlightTemplate template : templates) {
                Aeropuerto aeroOrigen = aeropuertos.get(template.getOrigen());
                Aeropuerto aeroDestino = aeropuertos.get(template.getDestino());

                if (aeroOrigen == null || aeroDestino == null) continue;

                Vuelo vuelo = template.instanciar(day, aeroOrigen.getGmtOffset(), aeroDestino.getGmtOffset());
                vuelos.add(vuelo);
            }
        }

        return vuelos;
    }

    /**
     * Agrupa los envíos por día (dayIndex basado en su fecha de creación UTC).
     *
     * @param envios Lista de todos los envíos
     * @return Mapa de dayIndex → lista de envíos de ese día
     */
    public static Map<Integer, List<Maleta>> agruparEnviosPorDia(List<Maleta> envios) {
        Map<Integer, List<Maleta>> porDia = new TreeMap<>();

        for (Maleta envio : envios) {
            int dayIndex = (int) (envio.getFechaCreacionUTC() / 1440);
            porDia.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(envio);
        }

        return porDia;
    }

    /**
     * Detecta el encoding de un archivo leyendo los primeros bytes (BOM).
     * Soporta: UTF-16 (FE FF o FF FE), UTF-8 BOM (EF BB BF), UTF-8 default.
     */
    private static Charset detectarEncoding(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path)) {
            byte[] bom = new byte[3];
            int read = fis.read(bom);

            if (read >= 2) {
                // UTF-16 Big Endian: FE FF or Little Endian: FF FE
                // Use UTF_16 which auto-detects BOM and handles byte order
                if (((bom[0] & 0xFF) == 0xFE && (bom[1] & 0xFF) == 0xFF) ||
                    ((bom[0] & 0xFF) == 0xFF && (bom[1] & 0xFF) == 0xFE)) {
                    return StandardCharsets.UTF_16;
                }
            }
            if (read >= 3) {
                // UTF-8 BOM: EF BB BF
                if ((bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        // Default
        return StandardCharsets.UTF_8;
    }
}
