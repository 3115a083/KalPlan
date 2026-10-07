package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleProfileTest {
    @Test
    fun checkedInExampleProfileDecodesAndValidates() {
        val file = File("samples/profile-structured-job-request.json")
        val result = ExtractionProfileCodec().decode(file.readText())

        assertTrue(result.isValid)
    }
}
