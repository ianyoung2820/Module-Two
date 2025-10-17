import java.io.Writer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CsvLogger
 * Appends a tiny CSV row: timestamp, focus_minutes, cycles_completed, reason
 * This is intentionally simple (not a full CSV lib).
 */
public class CsvLogger {
    private final Path file;
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    public CsvLogger(String path) {
        this.file = Paths.get(path);
    }

    public synchronized void append(Instant start, long focusMinutes, int cycles, String reason) {
        try {
            Files.createDirectories(file.getParent());
            boolean exists = Files.exists(file);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (!exists) {
                    w.write("timestamp,focus_minutes,cycles_completed,reason\n");
                }
                // simple escaping: double up quotes
                String safe = reason.replace("\"", "\"\"");
                w.write(String.format("\"%s\",%d,%d,\"%s\"%n",
                        TS.format(start), focusMinutes, cycles, safe));
            }
        } catch (IOException e) {
            // Not ideal, but fine for a tiny project
            System.err.println("Could not write CSV: " + e.getMessage());
        }
    }
}
