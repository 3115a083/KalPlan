package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleProfileTest {
    @Test
    fun checkedInExampleProfileDecodesAndValidates() {
        val resource = requireNotNull(
            javaClass.classLoader?.getResource("profile-structured-job-request.json")
        )
        val result = ExtractionProfileCodec().decode(resource.readText())

        assertTrue(result.isValid)
    }
}
