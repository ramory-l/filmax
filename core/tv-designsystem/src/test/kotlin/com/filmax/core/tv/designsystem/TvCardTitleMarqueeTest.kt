package com.filmax.core.tv.designsystem

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvCardTitleMarqueeTest {
    @Test
    fun `full effects keep a long title running`() {
        assertEquals(Int.MAX_VALUE, cardTitleMarqueeIterations(reducedMotion = false, lightEffects = false))
    }

    @Test
    fun `light effects run a long title once, then the screen goes still`() {
        assertEquals(1, cardTitleMarqueeIterations(reducedMotion = false, lightEffects = true))
    }

    @Test
    fun `reduced motion never runs the title`() {
        assertNull(cardTitleMarqueeIterations(reducedMotion = true, lightEffects = false))
        assertNull(cardTitleMarqueeIterations(reducedMotion = true, lightEffects = true))
    }
}
