package com.andrerinas.openheadunit.phoenix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoenixLaunchTest {

    @Test fun tcpWithHostAndIntPort() =
        assertEquals(PhoenixLaunch.Request.Tcp("10.0.2.2", 5277), PhoenixLaunch.parse("tcp", "10.0.2.2", 5277))

    @Test fun tcpPortFromStringAndFallback() {
        assertEquals(PhoenixLaunch.Request.Tcp("h", 6000), PhoenixLaunch.parse("TCP", " h ", "6000"))
        assertEquals(PhoenixLaunch.Request.Tcp("h", 5277), PhoenixLaunch.parse("tcp", "h", null))
        assertEquals(PhoenixLaunch.Request.Tcp("h", 5277), PhoenixLaunch.parse("tcp", "h", 70000))
    }

    @Test fun tcpWithoutHostIsNotALaunch() = assertNull(PhoenixLaunch.parse("tcp", " ", 5277))

    @Test fun usbAndUnknown() {
        assertEquals(PhoenixLaunch.Request.Usb, PhoenixLaunch.parse("usb", null, null))
        assertNull(PhoenixLaunch.parse(null, "h", 1))
        assertNull(PhoenixLaunch.parse("bt", "h", 1))
    }

    @Test fun retriesOnlyWhenNothingInFlight() {
        assertTrue(TcpRetryPolicy.shouldAttempt(TcpRetryPolicy.Phase.IDLE))
        assertTrue(TcpRetryPolicy.shouldAttempt(TcpRetryPolicy.Phase.FAILED))
        assertFalse(TcpRetryPolicy.shouldAttempt(TcpRetryPolicy.Phase.BUSY))
    }

    @Test fun loopbackTcpLaunchDeclaresTunnel() {
        val declared = PhoenixTunnel.endpointFor(PhoenixLaunch.Request.Tcp("127.0.0.1", 5277))
        assertEquals("127.0.0.1:5277", declared)
        assertTrue(PhoenixTunnel.matches(declared, "127.0.0.1", 5277))
        assertEquals("localhost:5277", PhoenixTunnel.endpointFor(PhoenixLaunch.Request.Tcp("LocalHost", 5277)))
    }

    @Test fun tunnelMatchesOnlyTheDeclaredEndpoint() {
        val declared = PhoenixTunnel.endpointFor(PhoenixLaunch.Request.Tcp("127.0.0.1", 5277))
        assertFalse(PhoenixTunnel.matches(declared, "127.0.0.1", 5288))
        assertFalse(PhoenixTunnel.matches(declared, "127.0.0.2", 5277))
        assertFalse(PhoenixTunnel.matches(null, "127.0.0.1", 5277))
    }

    @Test fun networkAndUsbLaunchesDeclareNoTunnel() {
        assertNull(PhoenixTunnel.endpointFor(PhoenixLaunch.Request.Tcp("10.0.2.2", 5277)))
        assertNull(PhoenixTunnel.endpointFor(PhoenixLaunch.Request.Tcp("192.168.1.20", 5277)))
        assertNull(PhoenixTunnel.endpointFor(PhoenixLaunch.Request.Usb))
    }

    @Test fun audioGateMapping() {
        assertEquals(false, PhoenixAudioGate.readyFrom(PhoenixAudioGate.ACTION_AUDIO_PATH, true, false))
        assertEquals(true, PhoenixAudioGate.readyFrom(PhoenixAudioGate.ACTION_AUDIO_PATH, true, true))
        assertNull(PhoenixAudioGate.readyFrom(PhoenixAudioGate.ACTION_AUDIO_PATH, false, false))
        assertNull(PhoenixAudioGate.readyFrom("other", true, false))
        assertTrue(PhoenixAudioGate.ready)
    }
}
