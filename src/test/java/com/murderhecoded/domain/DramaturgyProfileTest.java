package com.murderhecoded.domain;

import com.murderhecoded.service.DramaturgyDirectorService.StoryAct;
import com.murderhecoded.service.DramaturgyDirectorService.TensionTrend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class DramaturgyProfileTest {

    @Test
    void testStandardPresetValuesAndCurve() {
        DramaturgyProfile standard = DramaturgyProfile.standard();
        assertNotNull(standard);
        assertEquals("Standard Classic Mystery", standard.name());
        assertEquals(0.15, standard.act2RisingSuspenseThreshold());
        assertEquals(0.35, standard.act3CrimeThreshold());
        assertEquals(0.50, standard.act4DiscoveryThreshold());
        assertEquals(0.65, standard.act5EscalationThreshold());
        assertEquals(0.85, standard.act6ClimaxThreshold());

        int total = 100;
        assertEquals(2.5, standard.calculateTargetTension(0, total), 0.1);
        assertEquals(8.2, standard.calculateTargetTension(40, total), 0.1);
        assertEquals(4.8, standard.calculateTargetTension(52, total), 0.1);
        assertEquals(9.8, standard.calculateTargetTension(92, total), 0.1);
        assertEquals(6.0, standard.calculateTargetTension(100, total), 0.1);

        for (int s = 1; s <= total; s++) {
            double tension = standard.calculateTargetTension(s, total);
            assertTrue(tension >= 0.0 && tension <= 10.0, "Tension out of bounds at step " + s + ": " + tension);
        }

        assertEquals(TensionTrend.RISING, standard.determineTensionTrend(5, total));
        assertEquals(TensionTrend.PEAK, standard.determineTensionTrend(40, total));
        assertEquals(TensionTrend.LULL, standard.determineTensionTrend(50, total));
        assertEquals(TensionTrend.RISING, standard.determineTensionTrend(70, total));
        assertEquals(TensionTrend.CLIMAX, standard.determineTensionTrend(90, total));

        assertEquals(StoryAct.ACT_1_EXPOSITION, standard.determineAct(0.05, false, false));
        assertEquals(StoryAct.ACT_2_RISING_SUSPENSE, standard.determineAct(0.20, false, false));
        assertEquals(StoryAct.ACT_3_THE_CRIME, standard.determineAct(0.35, false, false));
        assertEquals(StoryAct.ACT_4_DISCOVERY, standard.determineAct(0.36, true, false));
        assertEquals(StoryAct.ACT_5_ESCALATION, standard.determineAct(0.70, false, false));
        assertEquals(StoryAct.ACT_6_CLIMAX, standard.determineAct(0.90, false, false));
    }

    @Test
    void testFastPacedThrillerPreset() {
        DramaturgyProfile thriller = DramaturgyProfile.fastPacedThriller();
        assertNotNull(thriller);
        assertEquals("Fast-Paced Thriller", thriller.name());
        assertEquals(0.10, thriller.act2RisingSuspenseThreshold());
        assertEquals(0.25, thriller.act3CrimeThreshold());
        assertEquals(0.40, thriller.act4DiscoveryThreshold());
        assertEquals(0.60, thriller.act5EscalationThreshold());
        assertEquals(0.80, thriller.act6ClimaxThreshold());

        int total = 100;
        assertEquals(3.5, thriller.calculateTargetTension(0, total), 0.1);
        assertEquals(8.8, thriller.calculateTargetTension(25, total), 0.1);
        assertEquals(5.5, thriller.calculateTargetTension(35, total), 0.1);
        assertEquals(9.8, thriller.calculateTargetTension(80, total), 0.1);
        assertEquals(6.0, thriller.calculateTargetTension(100, total), 0.1);

        for (int s = 1; s <= total; s++) {
            double tension = thriller.calculateTargetTension(s, total);
            assertTrue(tension >= 0.0 && tension <= 10.0, "Tension out of bounds at step " + s + ": " + tension);
        }

        // Fast thriller strikes and climaxes earlier
        assertEquals(StoryAct.ACT_3_THE_CRIME, thriller.determineAct(0.26, false, false));
        assertEquals(StoryAct.ACT_6_CLIMAX, thriller.determineAct(0.81, false, false));
    }

    @Test
    void testSlowBurnPreset() {
        DramaturgyProfile slowBurn = DramaturgyProfile.slowBurn();
        assertNotNull(slowBurn);
        assertEquals("Slow-Burn Atmosphere", slowBurn.name());
        assertEquals(0.25, slowBurn.act2RisingSuspenseThreshold());
        assertEquals(0.50, slowBurn.act3CrimeThreshold());
        assertEquals(0.65, slowBurn.act4DiscoveryThreshold());
        assertEquals(0.80, slowBurn.act5EscalationThreshold());
        assertEquals(0.90, slowBurn.act6ClimaxThreshold());

        int total = 100;
        assertEquals(2.0, slowBurn.calculateTargetTension(0, total), 0.1);
        assertEquals(7.8, slowBurn.calculateTargetTension(50, total), 0.1);
        assertEquals(4.2, slowBurn.calculateTargetTension(60, total), 0.1);
        assertEquals(9.8, slowBurn.calculateTargetTension(94, total), 0.1);
        assertEquals(5.5, slowBurn.calculateTargetTension(100, total), 0.1);

        for (int s = 1; s <= total; s++) {
            double tension = slowBurn.calculateTargetTension(s, total);
            assertTrue(tension >= 0.0 && tension <= 10.0, "Tension out of bounds at step " + s + ": " + tension);
        }

        // At progress 0.35, slow burn is still in Act 2, whereas standard would be in Act 3
        assertEquals(StoryAct.ACT_2_RISING_SUSPENSE, slowBurn.determineAct(0.35, false, false));
        assertEquals(StoryAct.ACT_3_THE_CRIME, slowBurn.determineAct(0.51, false, false));
    }

    @Test
    void testFromPresetName() {
        assertEquals("Fast-Paced Thriller", DramaturgyProfile.fromPresetName("thriller").name());
        assertEquals("Fast-Paced Thriller", DramaturgyProfile.fromPresetName("Fast").name());
        assertEquals("Slow-Burn Atmosphere", DramaturgyProfile.fromPresetName("slow-burn").name());
        assertEquals("Standard Classic Mystery", DramaturgyProfile.fromPresetName("standard").name());
        assertEquals("Standard Classic Mystery", DramaturgyProfile.fromPresetName(null).name());
        assertEquals("Standard Classic Mystery", DramaturgyProfile.fromPresetName("").name());
        assertEquals("Standard Classic Mystery", DramaturgyProfile.fromPresetName("random").name());
    }
}
