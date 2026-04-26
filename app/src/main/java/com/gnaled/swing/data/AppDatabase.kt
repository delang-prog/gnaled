package com.gnaled.swing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing

@Database(
    entities = [Swing::class, Sample::class, Metric::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun swingDao(): SwingDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "swing.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
