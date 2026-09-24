package com.example.hospital_dashboard.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowSizeTest {

    @Test
    fun testWidthDpToAdaptiveSize_boundaries() {
        assertEquals(AdaptiveSize.Compact, widthDpToAdaptiveSize(0))
        assertEquals(AdaptiveSize.Compact, widthDpToAdaptiveSize(411))
        assertEquals(AdaptiveSize.Compact, widthDpToAdaptiveSize(599))

        assertEquals(AdaptiveSize.Medium, widthDpToAdaptiveSize(600))
        assertEquals(AdaptiveSize.Medium, widthDpToAdaptiveSize(700))
        assertEquals(AdaptiveSize.Medium, widthDpToAdaptiveSize(839))

        assertEquals(AdaptiveSize.Expanded, widthDpToAdaptiveSize(840))
        assertEquals(AdaptiveSize.Expanded, widthDpToAdaptiveSize(1000))
        assertEquals(AdaptiveSize.Expanded, widthDpToAdaptiveSize(1440))
    }

    @Test
    fun testAdaptiveSize_properties() {
        assertTrue(AdaptiveSize.Compact.isCompact)
        assertFalse(AdaptiveSize.Compact.isAtLeastMedium)
        assertFalse(AdaptiveSize.Compact.isExpanded)

        assertFalse(AdaptiveSize.Medium.isCompact)
        assertTrue(AdaptiveSize.Medium.isAtLeastMedium)
        assertFalse(AdaptiveSize.Medium.isExpanded)

        assertFalse(AdaptiveSize.Expanded.isCompact)
        assertTrue(AdaptiveSize.Expanded.isAtLeastMedium)
        assertTrue(AdaptiveSize.Expanded.isExpanded)
    }

    @Test
    fun testAdaptiveSize_pick() {
        assertEquals("compact", AdaptiveSize.Compact.pick("compact", "medium", "expanded"))
        assertEquals("medium", AdaptiveSize.Medium.pick("compact", "medium", "expanded"))
        assertEquals("expanded", AdaptiveSize.Expanded.pick("compact", "medium", "expanded"))

        // Test default expandedValue = mediumValue
        assertEquals("medium_default", AdaptiveSize.Medium.pick("compact", "medium_default"))
        assertEquals("medium_default", AdaptiveSize.Expanded.pick("compact", "medium_default"))
    }
}
