package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlayHistoryEntity::class,
        LyricResourceEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class YimlyDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: YimlyDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_songs_id` ON `songs` (`id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_id` ON `albums` (`id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_id` ON `artists` (`id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_id` ON `playlists` (`id`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `hasElrc` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `lrcPath` TEXT")
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `elrcPath` TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lyric_resources` (
                        `songId` TEXT NOT NULL,
                        `format` TEXT NOT NULL,
                        `rawLyrics` TEXT NOT NULL,
                        `filePath` TEXT,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`songId`, `format`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyric_resources_songId` ON `lyric_resources` (`songId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyric_resources_format` ON `lyric_resources` (`format`)")
            }
        }

        fun getDatabase(context: Context): YimlyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YimlyDatabase::class.java,
                    "yimly_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
