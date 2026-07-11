package com.cdandeniya.fraud.rules;

/**
 * What a single rule hands back: did it fire, how many risk points it adds,
 * and a human-readable reason. Reason is null when the rule didn't fire.
 */
public class RuleResult {

    private final boolean triggered;
    private final int score;
    private final String reason;

    private RuleResult(boolean triggered, int score, String reason) {
        this.triggered = triggered;
        this.score = score;
        this.reason = reason;
    }

    public static RuleResult triggered(int score, String reason) {
        return new RuleResult(true, score, reason);
    }

    public static RuleResult notTriggered() {
        return new RuleResult(false, 0, null);
    }

    public boolean isTriggered() {
        return triggered;
    }

    public int getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }
}
