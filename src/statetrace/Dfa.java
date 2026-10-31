package statetrace;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DFA total: setiap pasangan state/simbol mempunyai tepat satu transisi. */
public final class Dfa {
    public static final int MAX_BYTES = 65_536;
    public static final int MAX_INPUT = 4096;
    private final String name;
    private final List<String> states;
    private final List<String> alphabet;
    private final String start;
    private final Set<String> accepting;
    private final Map<String, Map<String, String>> transitions;

    private Dfa(String name, List<String> states, List<String> alphabet, String start,
                Set<String> accepting, Map<String, Map<String, String>> transitions) {
        this.name = name;
        this.states = List.copyOf(states);
        this.alphabet = List.copyOf(alphabet);
        this.start = start;
        this.accepting = Collections.unmodifiableSet(new LinkedHashSet<>(accepting));
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        transitions.forEach((state, row) -> copy.put(state, Map.copyOf(row)));
        this.transitions = Map.copyOf(copy);
    }

    public String name() { return name; }
    public List<String> states() { return states; }
    public List<String> alphabet() { return alphabet; }
    public String start() { return start; }
    public Set<String> accepting() { return accepting; }
    public String next(String state, String symbol) { return transitions.get(state).get(symbol); }

    public static Dfa parse(String source) {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Definisi DFA harus maksimal 65536 byte UTF-8.");
        }
        if (source.indexOf('\0') >= 0) throw new IllegalArgumentException("Definisi mengandung NUL.");
        String clean = source.startsWith("\uFEFF") ? source.substring(1) : source;
        Map<String, String> fields = new LinkedHashMap<>();
        List<String[]> moves = new ArrayList<>();
        Set<String> seenMoves = new LinkedHashSet<>();
        String[] lines = clean.split("\\R", -1);
        for (int line = 0; line < lines.length; line++) {
            String text = lines[line].strip();
            if (text.isEmpty() || text.startsWith("#")) continue;
            int equals = text.indexOf('=');
            if (equals < 0 || equals != text.lastIndexOf('=')) fail(line, "Gunakan satu pemisah =.");
            String key = text.substring(0, equals).strip();
            String value = text.substring(equals + 1).strip();
            if (key.contains(",")) {
                String[] pair = key.split(",", -1);
                if (pair.length != 2) fail(line, "Transisi harus berbentuk state,simbol=tujuan.");
                String from = pair[0].strip();
                String symbol = pair[1].strip();
                if (!seenMoves.add(from + "," + symbol)) fail(line, "Transisi ganda: " + from + "," + symbol + ".");
                moves.add(new String[] {from, symbol, value, Integer.toString(line)});
            } else {
                if (!Set.of("name", "states", "alphabet", "start", "accept").contains(key)) fail(line, "Kolom tidak dikenal: " + key + ".");
                if (fields.putIfAbsent(key, value) != null) fail(line, "Kolom ganda: " + key + ".");
            }
        }
        for (String key : List.of("name", "states", "alphabet", "start", "accept")) {
            if (!fields.containsKey(key)) throw new IllegalArgumentException("Kolom wajib belum ada: " + key + ".");
        }
        String name = fields.get("name");
        if (name.isBlank() || name.length() > 120 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("name harus 1–120 karakter tanpa karakter kontrol.");
        }
        List<String> states = csv(fields.get("states"), false);
        if (states.size() > 50 || states.stream().anyMatch(state -> !state.matches("[A-Za-z][A-Za-z0-9_-]{0,31}"))) {
            throw new IllegalArgumentException("Gunakan 1–50 state; nama diawali huruf, maksimal 32 karakter huruf/angka/_/-.");
        }
        List<String> alphabet = csv(fields.get("alphabet"), false);
        if (alphabet.size() > 16 || alphabet.stream().anyMatch(symbol -> !validSymbol(symbol))) {
            throw new IllegalArgumentException("Alfabet berisi 1–16 simbol Unicode tunggal; tanpa whitespace, kontrol, koma, =, atau #.");
        }
        String start = fields.get("start");
        if (!states.contains(start)) throw new IllegalArgumentException("State awal tidak terdaftar: " + start + ".");
        Set<String> accepting = new LinkedHashSet<>(csv(fields.get("accept"), true));
        if (!states.containsAll(accepting)) throw new IllegalArgumentException("State penerima tidak terdaftar dalam states.");
        Map<String, Map<String, String>> transitions = new LinkedHashMap<>();
        for (String state : states) transitions.put(state, new LinkedHashMap<>());
        for (String[] move : moves) {
            int line = Integer.parseInt(move[3]);
            if (!states.contains(move[0]) || !states.contains(move[2])) fail(line, "State asal/tujuan transisi tidak terdaftar.");
            if (!alphabet.contains(move[1])) fail(line, "Simbol transisi tidak terdaftar dalam alphabet.");
            transitions.get(move[0]).put(move[1], move[2]);
        }
        for (String state : states) {
            for (String symbol : alphabet) {
                if (!transitions.get(state).containsKey(symbol)) {
                    throw new IllegalArgumentException("Transisi belum lengkap: " + state + "," + symbol + ".");
                }
            }
        }
        return new Dfa(name, states, alphabet, start, accepting, transitions);
    }

    private static boolean validSymbol(String symbol) {
        if (symbol.codePointCount(0, symbol.length()) != 1) return false;
        int cp = symbol.codePointAt(0);
        if (cp >= 0xD800 && cp <= 0xDFFF) return false;
        return !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)
                && !Character.isISOControl(cp) && !",=#".contains(symbol);
    }

    private static List<String> csv(String value, boolean allowEmpty) {
        if (value.isEmpty() && allowEmpty) return List.of();
        List<String> values = new ArrayList<>();
        for (String item : value.split(",", -1)) {
            String trimmed = item.strip();
            if (trimmed.isEmpty() || values.contains(trimmed)) throw new IllegalArgumentException("Daftar mengandung item kosong atau duplikat.");
            values.add(trimmed);
        }
        return values;
    }

    private static void fail(int line, String message) {
        throw new IllegalArgumentException("Baris " + (line + 1) + ": " + message);
    }

    public Set<String> unreachable() {
        Set<String> reached = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (reached.add(current)) queue.addAll(transitions.get(current).values());
        }
        Set<String> result = new LinkedHashSet<>(states);
        result.removeAll(reached);
        return Collections.unmodifiableSet(result);
    }

    public record Step(int number, String from, String symbol, String to) { }
    public record Result(String input, String start, String end, boolean accepted, List<Step> steps) {
        public Result { steps = List.copyOf(steps); }
    }

    public Result simulate(String input) {
        if (input == null) throw new IllegalArgumentException("String input tidak boleh null.");
        int[] codePoints = input.codePoints().toArray();
        if (codePoints.length > MAX_INPUT) throw new IllegalArgumentException("String input maksimal 4096 simbol Unicode.");
        List<Step> steps = new ArrayList<>();
        String current = start;
        for (int i = 0; i < codePoints.length; i++) {
            String symbol = new String(Character.toChars(codePoints[i]));
            if (!alphabet.contains(symbol)) throw new IllegalArgumentException("Simbol di luar alfabet pada posisi " + (i + 1) + ": U+" + String.format("%04X", codePoints[i]) + ".");
            String destination = next(current, symbol);
            steps.add(new Step(i + 1, current, symbol, destination));
            current = destination;
        }
        return new Result(input, start, current, accepting.contains(current), steps);
    }
}
