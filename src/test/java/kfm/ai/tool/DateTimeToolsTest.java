package kfm.ai.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DateTimeTools}.
 */
class DateTimeToolsTest {

    @Test
    void getCurrentDateTime_returnsNonNullNonBlank() {
        DateTimeTools tools = new DateTimeTools();

        String result = tools.getCurrentDateTime();

        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void getCurrentDateTime_containsDateComponents() {
        DateTimeTools tools = new DateTimeTools();

        String result = tools.getCurrentDateTime();

        // Should contain a 'T' separating date and time in ISO format
        assertTrue(result.contains("T"), "Expected ISO datetime format with 'T' separator");
    }
}
