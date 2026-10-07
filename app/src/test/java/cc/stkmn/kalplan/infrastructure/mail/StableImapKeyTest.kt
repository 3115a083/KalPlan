package cc.stkmn.kalplan.infrastructure.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class StableImapKeyTest {
    @Test
    fun roundTripsFolderNamesWithSeparatorCharacters() {
        val original = StableImapKey(
            accountId = "mail|1%",
            folderPath = "Jobs|2026%Q4",
            uidValidity = 1234L,
            uid = 9876L
        )

        assertEquals(original, StableImapKey.parse(original.encode()))
    }
}
