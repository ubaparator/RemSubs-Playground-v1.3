package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.SubtitleCue
import com.example.model.SubtitleStyle
import com.example.ui.components.SubtitleOverlay
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleCues = listOf(
      SubtitleCue(
        id = 1,
        startTimeMs = 0L,
        endTimeMs = 5000L,
        rawText = "remsubs playground\nÖnizleme Altyazısı",
        cleanText = "remsubs playground\nÖnizleme Altyazısı"
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF10121A))) {
          SubtitleOverlay(
            activeCues = sampleCues,
            style = SubtitleStyle(),
            fontFamily = null
          )
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun test_hardsub_dialog() {
    val uiState = com.example.ui.AxiSubUiState(
      showEncodeDialog = true,
      videoTitle = "test.mp4",
      subtitles = listOf(
        SubtitleCue(id = 1, startTimeMs = 0, endTimeMs = 1000, rawText = "Test", cleanText = "Test")
      )
    )
    val encodeState = com.example.encode.EncodeState()

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.components.HardsubEncodeDialog(
          uiState = uiState,
          encodeState = encodeState,
          onDismiss = {},
          onStartEncode = {},
          onCancelEncode = {},
          onPlayEncodedVideo = {},
          onSaveToDevice = {}
        )
      }
    }
  }

  @Test
  fun test_hardsub_dialog_encoding() {
    val uiState = com.example.ui.AxiSubUiState(showEncodeDialog = true)
    val encodeState = com.example.encode.EncodeState(
      isEncoding = true,
      progress = 0.45f,
      currentFrame = 450,
      totalFrames = 1000,
      currentFps = 29.5,
      fpsToTotalFramesRatio = 0.0295,
      averageEstimatedFinishText = "00:18",
      elapsedSeconds = 15
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.components.HardsubEncodeDialog(
          uiState = uiState,
          encodeState = encodeState,
          onDismiss = {},
          onStartEncode = {},
          onCancelEncode = {},
          onPlayEncodedVideo = {},
          onSaveToDevice = {}
        )
      }
    }
  }
}


