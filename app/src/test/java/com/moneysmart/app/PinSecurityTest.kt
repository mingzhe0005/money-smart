package com.moneysmart.app

import com.moneysmart.app.security.AppLockManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {

    @Test
    fun saltedSha256PinVerificationWorks() {
        val salt1 = AppLockManager.generateSalt()
        val salt2 = AppLockManager.generateSalt()
        assertNotEquals(salt1, salt2)

        val hash = AppLockManager.hashPin("4829", salt1)
        assertTrue(AppLockManager.verifyPin("4829", hash, salt1))
        assertFalse(AppLockManager.verifyPin("4820", hash, salt1))
        assertFalse(AppLockManager.verifyPin("4829", hash, salt2))
        assertFalse(AppLockManager.verifyPin("", hash, salt1))
    }
}
