package com.example.eventreminder.data.repo


import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao
) {

    fun getAllReminders(): Flow<List<EventReminder>> = dao.getAll()

    suspend fun insert(reminder: EventReminder): Long =
        dao.insert(reminder)

    suspend fun update(reminder: EventReminder) =
        dao.update(reminder)

    suspend fun delete(reminder: EventReminder) =
        dao.delete(reminder)

    suspend fun getAllOnce(): List<EventReminder> =
        dao.getAllOnce()

    suspend fun getReminder(id: Long): EventReminder? =
        dao.getById(id)
}
