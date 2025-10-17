import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

/**
 * TimerEngine
 * Minimal state machine: WORK -> BREAK repeated N times.
 * Uses a 1s fixed-rate scheduler. Remaining time is computed from a target end
 * time so it doesn't drift if a tick is a little late.
 *
 * I kept the console updates simple: just redraw one line with '\r'.
 */
public class TimerEngine {

    enum Phase { WORK, BREAK, DONE }

    private final Duration workDur;
    private final Duration breakDur;
    private final int totalCycles;
    private final CsvLogger logger;

    private volatile boolean running = false;
    private volatile boolean stopping = false;

    private Phase phase = Phase.WORK;
    private Instant phaseEnd;
    private int completedCycles = 0;

    private boolean paused = false;
    private Instant pausedAt;
    private Duration pausedSoFar = Duration.ZERO;

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch finished = new CountDownLatch(1);

    private Instant sessionStart;
    private String endReason = "Completed";

    public TimerEngine(Duration workDur, Duration breakDur, int totalCycles, CsvLogger logger) {
        this.workDur = workDur;
        this.breakDur = breakDur;
        this.totalCycles = totalCycles;
        this.logger = logger;
    }

    public boolean isRunning() {
        return running && !stopping;
    }

    public void start() {
        if (running) return;
        running = true;
        sessionStart = Instant.now();
        switchTo(Phase.WORK);
        ses.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    public void stop(String reason) {
        stopping = true;
        endReason = reason;
        ses.shutdownNow();
        finish();
    }

    public void togglePause() {
        if (!paused) {
            paused = true;
            pausedAt = Instant.now();
            writeStatusLine("(paused)");
        } else {
            // add how long we've been paused
            pausedSoFar = pausedSoFar.plus(Duration.between(pausedAt, Instant.now()));
            paused = false;
        }
    }

    public void awaitFinish() {
        try { finished.await(); } catch (InterruptedException ignored) {}
    }

    private void switchTo(Phase next) {
        phase = next;
        paused = false;
        pausedSoFar = Duration.ZERO;

        if (next == Phase.WORK)      phaseEnd = Instant.now().plus(workDur);
        else if (next == Phase.BREAK) phaseEnd = Instant.now().plus(breakDur);
        else                          phaseEnd = Instant.now();
    }

    private void tick() {
        try {
            if (stopping) return;

            Duration remaining = Duration.between(Instant.now(), phaseEnd).plus(pausedSoFar);
            if (paused) {
                writeStatusLine("(paused)");
                return;
            }

            if (!remaining.isNegative() && !remaining.isZero()) {
                writeStatusLine("");
                return;
            }

            // time's up for this phase
            if (phase == Phase.WORK) {
                completedCycles++;
                if (completedCycles >= totalCycles) {
                    phase = Phase.DONE;
                    finish();
                    return;
                }
                switchTo(Phase.BREAK);
            } else if (phase == Phase.BREAK) {
                switchTo(Phase.WORK);
            }
            writeStatusLine("");
        } catch (Exception e) {
            stop("Error: " + e.getMessage());
        }
    }

    private void writeStatusLine(String extra) {
        Duration rem = Duration.between(Instant.now(), phaseEnd).plus(pausedSoFar);
        long secs = Math.max(0, rem.toSeconds());
        long mm = secs / 60;
        long ss = secs % 60;

        String label = switch (phase) {
            case WORK -> "FOCUS";
            case BREAK -> "BREAK";
            default -> "DONE ";
        };

        // rough line clear: carriage return + pad some spaces (simple + portable)
        String line = String.format("%s | cycle %d/%d | %02d:%02d %s",
                label,
                Math.min(completedCycles + (phase == Phase.WORK ? 1 : 0), totalCycles),
                totalCycles, mm, ss,
                extra);
        System.out.print("\r" + pad(line, 64));
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private void finish() {
        if (!running) return;
        running = false;
        ses.shutdownNow();
        System.out.print("\r" + pad("", 64) + "\r"); // clear the line
        System.out.println("Session ended: " + endReason);

        long totalFocusMin = completedCycles * workDur.toMinutes();
        logger.append(sessionStart, totalFocusMin, completedCycles, endReason);
        finished.countDown();
    }
}
