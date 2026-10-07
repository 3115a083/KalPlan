package cc.stkmn.kalplan.domain.port

data class AiExtractionInput(
    val subject: String,
    val normalizedBody: String
)

data class AiExtractionResult(
    val json: String,
    val providerId: String,
    val localOnly: Boolean
)

interface AiExtractionProvider {
    val id: String
    suspend fun extract(input: AiExtractionInput): AiExtractionResult
}
