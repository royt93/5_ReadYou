package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrProfileEventTest {
    private val author = NostrTestEvents.AUTHOR
    private fun ev(json: String) = requireNotNull(NostrEvent.parse(json)) { "fixture must parse" }

    @Test fun aSignedProfileByTheAuthor_isValid() = assertTrue(ev(NostrTestEvents.PROFILE).isValidProfile(author))

    @Test fun aProfile_isNotAValidNote() = assertFalse("a profile must never pass the note check", ev(NostrTestEvents.PROFILE).isValid(author))

    @Test fun aNote_isNotAValidProfile() = assertFalse(ev(NostrTestEvents.PLAIN).isValidProfile(author))

    @Test fun aNote_stillPassesTheNoteCheck() = assertTrue(ev(NostrTestEvents.PLAIN).isValid(author))

    @Test fun aProfileByAnotherAuthor_isRejected() {
        assertFalse(ev(NostrTestEvents.PROFILE_STRANGER).isValidProfile(author))
        assertTrue(ev(NostrTestEvents.PROFILE_STRANGER).isValidProfile(NostrTestEvents.STRANGER))
    }

    @Test fun aProfileWithTamperedContent_isRejected() {
        val forged = NostrTestEvents.PROFILE.replace("Alice Liddell", "Someone Else")
        assertFalse(ev(forged).isValidProfile(author))
    }

    @Test fun aProfileWithABorrowedSignature_isRejected() {
        val e = ev(NostrTestEvents.PROFILE)
        val borrowed = ev(NostrTestEvents.PROFILE_NEWER).sig
        assertFalse(ev(NostrTestEvents.PROFILE.replace(e.sig, borrowed)).isValidProfile(author))
    }

    @Test fun aProfileWithAChangedTimestamp_isRejected() =
        assertFalse(ev(NostrTestEvents.PROFILE.replace("1700000900", "1700000901")).isValidProfile(author))

    @Test fun anUppercaseHexProfile_isRejected() {
        val e = ev(NostrTestEvents.PROFILE)
        assertFalse(ev(NostrTestEvents.PROFILE.replace(e.id, e.id.uppercase())).isValidProfile(author))
        assertFalse(ev(NostrTestEvents.PROFILE).isValidProfile(author.uppercase()))
    }

    @Test fun theProfileKindConstant_isZero() = assertEquals(0, NostrEvent.PROFILE_KIND)

    @Test fun otherKinds_areNotProfiles() = assertFalse(ev(NostrTestEvents.KIND7).isValidProfile(author))
}
