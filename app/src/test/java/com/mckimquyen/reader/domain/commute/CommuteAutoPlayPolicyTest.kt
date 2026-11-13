package com.mckimquyen.reader.domain.commute

import org.junit.Assert.assertEquals
import org.junit.Test

class CommuteAutoPlayPolicyTest {

    private val hour = 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val today = CommuteAutoPlayPolicy.epochDayUtc(now)

    private fun input(
        switchOn: Boolean = true,
        episodeDateMillis: Long? = now - hour,
        episodePlayed: Boolean = false,
        lastAutoPlayEpochDay: Long? = null,
        isAudioDevice: Boolean = true,
        otherAudioPlaying: Boolean = false,
    ) = AutoPlayInput(switchOn, episodeDateMillis, episodePlayed, now, lastAutoPlayEpochDay, isAudioDevice, otherAudioPlaying)

    private fun decide(i: AutoPlayInput) = CommuteAutoPlayPolicy.decide(i)

    @Test fun constants_areTheSpecifiedOnes() {
        assertEquals(2 * hour, CommuteAutoPlayPolicy.AUTO_PLAY_WINDOW_MS)
        assertEquals(86_400_000L, CommuteAutoPlayPolicy.MILLIS_PER_DAY)
    }

    @Test fun everythingRight_playsNow() = assertEquals(AutoPlayDecision.PlayNow, decide(input()))

    @Test fun switchOff_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.SWITCH_OFF), decide(input(switchOn = false)))

    @Test fun noEpisode_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.NO_EPISODE), decide(input(episodeDateMillis = null)))

    @Test fun alreadyPlayedEpisode_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.EPISODE_PLAYED), decide(input(episodePlayed = true)))

    @Test fun exactlyTwoHoursOld_stillInTheWindow() =
        assertEquals(AutoPlayDecision.PlayNow, decide(input(episodeDateMillis = now - 2 * hour)))

    @Test fun oneMillisecondOverTwoHours_isTooOld() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.EPISODE_TOO_OLD), decide(input(episodeDateMillis = now - 2 * hour - 1)))

    @Test fun anEpisodeDatedInTheFuture_isNotTooOld() =
        assertEquals(AutoPlayDecision.PlayNow, decide(input(episodeDateMillis = now + hour)))

    @Test fun alreadyAutoPlayedToday_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.ALREADY_AUTO_PLAYED_TODAY), decide(input(lastAutoPlayEpochDay = today)))

    @Test fun autoPlayedYesterday_doesNotBlockToday() =
        assertEquals(AutoPlayDecision.PlayNow, decide(input(lastAutoPlayEpochDay = today - 1)))

    @Test fun notAnAudioDevice_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.NOT_AN_AUDIO_DEVICE), decide(input(isAudioDevice = false)))

    @Test fun otherAudioPlaying_offersTheButton_insteadOfInterrupting() =
        assertEquals(AutoPlayDecision.OfferPlayButton, decide(input(otherAudioPlaying = true)))

    @Test fun theUtcDayRollsOverAtMidnightUtc() {
        val justBefore = 20_000L * CommuteAutoPlayPolicy.MILLIS_PER_DAY - 1
        val justAfter = 20_000L * CommuteAutoPlayPolicy.MILLIS_PER_DAY
        assertEquals(19_999L, CommuteAutoPlayPolicy.epochDayUtc(justBefore))
        assertEquals(20_000L, CommuteAutoPlayPolicy.epochDayUtc(justAfter))
    }

    @Test fun epochDayUtc_ofNegativeMillis_usesFloorDivision() =
        assertEquals(-1L, CommuteAutoPlayPolicy.epochDayUtc(-1L))

    // The order matters: the cheaper, more fundamental reasons are reported first.
    @Test fun switchOff_winsOverEverythingElse() =
        assertEquals(
            AutoPlayDecision.Skip(SkipReason.SWITCH_OFF),
            decide(input(switchOn = false, episodeDateMillis = null, isAudioDevice = false, lastAutoPlayEpochDay = today)),
        )

    @Test fun anEpisodeThatIsBothHeardAndTooOld_isReportedAsHeard() =
        assertEquals(
            AutoPlayDecision.Skip(SkipReason.EPISODE_PLAYED),
            decide(input(episodePlayed = true, episodeDateMillis = now - 3 * hour)),
        )

    @Test fun anOldEpisode_isReportedBeforeAHeadsetThatIsNotAudio() =
        assertEquals(
            AutoPlayDecision.Skip(SkipReason.EPISODE_TOO_OLD),
            decide(input(episodeDateMillis = now - 3 * hour, isAudioDevice = false)),
        )
}
