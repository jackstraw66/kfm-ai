package kfm.ai.ingest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based test for delay validation range in {@link BatchIngestProperties}.
 *
 * // Feature: setlist-batch-ingest, Property 7: Delay Validation Range
 *
 * <p><b>Validates: Requirements 8.3, 8.4</b></p>
 *
 * <p>For any integer value V provided as the request delay configuration, the application
 * SHALL start successfully if and only if 100 ≤ V ≤ 60000. Values outside this range
 * SHALL prevent startup.</p>
 */
class BatchIngestDelayValidationRangePropertyTest {

    private final Validator validator;

    BatchIngestDelayValidationRangePropertyTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    /**
     * Property 7: In-range delay values are accepted.
     *
     * <p>For any integer V in [100, 60000], creating a {@link BatchIngestProperties} with
     * {@code requestDelayMs = V} SHALL produce no validation violations.</p>
     *
     * <p><b>Validates: Requirements 8.3, 8.4</b></p>
     */
    @Property(tries = 200)
    void inRangeDelayValuesAreAccepted(
            @ForAll @IntRange(min = 100, max = 60000) int delayMs
    ) {
        BatchIngestProperties properties = new BatchIngestProperties();
        properties.setRequestDelayMs(delayMs);

        Set<ConstraintViolation<BatchIngestProperties>> violations = validator.validate(properties);

        assertTrue(violations.isEmpty(),
                "Expected no violations for in-range value " + delayMs
                        + " but got: " + violations);
    }

    /**
     * Property 7: Out-of-range delay values are rejected.
     *
     * <p>For any integer V where V < 100 or V > 60000, creating a
     * {@link BatchIngestProperties} with {@code requestDelayMs = V} SHALL produce
     * at least one validation violation.</p>
     *
     * <p><b>Validates: Requirements 8.3, 8.4</b></p>
     */
    @Property(tries = 200)
    void outOfRangeDelayValuesAreRejected(
            @ForAll("outOfRangeDelays") int delayMs
    ) {
        BatchIngestProperties properties = new BatchIngestProperties();
        properties.setRequestDelayMs(delayMs);

        Set<ConstraintViolation<BatchIngestProperties>> violations = validator.validate(properties);

        assertFalse(violations.isEmpty(),
                "Expected at least one violation for out-of-range value " + delayMs
                        + " but got none.");
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates random integers that fall outside the valid range [100, 60000].
     * Covers both below-minimum and above-maximum values across the full int range.
     */
    @Provide
    Arbitrary<Integer> outOfRangeDelays() {
        Arbitrary<Integer> belowMin = Arbitraries.integers()
                .between(Integer.MIN_VALUE, 99);
        Arbitrary<Integer> aboveMax = Arbitraries.integers()
                .between(60001, Integer.MAX_VALUE);
        return Arbitraries.oneOf(belowMin, aboveMax);
    }
}
