package com.mckimquyen.reader.domain.model.rpg

import org.junit.Assert.assertEquals
import org.junit.Test

class BrainRpgRewardTypeConvertersTest {
    private val converters = BrainRpgRewardTypeConverters()

    @Test
    fun everyRewardType_roundTripsThroughRoomStorage() {
        BrainRpgRewardType.entries.forEach { type ->
            assertEquals(type.name, converters.toStorage(type))
            assertEquals(type, converters.fromStorage(type.name))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownStoredRewardType_failsClearlyRatherThanMappingToAnotherReward() {
        converters.fromStorage("UNKNOWN_REWARD")
    }
}
