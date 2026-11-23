package com.lvupme.app.data.remote

import com.lvupme.app.data.local.AppSettings
import com.lvupme.app.worker.LockScreenCardWorker
import com.lvupme.app.worker.WorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CoreUtilsTest {

    @Test
    fun sanitizesModelNameAndThemeModeSafely() {
        assertEquals("gemini-2.5-flash", AppSettings.sanitizeModel("gemini-2.5-flash"))
        assertEquals("gemini-3.5-flash", AppSettings.sanitizeModel("  gemini-3.5-flash "))
        assertEquals(AppSettings.DEFAULT_GEMINI_MODEL, AppSettings.sanitizeModel("../evil?key=abc"))
        assertEquals(AppSettings.DEFAULT_GEMINI_MODEL, AppSettings.sanitizeModel("   "))

        assertEquals("DARK", AppSettings.sanitizeThemeMode("DARK"))
        assertEquals("LIGHT", AppSettings.sanitizeThemeMode("LIGHT"))
        assertEquals("SYSTEM", AppSettings.sanitizeThemeMode("INVALID_THEME"))
    }

    @Test
    fun dndHoursHandleMidnightWrapAndSameHour() {
        assertTrue(LockScreenCardWorker.isWithinDnd(23, 23, 7))
        assertTrue(LockScreenCardWorker.isWithinDnd(3, 23, 7))
        assertFalse(LockScreenCardWorker.isWithinDnd(7, 23, 7))
        assertFalse(LockScreenCardWorker.isWithinDnd(14, 23, 7))
        assertFalse(LockScreenCardWorker.isWithinDnd(12, 0, 0))
        assertTrue(LockScreenCardWorker.isWithinDnd(10, 9, 12))
    }

    @Test
    fun reminderDelaySchedulesSameDayOrNextDayAccurately() {
        val morning = LocalDateTime.of(2026, 11, 23, 8, 0)
        assertEquals(12 * 60L, WorkScheduler.computeDelayMinutesUntil(20, 0, morning))

        val night = LocalDateTime.of(2026, 11, 23, 21, 0)
        assertEquals(23 * 60L, WorkScheduler.computeDelayMinutesUntil(20, 0, night))
    }
}
