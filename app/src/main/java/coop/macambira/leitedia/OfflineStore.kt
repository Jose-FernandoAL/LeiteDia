package coop.macambira.leitedia

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingMilkEntry(
    val userId: String,
    val input: MilkEntryInput,
    val createdAt: Long,
    val attempts: Int,
    val lastError: String,
    val lastAttemptAt: Long?
)

class OfflineStore(context: Context) : SQLiteOpenHelper(context, "leitedia_offline.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""create table pending_entries (
            client_id text primary key, user_id text not null, producer_id integer not null,
            supplier text not null, liters real not null, shift text not null, notes text not null,
            entry_date text not null, entry_time text not null, created_at integer not null,
            attempts integer not null default 0, last_error text not null default '', last_attempt_at integer)""")
        db.execSQL("""create table cached_producers (
            user_id text not null, id integer not null, name text not null, document text not null,
            phone text not null, community text not null, active integer not null,
            primary key(user_id, id))""")
        createClosedPeriodsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("alter table pending_entries add column attempts integer not null default 0")
            db.execSQL("alter table pending_entries add column last_error text not null default ''")
            db.execSQL("alter table pending_entries add column last_attempt_at integer")
        }
        if (oldVersion < 3) createClosedPeriodsTable(db)
    }

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
                ), createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts")),
                    lastError = cursor.getString(cursor.getColumnIndexOrThrow("last_error")),
                    lastAttemptAt = cursor.getColumnIndexOrThrow("last_attempt_at").let { if (cursor.isNull(it)) null else cursor.getLong(it) }
                ))
            }
        }
    }

    fun remove(clientId: String) { writableDatabase.delete("pending_entries", "client_id=?", arrayOf(clientId)) }
    fun markFailure(clientId: String, message: String) {
        writableDatabase.execSQL(
            "update pending_entries set attempts=attempts+1,last_error=?,last_attempt_at=? where client_id=?",
            arrayOf(message.take(300), System.currentTimeMillis(), clientId)
        )
    }
    fun pendingCount(userId: String): Int = readableDatabase.rawQuery("select count(*) from pending_entries where user_id=?", arrayOf(userId)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun hasPending(userId: String, input: MilkEntryInput): Boolean = readableDatabase.rawQuery(
        "select 1 from pending_entries where user_id=? and producer_id=? and entry_date=? and shift=? limit 1",
        arrayOf(userId, input.producerId.toString(), input.entryDate, input.shift)
    ).use { it.moveToFirst() }

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

    fun cacheClosedPeriods(cooperativeId: String, periods: List<ClosedPeriod>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("cached_closed_periods", "cooperative_id=?", arrayOf(cooperativeId))
            periods.forEach { period -> writableDatabase.insert("cached_closed_periods", null, ContentValues().apply {
                put("cooperative_id", cooperativeId); put("id", period.id); put("start_date", period.startDate); put("end_date", period.endDate)
                put("reason", period.reason); put("closed_at", period.closedAt); put("reopened_at", period.reopenedAt)
            }) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun cachedClosedPeriods(cooperativeId: String): List<ClosedPeriod> = readableDatabase.query(
        "cached_closed_periods", null, "cooperative_id=?", arrayOf(cooperativeId), null, null, "closed_at desc"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(ClosedPeriod(
        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")), startDate = cursor.getString(cursor.getColumnIndexOrThrow("start_date")),
        endDate = cursor.getString(cursor.getColumnIndexOrThrow("end_date")), reason = cursor.getString(cursor.getColumnIndexOrThrow("reason")),
        closedAt = cursor.getString(cursor.getColumnIndexOrThrow("closed_at")), reopenedAt = cursor.getColumnIndexOrThrow("reopened_at").let { if (cursor.isNull(it)) null else cursor.getString(it) }
    )) } }

    companion object {
        private fun createClosedPeriodsTable(db: SQLiteDatabase) = db.execSQL("""create table if not exists cached_closed_periods (
            cooperative_id text not null, id integer not null, start_date text not null, end_date text not null,
            reason text not null, closed_at text not null, reopened_at text, primary key(cooperative_id,id))""")
    }
}
