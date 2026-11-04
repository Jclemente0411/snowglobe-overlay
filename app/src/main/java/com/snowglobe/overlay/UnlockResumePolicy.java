package com.snowglobe.overlay;

/** Bounded, opt-in wake reconciliation; no wake locks, polling or forced launches. */
public final class UnlockResumePolicy {
    private UnlockResumePolicy(){}
    public static final int MAX_RETRIES=12;
    public static final long CHECK_DELAY_MS=400L;
    public static boolean shouldRetry(int completedChecks,boolean userEnabled,boolean permitted,
            boolean interactive,boolean studioOpen,boolean alreadyAttached) {
        return completedChecks>=0 && completedChecks<MAX_RETRIES && userEnabled && permitted
            && interactive && !studioOpen && !alreadyAttached;
    }
    public static boolean shouldStartAfterBoot(boolean userEnabled,boolean permitted,boolean running) {
        return userEnabled&&permitted&&!running;
    }
}