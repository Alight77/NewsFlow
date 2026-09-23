package io.github.alight77.news.ui.article

import androidx.lifecycle.ViewModel
import io.github.alight77.news.domain.model.Article

/** Keeps opened article snapshots for detail routes during the current app session. */
class ArticleSessionViewModel : ViewModel() {
    private val articles = mutableMapOf<String, Article>()
    private var openedArticle: Article? = null

    fun open(article: Article) {
        articles[article.id] = article
        openedArticle = article
    }

    fun articleForDetail(id: String): Article? =
        openedArticle?.takeIf { it.id == id } ?: articles[id]
}
