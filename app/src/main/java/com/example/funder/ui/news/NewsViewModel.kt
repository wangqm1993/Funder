package com.example.funder.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.remote.NewsDto
import com.example.funder.data.repository.FundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsUiState(
    val news: List<NewsDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: FundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    init {
        loadNews()
    }

    fun refresh() {
        _uiState.update { it.copy(currentPage = 1, hasMore = true) }
        loadNews(isRefresh = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        loadNews(isLoadMore = true)
    }

    private fun loadNews(isRefresh: Boolean = false, isLoadMore: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !isLoadMore,
                    isRefreshing = isRefresh
                )
            }

            try {
                val page = if (isRefresh) 1 else _uiState.value.currentPage
                val newsList = repository.getNews(page, pageSize = 20)

                _uiState.update {
                    val updatedList = if (isRefresh || !isLoadMore) {
                        // 将第1页的前5条新闻标记为热门
                        newsList.mapIndexed { index, news ->
                            news.copy(isHot = page == 1 && index < 5)
                        }
                    } else {
                        it.news + newsList
                    }

                    it.copy(
                        news = updatedList,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        hasMore = newsList.isNotEmpty(),
                        currentPage = page
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message
                    )
                }
            }
        }
    }
}
