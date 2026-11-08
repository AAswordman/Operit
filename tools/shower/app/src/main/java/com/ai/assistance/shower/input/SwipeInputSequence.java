package com.ai.assistance.shower.input;

/** Real-time pointer sequencing shared with the server; no Android dependency is needed to verify timing. */
public final class SwipeInputSequence {
    public enum Action { DOWN, MOVE, UP, CANCEL }

    public interface Clock {
        long uptimeMillis();
        void sleep(long millis);
    }

    public interface Dispatcher {
        void dispatch(Action action, long downTime, long eventTime, float fraction);
    }

    private SwipeInputSequence() {}

    public static void run(long durationMs, int steps, Clock clock, Dispatcher dispatcher) {
        if (durationMs <= 0L || steps <= 0) {
            throw new IllegalArgumentException("Swipe duration and steps must be positive");
        }
        final long downTime = clock.uptimeMillis();
        dispatcher.dispatch(Action.DOWN, downTime, downTime, 0f);
        float lastFraction = 0f;
        try {
            for (int step = 1; step <= steps; step++) {
                long scheduledTime = downTime + Math.round(durationMs * (step / (double) steps));
                long remaining;
                // A past timestamp cannot make a gesture last longer. Wait on the actual clock,
                // and tolerate a sleeper waking early without sending future-dated events.
                while ((remaining = scheduledTime - clock.uptimeMillis()) > 0L) {
                    clock.sleep(remaining);
                }
                float fraction = step / (float) steps;
                dispatcher.dispatch(Action.MOVE, downTime, clock.uptimeMillis(), fraction);
                lastFraction = fraction;
            }
            dispatcher.dispatch(Action.UP, downTime, clock.uptimeMillis(), 1f);
        } catch (RuntimeException failure) {
            // Release only the incomplete stream, never retry the gesture after uncertain delivery.
            try {
                dispatcher.dispatch(Action.CANCEL, downTime, clock.uptimeMillis(), lastFraction);
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
}
