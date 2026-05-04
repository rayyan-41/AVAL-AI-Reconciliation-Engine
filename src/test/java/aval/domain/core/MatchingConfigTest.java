package aval.domain.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MatchingConfig — default values and accessor contracts.
 */
public class MatchingConfigTest {

    @Test
    void defaultAutoConfirmThreshold_is0_95() {
        MatchingConfig config = new MatchingConfig();
        assertEquals(0.95, config.getAutoConfirmThreshold(), 0.0001);
    }

    @Test
    void defaultReviewFloor_is0_70() {
        MatchingConfig config = new MatchingConfig();
        assertEquals(0.70, config.getReviewFloor(), 0.0001);
    }

    @Test
    void defaultRuleBasedDateToleranceDays_is7() {
        MatchingConfig config = new MatchingConfig();
        assertEquals(7, config.getRuleBasedDateToleranceDays());
    }

    @Test
    void defaultSemanticMaxCandidates_is3() {
        MatchingConfig config = new MatchingConfig();
        assertEquals(3, config.getSemanticMaxCandidates());
    }

    @Test
    void autoConfirmThreshold_greaterThanReviewFloor() {
        MatchingConfig config = new MatchingConfig();
        assertTrue(config.getAutoConfirmThreshold() > config.getReviewFloor(),
            "Auto-confirm threshold should be higher than review floor");
    }

    @Test
    void ruleBasedDateToleranceDays_isPositive() {
        MatchingConfig config = new MatchingConfig();
        assertTrue(config.getRuleBasedDateToleranceDays() > 0);
    }

    @Test
    void semanticMaxCandidates_isPositive() {
        MatchingConfig config = new MatchingConfig();
        assertTrue(config.getSemanticMaxCandidates() > 0);
    }

    @Test
    void twoInstances_haveEqualValues() {
        MatchingConfig c1 = new MatchingConfig();
        MatchingConfig c2 = new MatchingConfig();
        assertEquals(c1.getAutoConfirmThreshold(), c2.getAutoConfirmThreshold(), 0.0001);
        assertEquals(c1.getReviewFloor(), c2.getReviewFloor(), 0.0001);
        assertEquals(c1.getRuleBasedDateToleranceDays(), c2.getRuleBasedDateToleranceDays());
        assertEquals(c1.getSemanticMaxCandidates(), c2.getSemanticMaxCandidates());
    }
}
