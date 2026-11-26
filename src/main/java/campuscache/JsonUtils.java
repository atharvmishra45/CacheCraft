package campuscache;

import java.util.*;

/**
 * Minimal zero-dependency JSON helper for serialization and basic request parsing.
 * Keeps project 100% standard library (Java SE com.sun.net.httpserver).
 */
public class JsonUtils {

    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String opResultToJson(CampusCache.OpResult res) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"operation\":\"").append(escape(res.operation)).append("\",");
        sb.append("\"key\":").append(res.key == null ? "null" : "\"" + escape(res.key) + "\"").append(",");
        sb.append("\"value\":").append(res.value == null ? "null" : "\"" + escape(res.value) + "\"").append(",");
        sb.append("\"hit\":").append(res.hit).append(",");
        sb.append("\"oldFrequency\":").append(res.oldFrequency).append(",");
        sb.append("\"newFrequency\":").append(res.newFrequency).append(",");
        sb.append("\"evictedKey\":").append(res.evictedKey == null ? "null" : "\"" + escape(res.evictedKey) + "\"").append(",");
        sb.append("\"evictedValue\":").append(res.evictedValue == null ? "null" : "\"" + escape(res.evictedValue) + "\"").append(",");
        sb.append("\"evictedFrequency\":").append(res.evictedFrequency).append(",");
        sb.append("\"reason\":\"").append(escape(res.reason)).append("\",");
        sb.append("\"message\":\"").append(escape(res.message)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String cacheStateToJson(CampusCache.CacheState state) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"capacity\":").append(state.capacity).append(",");
        sb.append("\"currentSize\":").append(state.currentSize).append(",");
        sb.append("\"minFrequency\":").append(state.minFrequency).append(",");
        sb.append("\"nextEvictionKey\":").append(state.nextEvictionKey == null ? "null" : "\"" + escape(state.nextEvictionKey) + "\"").append(",");

        // Stats
        sb.append("\"stats\":{");
        int count = 0;
        for (Map.Entry<String, Object> entry : state.stats.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        sb.append("},");

        // Groups
        sb.append("\"frequencyGroups\":[");
        for (int i = 0; i < state.frequencyGroups.size(); i++) {
            if (i > 0) sb.append(",");
            CampusCache.FrequencyGroupSnapshot group = state.frequencyGroups.get(i);
            sb.append("{\"frequency\":").append(group.frequency).append(",\"nodes\":[");
            for (int j = 0; j < group.nodes.size(); j++) {
                if (j > 0) sb.append(",");
                CampusCache.NodeSnapshot node = group.nodes.get(j);
                sb.append("{")
                  .append("\"key\":\"").append(escape(node.key)).append("\",")
                  .append("\"value\":\"").append(escape(node.value)).append("\",")
                  .append("\"frequency\":").append(node.frequency).append(",")
                  .append("\"isLruInGroup\":").append(node.isLruInGroup).append(",")
                  .append("\"isNextEvictionCandidate\":").append(node.isNextEvictionCandidate)
                  .append("}");
            }
            sb.append("]}");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    /**
     * Primitive parser for simple flat JSON payload e.g. {"key":"A","value":"10","capacity":2}
     */
    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        // Regex or basic tokenizer
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (c == ',' && !inQuotes) {
                tokens.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString().trim());
        }

        for (String pair : tokens) {
            int colon = pair.indexOf(':');
            if (colon != -1) {
                String k = stripQuotes(pair.substring(0, colon).trim());
                String v = stripQuotes(pair.substring(colon + 1).trim());
                map.put(k, v);
            }
        }
        return map;
    }

    private static String stripQuotes(String str) {
        if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
}
