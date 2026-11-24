package com.murderhecoded.domain;

import com.murderhecoded.service.DramaturgyDirectorService.ActPhase;
import com.murderhecoded.service.DramaturgyDirectorService.StoryAct;
import com.murderhecoded.service.DramaturgyDirectorService.TensionTrend;

import java.util.Locale;

/**
 * Cohesive, immutable narrative pacing profile defining act transitions,
 * harmonic tension curve anchor points, and behavioral thresholds.
 */
@SuppressWarnings("PMD")
public record DramaturgyProfile(
        String name,

        // Act progression thresholds (normalized 0.0 - 1.0)
        double act2RisingSuspenseThreshold,
        double act3CrimeThreshold,
        double act4DiscoveryThreshold,
        double act5EscalationThreshold,
        double act6ClimaxThreshold,

        // Tension curve anchor points
        double startTension,
        double peak1Progress,
        double peak1Tension,
        double lullProgress,
        double lullTension,
        double peak2Progress,
        double peak2Tension,
        double endTension,

        // Behavioral & Atmosphere thresholds
        double criticalRoomSuspenseThreshold,
        double criticalGlobalDramaThreshold,
        int antiStallingTurnThreshold,
        int unwitnessedBodyDiscoveryDelay,
        int isolationAgentCount
) {

    /**
     * Calculates the theoretical narrative drama score for a given time-step
     * based on harmonic wave interpolation across the 5 anchor points.
     *
     * @param step Current time step (1-indexed)
     * @param totalSteps Total steps planned for the simulation
     * @return Target drama score from 0.0 to 10.0, rounded to one decimal place
     */
    public double calculateTargetTension(int step, int totalSteps) {
        if (totalSteps <= 0) {
            return 5.0;
        }
        double p = Math.max(0.0, Math.min(1.0, (double) step / totalSteps));

        double tension;
        if (p <= peak1Progress) {
            double norm = peak1Progress > 0.0 ? (p / peak1Progress) : 1.0;
            tension = startTension + (peak1Tension - startTension) * Math.pow(Math.sin((Math.PI / 2.0) * norm), 2);
        } else if (p <= lullProgress) {
            double span = lullProgress - peak1Progress;
            double norm = span > 0.0 ? ((p - peak1Progress) / span) : 1.0;
            tension = lullTension + (peak1Tension - lullTension) * Math.pow(Math.cos((Math.PI / 2.0) * norm), 2);
        } else if (p <= peak2Progress) {
            double span = peak2Progress - lullProgress;
            double norm = span > 0.0 ? ((p - lullProgress) / span) : 1.0;
            tension = lullTension + (peak2Tension - lullTension) * Math.pow(Math.sin((Math.PI / 2.0) * norm), 2);
        } else {
            double span = 1.0 - peak2Progress;
            double norm = span > 0.0 ? ((p - peak2Progress) / span) : 1.0;
            tension = endTension + (peak2Tension - endTension) * Math.pow(Math.cos((Math.PI / 2.0) * norm), 2);
        }

        return Math.round(tension * 10.0) / 10.0;
    }

    /**
     * Determines current pacing trend relative to the anchor points.
     */
    public TensionTrend determineTensionTrend(int step, int totalSteps) {
        if (totalSteps <= 0) {
            return TensionTrend.RISING;
        }
        double p = Math.max(0.0, Math.min(1.0, (double) step / totalSteps));
        if (p >= act6ClimaxThreshold) {
            return TensionTrend.CLIMAX;
        }

        double peakWindowEnd = peak1Progress + (lullProgress - peak1Progress) * 0.35;
        if (p >= act3CrimeThreshold && p <= peakWindowEnd) {
            return TensionTrend.PEAK;
        }

        double lullWindowEnd = lullProgress + (peak2Progress - lullProgress) * 0.05;
        if (p > peakWindowEnd && p <= lullWindowEnd) {
            return TensionTrend.LULL;
        }

        return TensionTrend.RISING;
    }

    /**
     * Evaluates current StoryAct given the normalized simulation progress.
     */
    public StoryAct determineAct(double progress, boolean bodyDiscovered, boolean hasDeadAgent) {
        if (progress >= act6ClimaxThreshold) {
            return StoryAct.ACT_6_CLIMAX;
        }
        if (progress >= act5EscalationThreshold) {
            return StoryAct.ACT_5_ESCALATION;
        }
        if (bodyDiscovered || progress >= act4DiscoveryThreshold) {
            return StoryAct.ACT_4_DISCOVERY;
        }
        if (hasDeadAgent || progress >= act3CrimeThreshold) {
            return StoryAct.ACT_3_THE_CRIME;
        }
        if (progress >= act2RisingSuspenseThreshold) {
            return StoryAct.ACT_2_RISING_SUSPENSE;
        }
        return StoryAct.ACT_1_EXPOSITION;
    }

    public double getActStartThreshold(StoryAct act) {
        return switch (act) {
            case ACT_1_EXPOSITION -> 0.0;
            case ACT_2_RISING_SUSPENSE -> act2RisingSuspenseThreshold;
            case ACT_3_THE_CRIME -> act3CrimeThreshold;
            case ACT_4_DISCOVERY -> act4DiscoveryThreshold;
            case ACT_5_ESCALATION -> act5EscalationThreshold;
            case ACT_6_CLIMAX -> act6ClimaxThreshold;
        };
    }

    public double getActEndThreshold(StoryAct act) {
        return switch (act) {
            case ACT_1_EXPOSITION -> act2RisingSuspenseThreshold;
            case ACT_2_RISING_SUSPENSE -> act3CrimeThreshold;
            case ACT_3_THE_CRIME -> act4DiscoveryThreshold;
            case ACT_4_DISCOVERY -> act5EscalationThreshold;
            case ACT_5_ESCALATION -> act6ClimaxThreshold;
            case ACT_6_CLIMAX -> 1.0;
        };
    }

    public double calculateActProgress(StoryAct act, double overallProgress) {
        double start = getActStartThreshold(act);
        double end = getActEndThreshold(act);
        if (end <= start) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, (overallProgress - start) / (end - start)));
    }

    public ActPhase determineActPhase(StoryAct act, double overallProgress) {
        double p = calculateActProgress(act, overallProgress);
        if (p < 0.33) {
            return ActPhase.EARLY;
        } else if (p < 0.67) {
            return ActPhase.MID;
        } else {
            return ActPhase.LATE;
        }
    }

    /**
     * Standard classic murder mystery pacing (Agatha Christie / country manor style).
     */
    public static DramaturgyProfile standard() {
        return new DramaturgyProfile(
                "Standard Classic Mystery",
                0.15, // Act 2 threshold
                0.35, // Act 3 threshold
                0.50, // Act 4 threshold
                0.65, // Act 5 threshold
                0.85, // Act 6 threshold
                2.5,  // startTension
                0.40, // peak1Progress
                8.2,  // peak1Tension
                0.52, // lullProgress
                4.8,  // lullTension
                0.92, // peak2Progress
                9.8,  // peak2Tension
                6.0,  // endTension
                7.5,  // criticalRoomSuspenseThreshold
                8.0,  // criticalGlobalDramaThreshold
                2,    // antiStallingTurnThreshold
                2,    // unwitnessedBodyDiscoveryDelay
                2     // isolationAgentCount
        );
    }

    /**
     * Fast-paced thriller with early strike window and urgent escalation.
     */
    public static DramaturgyProfile fastPacedThriller() {
        return new DramaturgyProfile(
                "Fast-Paced Thriller",
                0.10, // Act 2 threshold
                0.25, // Act 3 threshold
                0.40, // Act 4 threshold
                0.60, // Act 5 threshold
                0.80, // Act 6 threshold
                3.5,  // startTension
                0.25, // peak1Progress
                8.8,  // peak1Tension
                0.35, // lullProgress
                5.5,  // lullTension
                0.80, // peak2Progress
                9.8,  // peak2Tension
                6.0,  // endTension
                7.0,  // criticalRoomSuspenseThreshold
                7.5,  // criticalGlobalDramaThreshold
                2,    // antiStallingTurnThreshold
                1,    // unwitnessedBodyDiscoveryDelay
                2     // isolationAgentCount
        );
    }

    /**
     * Slow-burn atmospheric mystery with deep exposition and late strike.
     */
    public static DramaturgyProfile slowBurn() {
        return new DramaturgyProfile(
                "Slow-Burn Atmosphere",
                0.25, // Act 2 threshold
                0.50, // Act 3 threshold
                0.65, // Act 4 threshold
                0.80, // Act 5 threshold
                0.90, // Act 6 threshold
                2.0,  // startTension
                0.50, // peak1Progress
                7.8,  // peak1Tension
                0.60, // lullProgress
                4.2,  // lullTension
                0.94, // peak2Progress
                9.8,  // peak2Tension
                5.5,  // endTension
                7.8,  // criticalRoomSuspenseThreshold
                8.2,  // criticalGlobalDramaThreshold
                3,    // antiStallingTurnThreshold
                3,    // unwitnessedBodyDiscoveryDelay
                2     // isolationAgentCount
        );
    }

    public static DramaturgyProfile fromPresetName(String name) {
        if (name == null || name.isBlank()) {
            return standard();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("fast") || lower.contains("thriller")) {
            return fastPacedThriller();
        }
        if (lower.contains("slow") || lower.contains("burn")) {
            return slowBurn();
        }
        return standard();
    }
}
