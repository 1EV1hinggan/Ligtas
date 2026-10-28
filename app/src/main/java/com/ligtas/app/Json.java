package com.ligtas.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tolerant readers for data that came from Firestore (numbers may be Long or Double, lists may be missing). */
final class Json {
    private Json() { }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    static long longOf(Object o, long fallback) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(str(o)); } catch (NumberFormatException e) { return fallback; }
    }

    static int intOf(Object o, int fallback) {
        return (int) longOf(o, fallback);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> listOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<?>) o) if (item instanceof Map) out.add((Map<String, Object>) item);
        }
        return out;
    }
}
