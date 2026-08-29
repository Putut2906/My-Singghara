package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranDao {
    @Query("SELECT * FROM quran_bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarksFlow(): Flow<List<QuranBookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: QuranBookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: QuranBookmark)

    @Query("DELETE FROM quran_bookmarks WHERE surahId = :surahId AND verseNumber = :verseNumber")
    suspend fun deleteBookmarkByVerse(surahId: Int, verseNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM quran_bookmarks WHERE surahId = :surahId AND verseNumber = :verseNumber LIMIT 1)")
    suspend fun isBookmarked(surahId: Int, verseNumber: Int): Boolean

    @Query("DELETE FROM quran_bookmarks")
    suspend fun clearBookmarks()

    @Query("SELECT * FROM quran_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllHistoryFlow(): Flow<List<QuranHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: QuranHistory)

    @Query("DELETE FROM quran_history")
    suspend fun clearHistory()
}
