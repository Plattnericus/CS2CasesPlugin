package dev.plattnericus.cases.opening;

/**
 * Presentation of a running opening. The session decides everything; a view only shows it.
 * {@code center} is the (fractional) reel index currently under the marker.
 */
interface OpeningView {

    void open();

    void frame(double center);

    /** Client ticks needed to finish the last movement before the winner grows. */
    default int settleTicks() { return 0; }

    /** The winner is now known to the player (gold mystery icon replaced, effects). */
    void reveal();

    /** Final state after the reveal hold. */
    void result();

    /** Removes / closes everything; safe to call more than once. */
    void close();
}
