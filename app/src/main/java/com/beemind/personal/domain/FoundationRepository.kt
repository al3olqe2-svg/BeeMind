package com.beemind.personal.domain

import kotlinx.coroutines.flow.Flow

/** A small read-only boundary for local application metadata. No notes API in Stage 0. */
interface FoundationRepository {
    fun observeValue(key: String): Flow<String?>
}
