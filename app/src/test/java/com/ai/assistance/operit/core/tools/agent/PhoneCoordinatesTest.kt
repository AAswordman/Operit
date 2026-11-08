package com.ai.assistance.operit.core.tools.agent

import org.junit.Assert.*
import org.junit.Test

class PhoneCoordinatesTest {
    @Test fun reportedDevicePointFollowsTheExistingNormalizedContract() {
        assertEquals(679 to 215, mapPhoneNormalizedPoint("[539,77]", 1260, 2800))
    }

    @Test fun searchCornerAndCenterUseIndependentDisplayAxes() {
        assertEquals(1200 to 126, mapPhoneNormalizedPoint("[953,45]", 1260, 2800))
        assertEquals(630 to 1400, mapPhoneNormalizedPoint("[500,500]", 1260, 2800))
    }

    @Test fun rotatedDisplayUsesItsCurrentDimensions() {
        assertEquals(1400 to 630, mapPhoneNormalizedPoint("[500,500]", 2800, 1260))
    }

    @Test fun outOfRangePixelsAreRejectedInsteadOfScaledAgain() {
        assertNull(mapPhoneNormalizedPoint("[1200,126]", 1260, 2800))
        assertNull(mapPhoneNormalizedPoint("[-1,0]", 1260, 2800))
        assertNull(mapPhoneNormalizedPoint("[0,1000]", 1260, 2800))
    }

    @Test fun extraOrMalformedCoordinatesDoNotDispatch() {
        for (value in listOf("[1,2,3]", "[1]", "[x,2]", "[1.2,3]", "[1,2")) {
            assertNull(mapPhoneNormalizedPoint(value, 1260, 2800))
        }
    }

    @Test fun missingScreenDimensionsAndBoundaryValuesAreExplicit() {
        assertNull(mapPhoneNormalizedPoint("[0,0]", 0, 2800))
        assertNull(mapPhoneNormalizedPoint("[0,0]", 1260, -1))
        assertEquals(0 to 0, mapPhoneNormalizedPoint("[0,0]", 1260, 2800))
        assertEquals(1258 to 2797, mapPhoneNormalizedPoint("[999,999]", 1260, 2800))
    }
}
