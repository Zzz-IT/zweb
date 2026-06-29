package com.zzz.webvideobrowser.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmark_records")
data class BookmarkRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long
)
