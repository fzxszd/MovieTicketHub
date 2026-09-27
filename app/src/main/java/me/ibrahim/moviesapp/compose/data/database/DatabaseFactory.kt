package me.ibrahim.moviesapp.compose.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseFactory {
    fun create(context: Context): MoviesDatabase {
        return Room.databaseBuilder(
            context,
            MoviesDatabase::class.java,
            FavoriteMoviesDb
        ).addMigrations(MIGRATION_7_8, MIGRATION_8_9).build()
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE UserAccount RENAME TO UserAccount_legacy")
            db.execSQL("CREATE TABLE IF NOT EXISTS UserAccount (email TEXT NOT NULL PRIMARY KEY, userId TEXT, name TEXT NOT NULL, avatarUri TEXT)")
            db.execSQL("INSERT INTO UserAccount(email,userId,name,avatarUri) SELECT lower(trim(email)),NULL,name,avatarUri FROM UserAccount_legacy")
            db.execSQL("DROP TABLE UserAccount_legacy")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_UserAccount_userId ON UserAccount(userId)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS FavoriteState (accountId TEXT NOT NULL, movieId INTEGER NOT NULL CHECK(movieId > 0), confirmedState INTEGER NOT NULL, desiredState INTEGER NOT NULL, serverRevision INTEGER NOT NULL CHECK(serverRevision >= 0), syncStatus TEXT NOT NULL, lastErrorCode TEXT, title TEXT, posterPath TEXT, releaseDate TEXT, availability TEXT NOT NULL DEFAULT 'AVAILABLE', PRIMARY KEY(accountId,movieId))")
            db.execSQL("CREATE TABLE IF NOT EXISTS FavoriteOperation (clientOperationId TEXT NOT NULL, accountId TEXT NOT NULL, movieId INTEGER NOT NULL CHECK(movieId > 0), targetState INTEGER NOT NULL, localSequence INTEGER NOT NULL CHECK(localSequence > 0), status TEXT NOT NULL, retryCount INTEGER NOT NULL DEFAULT 0, lastErrorCode TEXT, PRIMARY KEY(clientOperationId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorite_operation_account_sequence ON FavoriteOperation(accountId,status,localSequence)")
            db.execSQL("CREATE TABLE IF NOT EXISTS FavoriteSyncCursor (accountId TEXT NOT NULL PRIMARY KEY, lastServerRevision INTEGER NOT NULL DEFAULT 0, lastSyncAt TEXT)")
            db.execSQL("INSERT OR IGNORE INTO FavoriteState(accountId,movieId,confirmedState,desiredState,serverRevision,syncStatus,title,posterPath,releaseDate) SELECT u.userId,f.id,1,1,0,'PENDING',f.title,f.posterPath,f.releaseDate FROM FavoriteMovie f JOIN UserAccount u ON lower(trim(u.email))=lower(trim(f.userEmail)) WHERE f.id > 0 AND u.userId IS NOT NULL AND trim(u.userId) != ''")
        }
    }
}
