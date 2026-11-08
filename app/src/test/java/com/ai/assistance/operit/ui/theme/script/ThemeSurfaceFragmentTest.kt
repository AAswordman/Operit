package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSurfaceFragmentTest {
    private val density = Density(2f)
    private val size = Size(300f, 100f)
    private val fragments = listOf(
        ThemeSurfaceFragment.Whole,
        ThemeSurfaceFragment(first = true, last = false),
        ThemeSurfaceFragment(first = false, last = false),
        ThemeSurfaceFragment(first = false, last = true),
    )

    private fun corners(shape: CornerBasedShape) = listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart)
        .map { it.toPx(size, density) }

    @Test
    fun onlyTheOuterEndsKeepTheirOwnAsymmetricCorners() {
        for (shape in listOf(
            CutCornerShape(6.dp, 10.dp, 14.dp, 18.dp),
            RoundedCornerShape(6.dp, 10.dp, 14.dp, 18.dp),
        )) {
            assertEquals(listOf(12f, 20f, 0f, 0f), corners(fragments[1].shape(shape)))
            assertEquals(listOf(0f, 0f, 0f, 0f), corners(fragments[2].shape(shape)))
            assertEquals(listOf(0f, 0f, 28f, 36f), corners(fragments[3].shape(shape)))
            assertEquals(shape.javaClass, fragments[1].shape(shape).javaClass)
        }
    }

    @Test
    fun circleFragmentsKeepPercentCornersAndTheTypeUsedByGlass() {
        val first = fragments[1].shape(CircleShape)
        val last = fragments[3].shape(CircleShape)
        assertTrue(first is RoundedCornerShape)
        assertTrue(last is RoundedCornerShape)
        assertEquals(listOf(50f, 50f, 0f, 0f), corners(first))
        assertEquals(listOf(0f, 0f, 50f, 50f), corners(last))
    }

    @Test
    fun wholeSurfacesKeepTheirOriginalShapeInstance() {
        val shape = CutCornerShape(12.dp)
        assertSame(shape, ThemeSurfaceFragment.Whole.shape(shape))
        assertEquals(listOf(24f, 24f, 24f, 24f), corners(shape))
    }

    @Test
    fun nineSliceFragmentsShareTheCenterWithoutRepeatingEitherCap() {
        val axes = fragments.map { themeNineSliceAxis(100, 12f, 18f, 400f, 2f, it) }
        assertEquals(listOf(0, 12, 82, 100), axes[0].source)
        assertEquals(listOf(0, 12, 82, 82), axes[1].source)
        assertEquals(listOf(12, 12, 82, 82), axes[2].source)
        assertEquals(listOf(12, 12, 82, 100), axes[3].source)
        assertEquals(listOf(0f, 24f, 400f, 400f), axes[1].destination)
        assertEquals(listOf(0f, 0f, 400f, 400f), axes[2].destination)
        assertEquals(listOf(0f, 0f, 364f, 400f), axes[3].destination)
    }

    @Test
    fun veryShortFragmentsKeepCapsInsideTheDestinationAtEveryDensity() {
        for (fragment in fragments) for (height in listOf(1f, 2f, 16f, 400f)) for (density in listOf(1f, 2f, 2.75f)) {
            val axis = themeNineSliceAxis(100, 12f, 18f, height, density, fragment)
            assertTrue(axis.source.all { it in 0..100 })
            assertTrue(axis.source.zipWithNext().all { (a, b) -> a <= b })
            assertTrue(axis.destination.all { it >= -0.0001f && it <= height + 0.0001f })
            assertTrue(axis.destination.zipWithNext().all { (a, b) -> a <= b + 0.0001f })
            assertEquals(0f, axis.destination.first(), 0f)
            assertEquals(height, axis.destination.last(), 0f)
        }
    }

    @Test
    fun wholeNineSliceKeepsTheOriginalDensityScaledLayout() {
        val axis = themeNineSliceAxis(100, 12f, 18f, 400f, 2f, ThemeSurfaceFragment.Whole)
        assertEquals(listOf(0f, 24f, 364f, 400f), axis.destination)
    }
}