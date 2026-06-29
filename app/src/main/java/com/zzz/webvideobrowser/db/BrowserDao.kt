package com.zzz.webvideobrowser.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BrowserDao {

    // History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(record: HistoryRecord)

    @Query("SELECT * FROM history_records ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<HistoryRecord>

    @Query("DELETE FROM history_records")
    suspend fun clearAllHistory()

    @Delete
    suspend fun deleteHistory(record: HistoryRecord)

    // Bookmark
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(record: BookmarkRecord)

    @Query("SELECT * FROM bookmark_records ORDER BY timestamp DESC")
    suspend fun getAllBookmarks(): List<BookmarkRecord>

    @Delete
    suspend fun deleteBookmark(record: BookmarkRecord)

    @Query("DELETE FROM bookmark_records")
    suspend fun clearAllBookmarks()
}
