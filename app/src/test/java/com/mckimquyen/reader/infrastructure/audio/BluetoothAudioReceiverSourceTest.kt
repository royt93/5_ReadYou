package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BluetoothAudioReceiverSourceTest {

    private fun code(path: String) = File(path).readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val receiver = code("src/main/java/com/mckimquyen/reader/infrastructure/audio/BluetoothAudioReceiver.kt")
    private val handler = code("src/main/java/com/mckimquyen/reader/infrastructure/audio/HeadsetAutoPlayHandler.kt")
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val receiverTag = manifest.substringAfter("android:name=\".infrastructure.audio.BluetoothAudioReceiver\"").substringBefore("</receiver>")

    @Test fun theReceiverIsDeclared_notExported_andListensOnlyForAclConnected() {
        assertTrue(manifest.contains(".infrastructure.audio.BluetoothAudioReceiver"))
        assertTrue(receiverTag.contains("android:exported=\"false\""))
        assertTrue(receiverTag.contains("android.bluetooth.device.action.ACL_CONNECTED"))
        assertEquals(1, Regex("<action ").findAll(receiverTag).count())
    }

    @Test fun theManifestAsksForNearbyDevices_onlyWhereTheOsRequiresIt() {
        assertTrue(Regex("""BLUETOOTH_CONNECT"[^>]*/>""").containsMatchIn(manifest))
        assertEquals("declared exactly once", 1, Regex("""android.permission.BLUETOOTH_CONNECT""").findAll(manifest).count())
    }

    @Test fun noBroaderBluetoothOrLocationPermissionIsAdded() {
        listOf("BLUETOOTH_SCAN", "BLUETOOTH_ADMIN", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "RECEIVE_BOOT_COMPLETED", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM")
            .forEach { assertFalse("must not add $it", manifest.contains(it)) }
    }

    @Test fun theReceiverFinishesItsPendingResult_inAFinally_andCancelsItsScope() {
        assertTrue(receiver.contains("goAsync()"))
        assertTrue(receiver.contains("finally"))
        assertTrue(receiver.substringAfter("finally").contains("finish()"))
        assertTrue(receiver.contains("cancel()"))
    }

    @Test fun theReceiverNeverBlocksTheMainThread() {
        assertFalse(receiver.contains("runBlocking"))
        assertTrue(receiver.contains("Dispatchers.IO"))
    }

    @Test fun onlyAudioDevicesAreAccepted() {
        assertTrue(receiver.contains("BluetoothClass.Device.Major.AUDIO_VIDEO") || receiver.contains("BluetoothClass.Service.AUDIO"))
    }

    @Test fun theDecisionLivesInThePolicy_notInTheReceiver() {
        assertTrue(handler.contains("CommuteAutoPlayPolicy.decide("))
        assertFalse(receiver.contains("CommuteAutoPlayPolicy"))
    }

    @Test fun theHandlerSavesTheDayOnlyAfterAStartThatSucceeded() {
        val body = handler.substringAfter("AutoPlayDecision.PlayNow ->").substringBefore("catch (e: CancellationException)")
        assertTrue(body.contains("startPlayback()") && body.contains("saveAutoPlayEpochDay("))
        assertTrue(body.indexOf("startPlayback()") < body.indexOf("saveAutoPlayEpochDay("))
    }

    @Test fun theHandlerNeverSwallowsCancellation() {
        assertTrue(handler.contains("catch (e: CancellationException)"))
        assertTrue(handler.indexOf("catch (e: CancellationException)") < handler.indexOf("catch (e: Exception)"))
    }

    @Test fun nothingInThisFeatureTouchesTheNetworkGeminiOrAds() {
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "AdManager", "Rewarded", "http").forEach {
            assertFalse("receiver must not mention '$it'", receiver.contains(it, ignoreCase = true))
            assertFalse("handler must not mention '$it'", handler.contains(it, ignoreCase = true))
        }
    }
}
