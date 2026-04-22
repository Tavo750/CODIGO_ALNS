package util;

/**
 * Utilidades para conversión y manejo de tiempos en el sistema logístico.
 * 
 * Epoch: 2026-01-01 00:00:00 UTC = minuto 0
 * Todos los tiempos se normalizan a minutos absolutos UTC desde este epoch.
 */
public class TimeUtils {

    // Año base del epoch
    private static final int EPOCH_YEAR = 2026;
    private static final int EPOCH_MONTH = 1;
    private static final int EPOCH_DAY = 1;

    // Días en cada mes (no bisiesto y bisiesto)
    private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    /**
     * Convierte una fecha y hora local a minutos absolutos UTC.
     *
     * @param year      Año (e.g., 2026)
     * @param month     Mes (1-12)
     * @param day       Día (1-31)
     * @param hour      Hora local (0-23)
     * @param minute    Minuto (0-59)
     * @param gmtOffset GMT offset en horas (e.g., -5 para SKBO)
     * @return Minutos absolutos UTC desde epoch (2026-01-01 00:00 UTC)
     */
    public static long toAbsoluteUTC(int year, int month, int day, int hour, int minute, int gmtOffset) {
        int dayIndex = daysBetweenEpoch(year, month, day);
        // Minutos locales del día
        long localMinutes = (long) dayIndex * 1440 + hour * 60 + minute;
        // Convertir a UTC: UTC = local - gmtOffset
        // Si gmtOffset = -5, UTC = local + 5 (restamos: local - (-5*60) = local + 300)
        return localMinutes - (long) gmtOffset * 60;
    }

    /**
     * Calcula el índice de día (0-based) desde el epoch (2026-01-01).
     *
     * @param year  Año
     * @param month Mes (1-12)
     * @param day   Día (1-31)
     * @return Número de días desde 2026-01-01 (day 0)
     */
    public static int daysBetweenEpoch(int year, int month, int day) {
        int totalDays = 0;

        // Sumar años completos desde EPOCH_YEAR hasta year-1
        for (int y = EPOCH_YEAR; y < year; y++) {
            totalDays += isLeapYear(y) ? 366 : 365;
        }

        // Sumar meses completos del año actual
        for (int m = 1; m < month; m++) {
            totalDays += daysInMonth(year, m);
        }

        // Sumar días del mes actual (restar 1 porque day 1 = offset 0 del mes)
        totalDays += (day - 1);

        return totalDays;
    }

    /**
     * Convierte un dayIndex a formato de fecha legible (YYYY-MM-DD).
     */
    public static String dayIndexToDate(int dayIndex) {
        int year = EPOCH_YEAR;
        int remaining = dayIndex;

        while (true) {
            int daysInYear = isLeapYear(year) ? 366 : 365;
            if (remaining < daysInYear) break;
            remaining -= daysInYear;
            year++;
        }

        int month = 1;
        while (true) {
            int dim = daysInMonth(year, month);
            if (remaining < dim) break;
            remaining -= dim;
            month++;
        }

        int day = remaining + 1;
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    /**
     * Convierte minutos UTC absolutos a una representación legible.
     */
    public static String minutosUTCToString(long minutosUTC) {
        if (minutosUTC < 0) return "N/A";
        int dayIndex = (int) (minutosUTC / 1440);
        int minuteOfDay = (int) (minutosUTC % 1440);
        int hour = minuteOfDay / 60;
        int minute = minuteOfDay % 60;
        return dayIndexToDate(dayIndex) + " " + String.format("%02d:%02d", hour, minute) + " UTC";
    }

    /**
     * Parsea una fecha en formato aaaammdd y hora hh mm a un índice de día y minutos locales.
     *
     * @param yyyymmdd Fecha como cadena "20260102"
     * @return Array [dayIndex, 0] (solo el índice de día)
     */
    public static int parseDateToDayIndex(String yyyymmdd) {
        int year = Integer.parseInt(yyyymmdd.substring(0, 4));
        int month = Integer.parseInt(yyyymmdd.substring(4, 6));
        int day = Integer.parseInt(yyyymmdd.substring(6, 8));
        return daysBetweenEpoch(year, month, day);
    }

    /**
     * Parsea hora:minuto (formato "HH:MM") a minutos desde medianoche.
     */
    public static int parseTimeToMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return hour * 60 + minute;
    }

    /**
     * Retorna los días del mes dado el año y el mes.
     */
    private static int daysInMonth(int year, int month) {
        if (month == 2 && isLeapYear(year)) return 29;
        return DAYS_IN_MONTH[month];
    }

    /**
     * Verifica si un año es bisiesto.
     */
    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}
