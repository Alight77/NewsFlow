package io.github.alight77.news.domain.model

enum class NewsError {
    CONNECTION,
    AUTHENTICATION,
    QUOTA_EXCEEDED,
    RATE_LIMITED,
    INVALID_DATA,
    UNKNOWN,
}
