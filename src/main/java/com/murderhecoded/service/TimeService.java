package com.murderhecoded.service;

import org.springframework.stereotype.Service;

@Service
public class TimeService {

    public static final int START_MINUTES = 0; // Starts at 00:00
    public static final int DEFAULT_MINUTES_PER_STEP = 15;
    public static final int MINUTES_PER_STEP = DEFAULT_MINUTES_PER_STEP;
    public static final int MINUTES_PER_DAY = 24 * 60;

    private int minutesPerStep = DEFAULT_MINUTES_PER_STEP;

    public int getMinutesPerStep() {
        return minutesPerStep;
    }

    public void setMinutesPerStep(int minutesPerStep) {
        if (minutesPerStep <= 0) {
            this.minutesPerStep = DEFAULT_MINUTES_PER_STEP;
        } else {
            this.minutesPerStep = minutesPerStep;
        }
    }

    public int hoursToSteps(int hours) {
        return (hours * 60) / minutesPerStep;
    }

    public int stepsToHours(int steps) {
        return (steps * minutesPerStep) / 60;
    }

    public int getDay(int step) {
        int totalMinutes = START_MINUTES + (Math.max(0, step) * minutesPerStep);
        return (totalMinutes / MINUTES_PER_DAY) + 1;
    }

    public int getHour(int step) {
        int totalMinutes = START_MINUTES + (Math.max(0, step) * minutesPerStep);
        return totalMinutes / 60;
    }

    public int getMinute(int step) {
        int totalMinutes = START_MINUTES + (Math.max(0, step) * minutesPerStep);
        return totalMinutes % 60;
    }

    /**
     * Formats time for display in the UI (e.g., "00:00", "01:15").
     * Counts up from 00:00 in hours and minutes from start.
     */
    public String formatForUI(int step) {
        return String.format("%02d:%02d", getHour(step), getMinute(step));
    }

    /**
     * Formats time for injection into LLM prompts (e.g., "00:00").
     */
    public String formatForPrompt(int step) {
        return formatForUI(step);
    }

    /**
     * Formats a time interval for prompts and logs (e.g., "00:00 - 00:15").
     */
    public String formatInterval(int fromStep, int toStep) {
        int hFrom = getHour(fromStep);
        int mFrom = getMinute(fromStep);
        int hTo = getHour(toStep);
        int mTo = getMinute(toStep);

        return String.format("%02d:%02d - %02d:%02d", hFrom, mFrom, hTo, mTo);
    }
}
