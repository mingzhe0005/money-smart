package com.moneysmart.app

import com.moneysmart.app.notification.NotificationProcessor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRetryLogicTest {

    @Test
    fun `isLikelyOfflineError detects DNS and background network errors cleanly`() {
        assertTrue(
            NotificationProcessor.isLikelyOfflineError(
                "Network error: Unable to resolve host \"generativelanguage.googleapis.com\": No address associated with hostname"
            )
        )
        assertTrue(NotificationProcessor.isLikelyOfflineError("Network error: timeout"))
        assertFalse(NotificationProcessor.isLikelyOfflineError("Gemini free-tier rate limit reached (429)"))
        assertFalse(NotificationProcessor.isLikelyOfflineError("Invalid API key or request (403)"))
        assertFalse(NotificationProcessor.isLikelyOfflineError(null))
    }
}
