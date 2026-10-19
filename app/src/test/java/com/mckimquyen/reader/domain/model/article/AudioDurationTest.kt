package com.mckimquyen.reader.domain.model.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioDurationTest {
    @Test fun seconds() = assertEquals(45, AudioDuration.parse("45"))
    @Test fun minutesSeconds() = assertEquals(185, AudioDuration.parse("3:05"))
    @Test fun hoursMinutesSeconds() = assertEquals(3723, AudioDuration.parse("01:02:03"))
    @Test fun trimmed() = assertEquals(750, AudioDuration.parse(" 12:30 "))
    @Test fun nullInput() = assertNull(AudioDuration.parse(null))
    @Test fun empty() = assertNull(AudioDuration.parse(""))
    @Test fun garbage() = assertNull(AudioDuration.parse("abc"))
    @Test fun tooManyParts() = assertNull(AudioDuration.parse("1:2:3:4"))
    @Test fun negative() = assertNull(AudioDuration.parse("-5"))
    @Test fun zero() = assertEquals(0, AudioDuration.parse("00:00"))
    @Test fun plainLargeSeconds() = assertEquals(1913, AudioDuration.parse("1913"))
    @Test fun minutesOverSixty() = assertEquals(5400, AudioDuration.parse("90:00"))
}
