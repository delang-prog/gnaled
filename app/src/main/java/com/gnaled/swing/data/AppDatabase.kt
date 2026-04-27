package com.gnaled.swing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gnaled.swing.data.entity.BallSample
import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing

@Database(
    entities = [Swing::class, Sample::class, Metric::class, BallSample::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun swingDao(): SwingDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ball_samples (
                        swingId TEXT NOT NULL,
                        frameIndex INTEGER NOT NULL,
                        timestampMillis INTEGER NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        confidence REAL NOT NULL,
                        PRIMARY KEY(swingId, frameIndex),
                        FOREIGN KEY(swingId) REFERENCES swings(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ball_samples_swingId ON ball_samples(swingId)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "swing.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
