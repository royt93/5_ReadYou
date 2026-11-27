package com.mckimquyen.reader.ui.component.ambient

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * On a Tecno, with Polish text, the four sleep-timer chips sat in a plain Row: the "60 min" chip was squeezed to a sliver and its label ran down it one letter per line.
 * The chips scroll in a LazyRow now, like the sound-type chips above them, so a longer label (Russian "30 минут", Romanian "30 de minute") never has to wrap.
 */
class ZenSleepTimerChipsSourceTest {
    private val source: String = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        .resolve("com/mckimquyen/reader/ui/component/ambient/ZenSoundSheet.kt").readText()

    private val timerBlock = source.substringAfter("R.string.zen_sleep_timer)").substringBefore("Spacer(modifier = Modifier.height(16.dp))")

    @Test fun theSleepTimerChipsScrollInsteadOfSharingOneRow() {
        assertTrue("the chips must sit in a LazyRow", "LazyRow(" in timerBlock)
        assertTrue("and be built with items(...)", "items(listOf(0, 15, 30, 60))" in timerBlock)
        assertTrue("not in a plain Row that squeezes the last chip", "                        Row(" !in timerBlock)
    }

    @Test fun theLabelIsAPluralSoTheNounFollowsTheNumber() {
        assertTrue("pluralStringResource(R.plurals.zen_sleep_timer_min" in timerBlock)
    }
}
