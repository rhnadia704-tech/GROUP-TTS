package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VoiceSlot
import com.example.data.repository.ScriptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Group TTS", appName)
    }

    @Test
    fun `test script parser with multiple voice slots`() {
        val voices = VoiceSlot.defaultVoices()
        val script = """
            Alice: Bonjour Bob !
            Bob: Salut Alice, comment vas-tu ?
            Narrateur: Les deux personnages se saluèrent chaleureusement.
        """.trimIndent()

        val lines = ScriptParser.parse(script, voices)
        assertEquals(3, lines.size)
        assertEquals("Alice", lines[0].speakerName)
        assertEquals("Bonjour Bob !", lines[0].text)
        assertEquals("Bob", lines[1].speakerName)
        assertEquals("Narrateur", lines[2].speakerName)
    }

    @Test
    fun `test script parser bracket tags`() {
        val voices = VoiceSlot.defaultVoices()
        val script = """
            [Voice 1] Première phrase
            [Voice 2] Deuxième phrase
        """.trimIndent()

        val lines = ScriptParser.parse(script, voices)
        assertEquals(2, lines.size)
        assertEquals(1, lines[0].voiceSlotId)
        assertEquals("Première phrase", lines[0].text)
        assertEquals(2, lines[1].voiceSlotId)
        assertEquals("Deuxième phrase", lines[1].text)
    }
}
