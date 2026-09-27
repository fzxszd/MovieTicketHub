package me.ibrahim.moviesapp.compose.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class Migration7To8Test {
    @Test fun migrationRemovesCredentialsAndPreservesProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("migration-test.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE UserAccount(email TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,avatarUri TEXT,password TEXT NOT NULL,isLoggedIn INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE FavoriteMovie(id INTEGER PRIMARY KEY,userEmail TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE MovieOrder(id INTEGER PRIMARY KEY,userEmail TEXT NOT NULL)")
                        db.execSQL("INSERT INTO UserAccount VALUES ('Alice@Example.com','Alice',NULL,'secret',1)")
                        db.execSQL("INSERT INTO FavoriteMovie VALUES (1,'Alice@Example.com')")
                        db.execSQL("INSERT INTO MovieOrder VALUES (1,'Alice@Example.com')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        val db = helper.writableDatabase
        DatabaseFactory.MIGRATION_7_8.migrate(db)
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(UserAccount)").use { cursor ->
            val index = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) columns += cursor.getString(index)
        }
        assertFalse(columns.contains("password"))
        assertFalse(columns.contains("isLoggedIn"))
        db.query("SELECT email,name,userId FROM UserAccount").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("alice@example.com", cursor.getString(0))
            assertEquals("Alice", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
        db.query("SELECT COUNT(*) FROM FavoriteMovie").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
        db.query("SELECT COUNT(*) FROM MovieOrder").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
        helper.close()
        context.deleteDatabase("migration-test.db")
    }
}
