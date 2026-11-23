package com.titradeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PubChemDepictionTest {
    @Test fun imageSizeUsesHeavyAtomCountWithLowerAndUpperBounds() {
        assertEquals(600, depictionImageSize(0))
        assertEquals(600, depictionImageSize(10))
        assertEquals(684, depictionImageSize(13))
        assertEquals(1200, depictionImageSize(40))
        assertEquals(1800, depictionImageSize(90))
        assertEquals(2000, depictionImageSize(112))
    }
}
