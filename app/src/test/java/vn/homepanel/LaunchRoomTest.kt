package vn.homepanel

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRoomTest {
    @Test fun onlyLauncherOpenGoesStraightToRoom() {
        assertTrue(opensRoomOnLaunch(Intent.ACTION_MAIN, setOf(Intent.CATEGORY_LAUNCHER)))
        assertFalse("Return to window", opensRoomOnLaunch(null, null))
        assertFalse("Sign-in callback", opensRoomOnLaunch(Intent.ACTION_VIEW, setOf(Intent.CATEGORY_BROWSABLE)))
        assertFalse(opensRoomOnLaunch(Intent.ACTION_MAIN, null))
    }
}
