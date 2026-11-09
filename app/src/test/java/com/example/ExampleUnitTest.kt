package com.example

import com.example.core.DataCenter
import com.example.core.TELEGRAM_DCS
import com.example.core.TelegramCoreEngine
import com.example.core.TransportProtocol
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDataCentersConfiguration() {
        assertEquals(5, TELEGRAM_DCS.size)
        val dc2 = TELEGRAM_DCS.first { it.id == 2 }
        assertEquals("149.154.167.50", dc2.ip)
        assertEquals(443, dc2.port)
        assertEquals("149.154.167.50:443", dc2.endpoint)
    }

    @Test
    fun testTransportProtocols() {
        assertEquals("0xEF", TransportProtocol.ABRIDGED.markerHex)
        assertEquals("0xEEEEEEEE", TransportProtocol.INTERMEDIATE.markerHex)
        assertEquals("0xDDDDDDDD", TransportProtocol.PADDED_INTERMEDIATE.markerHex)
    }

    @Test
    fun testAuthKeyGenerationAndZeroize() {
        val engine = TelegramCoreEngine("/tmp")
        val session = engine.getActiveKeySession()
        assertNotNull(session)
        assertFalse(session.isZeroized)
        assertTrue(session.keyId.startsWith("0x"))

        // Test Zeroize
        val zeroized = engine.zeroizeActiveKey()
        assertTrue(zeroized.isZeroized)
        assertTrue(zeroized.rawKeyHex.contains("ALL ZEROS"))
    }

    @Test
    fun testPanicBoundarySafety() {
        val engine = TelegramCoreEngine("/tmp")
        val resultOob = engine.testPanicBoundary("oob")
        assertTrue(resultOob.caughtPanic)
        assertTrue(resultOob.jvmSurvived)
        assertTrue(resultOob.errorReason.contains("bounds"))

        val resultUnwrap = engine.testPanicBoundary("unwrap")
        assertTrue(resultUnwrap.caughtPanic)
        assertTrue(resultUnwrap.jvmSurvived)
    }

    @Test
    fun testPushNotificationProcessing() {
        val engine = TelegramCoreEngine("/tmp")
        val result = engine.handleBackgroundPush("Тестовое уведомление из FCM")
        assertEquals(777L, result.chatId)
        assertTrue(result.previewText.contains("Тестовое уведомление"))
        assertTrue(result.hasUnread)
    }
}
