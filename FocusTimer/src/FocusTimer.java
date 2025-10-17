import java.time.Duration;
import java.util.Scanner;

/**
 * FocusTimer (entry point)
 * Very small Pomodoro-style timer for the terminal.
 * Flags (all optional): --work <min> --break <min> --cycles <count>
 *   Example: java FocusTimer --work 25 --break 5 --cycles 4
 *
 * I kept this simple on purpose so it's easy to read/grade.
 */
public class FocusTimer {

    private static void usage() {
        System.out.println("FocusTimer - tiny Pomodoro timer (Java)");
        System.out.println("Usage: java FocusTimer --work <min> --break <min> --cycles <count>");
        System.out.println("Example: java FocusTimer --work 25 --break 5 --cycles 4");
        System.out.println("During run: type 'p' + ENTER to pause/resume, 'q' + ENTER to quit.");
    }

    public static void main(String[] args) {
        int workMin = 25;  // defaults are the classic Pomodoro
        int breakMin = 5;
        int cycles = 4;

        // super basic arg parsing (good enough for this project)
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--work".equals(a))   workMin = Integer.parseInt(args[++i]);
                else if ("--break".equals(a)) breakMin = Integer.parseInt(args[++i]);
                else if ("--cycles".equals(a)) cycles = Integer.parseInt(args[++i]);
                else if ("--help".equals(a) || "-h".equals(a)) { usage(); return; }
                else throw new IllegalArgumentException("Unknown arg: " + a);
            }
            if (workMin <= 0 || breakMin <= 0 || cycles <= 0) {
                throw new IllegalArgumentException("All values must be > 0.");
            }
        } catch (Exception e) {
            System.err.println("Arg error: " + e.getMessage());
            usage();
            return;
        }

        CsvLogger logger = new CsvLogger("logs/focus_sessions.csv");
        TimerEngine engine = new TimerEngine(
                Duration.ofMinutes(workMin),
                Duration.ofMinutes(breakMin),
                cycles,
                logger
        );

        // if user hits Ctrl-C, try to stop cleanly (not perfect, but fine)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.stop("Interrupted");
        }));

        System.out.println("Starting FocusTimer… Type 'p' + ENTER to pause/resume, 'q' + ENTER to quit.");

        // read simple commands on a small background thread
        Thread input = new Thread(() -> {
            try (Scanner sc = new Scanner(System.in)) {
                while (engine.isRunning()) {
                    if (!sc.hasNextLine()) break;
                    String line = sc.nextLine().trim().toLowerCase();
                    if (line.equals("p")) engine.togglePause();
                    else if (line.equals("q")) { engine.stop("Quit by user"); break; }
                }
            } catch (Exception ignored) {}
        });
        input.setDaemon(true);
        input.start();

        engine.start();       // run until done or stopped
        engine.awaitFinish(); // block main so program doesn't exit early
    }
}
