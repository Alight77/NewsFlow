package io.github.alight77.news.data.remote

data class GNewsResponseDto(
    val totalArticles: Int? = null,
    val articles: List<GNewsArticleDto>? = null,
)

data class GNewsArticleDto(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val content: String? = null,
    val url: String? = null,
    val image: String? = null,
    val publishedAt: String? = null,
    val lang: String? = null,
    val source: GNewsSourceDto? = null,
)

data class GNewsSourceDto(
    val name: String? = null,
    val url: String? = null,
)
