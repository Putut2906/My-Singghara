package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "fcm_notifications")
data class FcmNotification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val dataPayload: String = ""
)

@Dao
interface FcmNotificationDao {
    @Query("SELECT * FROM fcm_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<FcmNotification>>

    @Query("SELECT COUNT(*) FROM fcm_notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: FcmNotification): Long

    @Query("UPDATE fcm_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE fcm_notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Delete
    suspend fun deleteNotification(notification: FcmNotification)

    @Query("DELETE FROM fcm_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM fcm_notifications")
    suspend fun clearAll()
}
