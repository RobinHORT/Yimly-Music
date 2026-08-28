import re

with open('app/src/main/java/com/example/data/models/MusicModels.kt', 'r') as f:
    content = f.read()

content = content.replace(
'''    @Json(name = "cover_path") val rawCoverPathSnake: String? = null,
    @Json(name = "cover") val rawCover: String? = null,''',
'''    @Json(name = "cover_path") val rawCoverPathSnake: String? = null,
    @Json(name = "cover") val rawCover: String? = null,
    @Json(name = "coverImageUrl") val rawCoverImageUrl: String? = null,'''
)

content = content.replace(
'''    val coverUrl: String?
        get() = (rawCoverUrl
            ?: rawCoverUrlSnake
            ?: rawCoverImage
            ?: rawCoverImageSnake
            ?: rawCoverPath
            ?: rawCoverPathSnake
            ?: rawCover
            ?: rawArtworkUrl)''',
'''    val coverUrl: String?
        get() {
            val bestUrl = rawCoverImageUrl 
                ?: rawCoverUrl
                ?: rawCoverUrlSnake
                ?: rawCoverImage
                ?: rawCoverImageSnake
                ?: rawCoverPath
                ?: rawCoverPathSnake
                ?: rawCover
                ?: rawArtworkUrl
            if (bestUrl?.startsWith("/api") == true) {
                return "https://yimly.robinhort.link$bestUrl"
            }
            return bestUrl
        }'''
)

with open('app/src/main/java/com/example/data/models/MusicModels.kt', 'w') as f:
    f.write(content)
