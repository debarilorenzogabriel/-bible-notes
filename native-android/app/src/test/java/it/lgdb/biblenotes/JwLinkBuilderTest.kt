package it.lgdb.biblenotes

import it.lgdb.biblenotes.domain.JwLinkBuilder
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class JwLinkBuilderTest {

    @Test
    fun `buildBibleCode handles single verse, range, and chapter only`() {
        assertEquals("01001001", JwLinkBuilder.buildBibleCode(1, 1, null))
        assertEquals("43003016", JwLinkBuilder.buildBibleCode(43, 3, "16"))
        assertEquals("19023001-19023004", JwLinkBuilder.buildBibleCode(19, 23, "1-4"))
    }

    @Test
    fun `buildJwLibraryUrl and buildJwOrgUrl include expected parameters`() {
        val ref = BibleRefSpan(
            start = 0,
            end = 13,
            bookNumber = 43,
            bookName = "Giovanni",
            chapter = 17,
            verseSpec = "3"
        )
        assertEquals(
            "jwlibrary://finder?srcid=jwlshare&wtlocale=I&prefer=lang&pub=nwtsty&bible=43017003",
            JwLinkBuilder.buildJwLibraryUrl(ref, AppLanguage.IT)
        )
        assertEquals(
            "https://www.jw.org/finder?srcid=jwlshare&wtlocale=E&prefer=lang&pub=nwtsty&bible=43017003",
            JwLinkBuilder.buildJwOrgUrl(ref, AppLanguage.EN)
        )
    }
}
