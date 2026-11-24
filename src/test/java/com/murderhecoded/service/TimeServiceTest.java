package com.murderhecoded.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class TimeServiceTest {

    private TimeService timeService;

    @BeforeEach
    void setUp() {
        timeService = new TimeService();
    }

    @Test
    void testInitialTimeStepZero() {
        // Step 0 should be 00:00
        assertEquals("00:00", timeService.formatForUI(0));
        assertEquals(0, timeService.getHour(0));
        assertEquals(0, timeService.getMinute(0));
    }

    @Test
    void testStepOne() {
        // Step 1 should be 00:15
        assertEquals("00:15", timeService.formatForUI(1));
        assertEquals(0, timeService.getHour(1));
        assertEquals(15, timeService.getMinute(1));
    }

    @Test
    void testHoursToStepsConversions() {
        assertEquals(96, timeService.hoursToSteps(24));
        assertEquals(144, timeService.hoursToSteps(36));
        assertEquals(192, timeService.hoursToSteps(48));

        assertEquals(24, timeService.stepsToHours(96));
        assertEquals(36, timeService.stepsToHours(144));
        assertEquals(48, timeService.stepsToHours(192));
    }

    @Test
    void testSameDayInterval() {
        // Step 0 to 1: 00:00 - 00:15
        String interval = timeService.formatInterval(0, 1);
        assertEquals("00:00 - 00:15", interval);
    }

    @Test
    void testMidnightTransitionInterval() {
        // Step 59 is 14:45. Step 60 is 15:00.
        assertEquals("14:45", timeService.formatForUI(59));
        assertEquals("15:00", timeService.formatForUI(60));

        String interval = timeService.formatInterval(59, 60);
        assertEquals("14:45 - 15:00", interval);
    }

    @Test
    void testEndTimes() {
        // 24 hours (step 96): 24:00
        assertEquals("24:00", timeService.formatForUI(96));

        // 36 hours (step 144): 36:00
        assertEquals("36:00", timeService.formatForUI(144));

        // 48 hours (step 192): 48:00
        assertEquals("48:00", timeService.formatForUI(192));
    }

    @Test
    void testConfigurableInterval20Minutes() {
        timeService.setMinutesPerStep(20);
        assertEquals(20, timeService.getMinutesPerStep());
        assertEquals("00:20", timeService.formatForUI(1));
        assertEquals("00:40", timeService.formatForUI(2));
        assertEquals("01:00", timeService.formatForUI(3));
        assertEquals(72, timeService.hoursToSteps(24));
        assertEquals(108, timeService.hoursToSteps(36));
        assertEquals(24, timeService.stepsToHours(72));
        assertEquals("00:00 - 00:20", timeService.formatInterval(0, 1));
    }

    @Test
    void testConfigurableInterval30Minutes() {
        timeService.setMinutesPerStep(30);
        assertEquals(30, timeService.getMinutesPerStep());
        assertEquals("00:30", timeService.formatForUI(1));
        assertEquals("01:00", timeService.formatForUI(2));
        assertEquals(48, timeService.hoursToSteps(24));
        assertEquals(72, timeService.hoursToSteps(36));
        assertEquals(36, timeService.stepsToHours(72));
        assertEquals("00:00 - 00:30", timeService.formatInterval(0, 1));
    }

    @Test
    void testConfigurableInterval60Minutes() {
        timeService.setMinutesPerStep(60);
        assertEquals(60, timeService.getMinutesPerStep());
        assertEquals("01:00", timeService.formatForUI(1));
        assertEquals("02:00", timeService.formatForUI(2));
        assertEquals(24, timeService.hoursToSteps(24));
        assertEquals(36, timeService.hoursToSteps(36));
        assertEquals(48, timeService.stepsToHours(48));
        assertEquals("00:00 - 01:00", timeService.formatInterval(0, 1));
    }

    @Test
    void testInvalidStepIntervalFallback() {
        timeService.setMinutesPerStep(0);
        assertEquals(TimeService.DEFAULT_MINUTES_PER_STEP, timeService.getMinutesPerStep());
        timeService.setMinutesPerStep(-15);
        assertEquals(TimeService.DEFAULT_MINUTES_PER_STEP, timeService.getMinutesPerStep());
    }
}
