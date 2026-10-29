package com.personal.focusguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppRuleEntity::class, SessionEntity::class, OpenEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): FocusDatabase =
            Room.databaseBuilder(context, FocusDatabase::class.java, "focusguard.db").build()
    }
}
