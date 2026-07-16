package coop.macambira.leitedia

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingMilkEntry(val userId: String, val input: MilkEntryInput)

class OfflineStore(context: Context) : SQLiteOpenHelper(context, "leitedia_offline.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""create table pending_entries (
            client_id text primary key, user_id text not null, producer_id integer not null,
            supplier text not null, liters real not null, shift text not null, notes text not null,
            entry_date text not null, entry_time text not null, created_at integer not null)""")
        db.execSQL("""create table cached_producers (
            user_id text not null, id integer not null, name text not null, document text not null,
            phone text not null, community text not null, active integer not null,
            primary key(user_id, id))""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun queue(userId: String, input: MilkEntryInput) {
        writableDatabase.insertWithOnConflict("pending_entries", null, ContentValues().apply {
            put("client_id", input.clientEntryId); put("user_id", userId); put("producer_id", input.producerId)
            put("supplier", input.supplier); put("liters", input.liters); put("shift", input.shift)
            put("notes", input.notes); put("entry_date", input.entryDate); put("entry_time", input.entryTime); put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun pending(userId: String): List<PendingMilkEntry> {
        return readableDatabase.query("pending_entries", null, "user_id=?", arrayOf(userId), null, null, "created_at asc").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(PendingMilkEntry(userId, MilkEntryInput(
                    producerId = cursor.getLong(cursor.getColumnIndexOrThrow("producer_id")),
                    supplier = cursor.getString(cursor.getColumnIndexOrThrow("supplier")),
                    liters = cursor.getDouble(cursor.getColumnIndexOrThrow("liters")),
                    shift = cursor.getString(cursor.getColumnIndexOrThrow("shift")),
                    notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                    clientEntryId = cursor.getString(cursor.getColumnIndexOrThrow("client_id")),
                    entryDate = cursor.getString(cursor.getColumnIndexOrThrow("entry_date")),
                    entryTime = cursor.getString(cursor.getColumnIndexOrThrow("entry_time"))
                )))
            }
        }
    }

    fun remove(clientId: String) { writableDatabase.delete("pending_entries", "client_id=?", arrayOf(clientId)) }
    fun pendingCount(userId: String): Int = readableDatabase.rawQuery("select count(*) from pending_entries where user_id=?", arrayOf(userId)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun cacheProducers(userId: String, producers: List<Producer>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("cached_producers", "user_id=?", arrayOf(userId))
            producers.forEach { producer -> writableDatabase.insert("cached_producers", null, ContentValues().apply {
                put("user_id", userId); put("id", producer.id); put("name", producer.name); put("document", producer.document)
                put("phone", producer.phone); put("community", producer.community); put("active", if (producer.active) 1 else 0)
            }) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun cachedProducers(userId: String): List<Producer> = readableDatabase.query("cached_producers", null, "user_id=?", arrayOf(userId), null, null, "name asc").use { cursor ->
        buildList { while (cursor.moveToNext()) add(Producer(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")), name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            document = cursor.getString(cursor.getColumnIndexOrThrow("document")), phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
            community = cursor.getString(cursor.getColumnIndexOrThrow("community")), active = cursor.getInt(cursor.getColumnIndexOrThrow("active")) == 1
        )) }
    }
}
