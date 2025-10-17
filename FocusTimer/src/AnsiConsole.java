/**
 * Keeping the helper, but dead simple:
 * clears the current line by printing a carriage return + spaces.
 * (I only use this style inside TimerEngine now.)
 */
public class AnsiConsole {
    public static void clearLine() {
        System.out.print("\r" + " ".repeat(64) + "\r");
    }
}
