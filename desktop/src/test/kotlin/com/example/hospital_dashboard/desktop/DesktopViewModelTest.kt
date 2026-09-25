package com.example.hospital_dashboard.desktop

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DesktopViewModelTest {

    @Test
    fun testViewModelInitializationAndSettings() {
        val tempDir = File.createTempFile("vm_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val vm = DesktopViewModel(tempDir)
            assertEquals(UiState.NoData, vm.uiState.value)
            assertFalse(vm.isDarkMode.value)
            assertEquals(0, vm.fontScaleLevel.value)

            vm.setDarkMode(true)
            assertTrue(vm.isDarkMode.value)

            vm.setFontScaleLevel(3)
            assertEquals(3, vm.fontScaleLevel.value)

            // Recreate VM to test persistence in settings.json
            val vm2 = DesktopViewModel(tempDir)
            assertTrue(vm2.isDarkMode.value)
            assertEquals(3, vm2.fontScaleLevel.value)

            // Test resolveDefaultYears
            val years = listOf("113", "114", "115")
            val defaultYears = DesktopViewModel.resolveDefaultYears(years)
            assertEquals(listOf("114", "115"), defaultYears)
            assertFalse(defaultYears.contains("113"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
