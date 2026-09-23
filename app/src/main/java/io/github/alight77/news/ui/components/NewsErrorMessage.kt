package io.github.alight77.news.ui.components

import androidx.annotation.StringRes
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.NewsError

@StringRes
fun NewsError.messageRes(): Int = when (this) {
    NewsError.CONNECTION -> R.string.error_connection
    NewsError.TIMEOUT -> R.string.error_timeout
    NewsError.INVALID_REQUEST -> R.string.error_invalid_request
    NewsError.AUTHENTICATION -> R.string.error_authentication
    NewsError.QUOTA_EXCEEDED -> R.string.error_quota
    NewsError.RATE_LIMITED -> R.string.error_rate_limit
    NewsError.INVALID_DATA -> R.string.error_invalid_data
    NewsError.UNKNOWN -> R.string.error_unknown
}
