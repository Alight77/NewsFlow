package io.github.alight77.news.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.repository.FavoriteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val favoriteArticleIds: Set<String> = emptySet(),
    val favorites: List<Article> = emptyList(),
    val pendingArticleIds: Set<String> = emptySet(),
)

sealed interface FavoriteEvent {
    data class MutationFailed(val articleId: String) : FavoriteEvent
}

class FavoritesViewModel(private val repository: FavoriteRepository) : ViewModel() {
    private val pendingArticleIds = MutableStateFlow<Set<String>>(emptySet())
    private val _events = MutableSharedFlow<FavoriteEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<FavoritesUiState> = combine(
        repository.observeFavoriteArticleIds(),
        repository.observeFavorites(),
        pendingArticleIds,
    ) { favoriteArticleIds, favorites, pendingIds ->
        FavoritesUiState(favoriteArticleIds, favorites, pendingIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FavoritesUiState(),
    )

    fun toggle(article: Article) {
        if (article.id in pendingArticleIds.value) return
        pendingArticleIds.value += article.id
        viewModelScope.launch {
            try {
                if (repository.isFavorite(article.id)) repository.remove(article.id)
                else repository.save(article)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _events.emit(FavoriteEvent.MutationFailed(article.id))
            } finally {
                pendingArticleIds.value -= article.id
            }
        }
    }
}
