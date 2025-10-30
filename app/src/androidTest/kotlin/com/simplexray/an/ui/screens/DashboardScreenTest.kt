package com.simplexray.an.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.simplexray.an.viewmodel.MainViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboardScreen_displaysTrafficCard() {
        val mockViewModel = mockk<MainViewModel>(relaxed = true)
        val mockCoreStats = MainViewModel.CoreStats(
            uplink = 1024 * 1024,  // 1 MB
            downlink = 2048 * 1024,  // 2 MB
            uptimeSeconds = 3600,  // 1 hour
            numGoroutine = 10,
            alloc = 5 * 1024 * 1024,  // 5 MB
            totalAlloc = 10 * 1024 * 1024,  // 10 MB
            sys = 8 * 1024 * 1024,  // 8 MB
            mallocs = 1000,
            frees = 500,
            numGC = 5
        )
        
        every { mockViewModel.coreStatsState } returns MutableStateFlow(mockCoreStats)
        every { mockViewModel.updateCoreStats() } returns Unit

        composeTestRule.setContent {
            DashboardScreen(mainViewModel = mockViewModel)
        }

        composeTestRule.onNodeWithText("Traffic").assertIsDisplayed()
    }

    @Test
    fun dashboardScreen_displaysStatsCard() {
        val mockViewModel = mockk<MainViewModel>(relaxed = true)
        val mockCoreStats = MainViewModel.CoreStats()
        
        every { mockViewModel.coreStatsState } returns MutableStateFlow(mockCoreStats)
        every { mockViewModel.updateCoreStats() } returns Unit

        composeTestRule.setContent {
            DashboardScreen(mainViewModel = mockViewModel)
        }

        composeTestRule.onNodeWithText("Stats").assertIsDisplayed()
    }
}
