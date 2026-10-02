package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("RemSubs Playground", appName)
  }

  @Test
  fun `test openEncodeDialog`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.AxiSubViewModel(context)
    viewModel.openEncodeDialog()
    assertEquals(true, viewModel.uiState.value.showEncodeDialog)
  }

  @Test
  fun `test undo redo stack and cue manipulation`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.AxiSubViewModel(context)
    val initialCount = viewModel.uiState.value.subtitles.size

    // Add cue
    viewModel.addNewCueAtCurrentPosition()
    assertEquals(initialCount + 1, viewModel.uiState.value.subtitles.size)
    assertEquals(true, viewModel.uiState.value.canUndo)

    // Undo addition
    viewModel.undo()
    assertEquals(initialCount, viewModel.uiState.value.subtitles.size)
    assertEquals(true, viewModel.uiState.value.canRedo)

    // Redo addition
    viewModel.redo()
    assertEquals(initialCount + 1, viewModel.uiState.value.subtitles.size)

    // Test duplicate
    val lastCue = viewModel.uiState.value.subtitles.last()
    viewModel.duplicateCue(lastCue)
    assertEquals(initialCount + 2, viewModel.uiState.value.subtitles.size)
  }
}

