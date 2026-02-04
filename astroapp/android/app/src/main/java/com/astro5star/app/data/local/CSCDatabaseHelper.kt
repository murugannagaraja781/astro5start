package com.astro5star.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class CSCDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "csc.db"
        const val DB_VERSION = 1
        private var DB_PATH = ""
    }

    init {
        DB_PATH = context.applicationInfo.dataDir + "/databases/"
        copyDataBase()
    }

    private fun checkDataBase(): Boolean {
        val dbFile = File(DB_PATH + DB_NAME)
        return dbFile.exists()
    }

    private fun copyDataBase() {
        if (!checkDataBase()) {
            this.readableDatabase // Create empty DB structure first
            this.close()
            try {
                copyDBFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun copyDBFile() {
        val mInput: InputStream = context.assets.open(DB_NAME)
        val outFile = File(DB_PATH + DB_NAME)
        val mOutput: OutputStream = FileOutputStream(outFile)
        val mBuffer = ByteArray(1024)
        var mLength: Int
        while (mInput.read(mBuffer).also { mLength = it } > 0) {
            mOutput.write(mBuffer, 0, mLength)
        }
        mOutput.flush()
        mOutput.close()
        mInput.close()
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun getCountries(): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM countries ORDER BY name", null)
        try {
            if (cursor.moveToFirst()) {
                do {
                    val map = mutableMapOf<String, String>()
                    map["id"] = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    map["name"] = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    map["iso2"] = cursor.getString(cursor.getColumnIndexOrThrow("iso2"))
                    list.add(map)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) { e.printStackTrace() }
        finally { cursor.close() }
        return list
    }

    fun getStates(countryId: String): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM states WHERE country_id = ? ORDER BY name", arrayOf(countryId))
        try {
            if (cursor.moveToFirst()) {
                do {
                    val map = mutableMapOf<String, String>()
                    map["id"] = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    map["name"] = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    list.add(map)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) { e.printStackTrace() }
        finally { cursor.close() }
        return list
    }

    fun getCities(stateId: String): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM cities WHERE state_id = ? ORDER BY name", arrayOf(stateId))
        try {
            if (cursor.moveToFirst()) {
                do {
                    val map = mutableMapOf<String, String>()
                    map["id"] = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    map["name"] = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    map["latitude"] = cursor.getString(cursor.getColumnIndexOrThrow("latitude"))
                    map["longitude"] = cursor.getString(cursor.getColumnIndexOrThrow("longitude"))
                    // Assuming 'timezone' column exists per user request 'use get timezone also'
                    // If not, we iterate column names to be safe or try/catch column check
                    // For now, assume it's there or try to fetch it if available.
                    // Let's check columns safely for timezone
                    val tzIndex = cursor.getColumnIndex("timezone")
                    if (tzIndex != -1) {
                        map["timezone"] = cursor.getString(tzIndex) ?: ""
                    }
                    else {
                        // Fallback column check? Many csc.db versions use 'wikiDataId' etc, but standard ones might not have timezone directly in cities.
                        // However, user specifically asked "Select contry and select cstate and city use get timezone also this db".
                        // Use safe check.
                    }
                    list.add(map)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) { e.printStackTrace() }
        finally { cursor.close() }
        return list
    }
}
