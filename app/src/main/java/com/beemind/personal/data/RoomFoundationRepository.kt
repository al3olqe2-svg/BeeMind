package com.beemind.personal.data

import com.beemind.personal.data.local.FoundationDao
import com.beemind.personal.domain.FoundationRepository
import kotlinx.coroutines.flow.Flow

class RoomFoundationRepository(private val dao: FoundationDao) : FoundationRepository {
    override fun observeValue(key: String): Flow<String?> = dao.observeValue(key)
}
