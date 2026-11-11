package com.bettercallsoul.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {
    @get:Rule
    val rule = createComposeRule()

    private fun zoom(): Pair<Float, Offset> {
        rule.waitForIdle()
        return rule.onNodeWithContentDescription(NAME).fetchSemanticsNode().config[PhotoZoom]
    }

    @Test
    fun zoomsMovesAndCloses() {
        var closed by mutableStateOf(false)
        rule.setContent { if (!closed) PhotoViewer("file:///nothing.jpg", NAME) { closed = true } }
        val photo = rule.onNodeWithContentDescription(NAME)
        assertEquals(1f, zoom().first)

        photo.performTouchInput { doubleClick(center) }
        assertEquals("double tap zooms in", 2.5f, zoom().first)

        photo.performTouchInput { doubleClick(center) }
        assertEquals("a second double tap zooms back out", 1f to Offset.Zero, zoom())

        photo.performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(300f, 0f), center + Offset(50f, 0f), center + Offset(300f, 0f))
        }
        val (pinched, at) = zoom()
        assertTrue("two fingers zoom in: $pinched", pinched > 3f)

        photo.performTouchInput {
            down(center)
            moveBy(Offset(-150f, -100f))
            moveBy(Offset(-150f, -100f))
            up()
        }
        assertNotEquals("one finger moves the zoomed photo", at, zoom().second)

        // A tap on the photo leaves it open; the close button closes it.
        photo.performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertTrue("a tap on the photo keeps it open", !closed)
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()
        assertTrue("the close button closes it", closed)
    }

    private companion object {
        const val NAME = "Test photo"
    }
}
