package campuscache;

import java.util.*;

/**
 * Parses and executes command strings or multi-line script sequences:
 * Supported syntax:
 *   PUT(A, 10) or PUT(A,10)
 *   GET(A)
 *   STATE
 *   CAPACITY(5)
 *   RESET
 */
public class CommandParser {

    public static class Command {
        public enum Type { PUT, GET, STATE, CAPACITY, RESET, UNKNOWN }

        public final Type type;
        public final String key;
        public final String value;
        public final int capacity;
        public final String raw;

        public Command(Type type, String key, String value, int capacity, String raw) {
            this.type = type;
            this.key = key;
            this.value = value;
            this.capacity = capacity;
            this.raw = raw;
        }
    }

    public static Command parse(String line) {
        if (line == null) return new Command(Command.Type.UNKNOWN, null, null, 0, "");
        String raw = line.trim();
        if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("//")) {
            return null; // comment or empty
        }

        String upper = raw.toUpperCase();
        if (upper.equals("STATE") || upper.equals("STATE()")) {
            return new Command(Command.Type.STATE, null, null, 0, raw);
        }
        if (upper.equals("RESET") || upper.equals("RESET()")) {
            return new Command(Command.Type.RESET, null, null, 0, raw);
        }

        if (upper.startsWith("GET(") && raw.endsWith(")")) {
            String inside = raw.substring(4, raw.length() - 1).trim();
            return new Command(Command.Type.GET, inside, null, 0, raw);
        }

        if (upper.startsWith("PUT(") && raw.endsWith(")")) {
            String inside = raw.substring(4, raw.length() - 1).trim();
            int commaIdx = inside.indexOf(',');
            if (commaIdx != -1) {
                String k = inside.substring(0, commaIdx).trim();
                String v = inside.substring(commaIdx + 1).trim();
                return new Command(Command.Type.PUT, k, v, 0, raw);
            }
        }

        if (upper.startsWith("CAPACITY(") && raw.endsWith(")")) {
            String inside = raw.substring(9, raw.length() - 1).trim();
            try {
                int cap = Integer.parseInt(inside);
                return new Command(Command.Type.CAPACITY, null, null, cap, raw);
            } catch (NumberFormatException ignored) {}
        }

        return new Command(Command.Type.UNKNOWN, null, null, 0, raw);
    }

    public static List<Command> parseSequence(String sequenceText) {
        List<Command> commands = new ArrayList<>();
        if (sequenceText == null) return commands;
        String[] lines = sequenceText.split("\\r?\\n");
        for (String line : lines) {
            Command cmd = parse(line);
            if (cmd != null) {
                commands.add(cmd);
            }
        }
        return commands;
    }
}
