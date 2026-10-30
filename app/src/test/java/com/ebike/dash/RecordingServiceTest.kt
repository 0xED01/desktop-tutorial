package com.ebike.dash

import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingServiceTest {
    @Test
    fun showsOngoingRecordingNotification() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), RecordingService::class.java)
            .putExtra("text", "本次 3.2 km · 点击返回 Driver")
        val service = Robolectric.buildService(RecordingService::class.java, intent).create().startCommand(0, 1).get()
        val n = shadowOf(service).lastForegroundNotification
        assertNotNull(n)
        assertEquals("Driver 正在记录足迹", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("本次 3.2 km · 点击返回 Driver", n.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
