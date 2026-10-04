package vn.homepanel

import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.auth.authReturnPage

class AuthReturnPageTest {
    @Test fun browserReturnLinkSupportsEveryDistributionAndLegacyBeta() {
        for (scheme in listOf("homepanelmr", "homepanelmr-community", "homepanelmr-store",
            "homepanelmr-community-debug", "homepanelmr-store-debug")) {
            val page = authReturnPage("Done", "Return to the app", "Open", scheme)
            assertTrue(page.contains("href=\"$scheme://open\""))
        }
    }

    @Test fun untrustedSchemesCannotBecomeBrowserLinks() {
        for (scheme in listOf("javascript", "https", "homepanelmr\" onclick=\"alert(1)", "")) {
            assertThrows(IllegalArgumentException::class.java) { authReturnPage("Done", "", "Open", scheme) }
        }
    }

    @Test fun displayTextIsEscapedBeforeEmbeddingInHtml() {
        val page = authReturnPage("<script>", "A&B", "\"Open\"", "homepanelmr-community")
        assertFalse(page.contains("<script>"))
        assertTrue(page.contains("&lt;script&gt;"))
        assertTrue(page.contains("A&amp;B"))
        assertTrue(page.contains("&quot;Open&quot;"))
    }
}
