package tw.expirybook

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local SQLite schema follows Stuff / Tag / Stuff_Tag / Setting in the supplied ERD. */
class Store(context: Context, name: String = "expiry-book.db") : SQLiteOpenHelper(context.applicationContext, name, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Tag(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, icon TEXT NOT NULL, color INTEGER NOT NULL, sort_order INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE Stuff(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, status INTEGER NOT NULL CHECK(status BETWEEN 0 AND 2), note TEXT NOT NULL, expiryDate TEXT NOT NULL, createDate TEXT NOT NULL, reminder INTEGER NOT NULL, reminder_days INTEGER NOT NULL, reminder_minute INTEGER NOT NULL, delivered TEXT)")
        db.execSQL("CREATE TABLE Stuff_Tag(stuff INTEGER NOT NULL REFERENCES Stuff(id) ON DELETE CASCADE, tag INTEGER NOT NULL REFERENCES Tag(id) ON DELETE CASCADE, PRIMARY KEY(stuff,tag))")
        db.execSQL("CREATE TABLE Setting(setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL)")
        db.execSQL("INSERT INTO Tag(id,name,icon,color,sort_order) VALUES(1,'其他','標籤',-10053242,0)")
        db.execSQL("INSERT INTO Setting(setting_key,setting_value,updated_at) VALUES('enabled','true',0),('days','3',0),('minute','540',0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Missing database migration $oldVersion → $newVersion") }
    fun setting(key: String): String = readableDatabase.rawQuery("SELECT setting_value FROM Setting WHERE setting_key=?", arrayOf(key)).use { check(it.moveToFirst()); it.getString(0) }
    val enabled get() = setting("enabled").toBoolean()
    fun settings(enabled: Boolean, days: Int, minute: Int) = transaction { db ->
        mapOf("enabled" to enabled.toString(), "days" to days.toString(), "minute" to minute.toString()).forEach { (k,v) ->
            db.insertWithOnConflict("Setting", null, ContentValues().apply { put("setting_key",k); put("setting_value",v); put("updated_at",System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
    fun tags(): List<Tag> = readableDatabase.rawQuery("SELECT id,name,icon,color,sort_order FROM Tag ORDER BY sort_order,id", null).use { c ->
        buildList { while(c.moveToNext()) add(Tag(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4))) }
    }
    fun items(): List<Stuff> {
        val links = mutableMapOf<Long,MutableSet<Long>>()
        readableDatabase.rawQuery("SELECT stuff,tag FROM Stuff_Tag", null).use { c -> while(c.moveToNext()) links.getOrPut(c.getLong(0)){mutableSetOf()}.add(c.getLong(1)) }
        return readableDatabase.rawQuery("SELECT id,name,expiryDate,createDate,status,note,reminder,reminder_days,reminder_minute,delivered FROM Stuff",null).use { c ->
            buildList { while(c.moveToNext()) add(Stuff(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4),c.getString(5),c.getInt(6)==1,c.getInt(7),c.getInt(8),links[c.getLong(0)]?.toSet() ?: setOf(1L),c.getString(9))) }
        }
    }
    fun save(item: Stuff): Long {
        require(item.name.isNotBlank()) { "請輸入物品名稱" }
        Rules.calendar(item.expiry)
        require(item.days in Rules.offsets && item.minute in 0..1439)
        var id = item.id
        transaction { db ->
            val old = if(id == 0L) null else items().find { it.id == id }
            val v = ContentValues().apply {
                put("name",item.name.trim()); put("expiryDate",item.expiry); put("createDate",old?.created ?: item.created)
                put("status",item.status); put("note",item.note); put("reminder",if(item.reminder) 1 else 0)
                put("reminder_days",item.days); put("reminder_minute",item.minute)
                put("delivered", if(old != null && Rules.token(old)==Rules.token(item)) old.delivered else null)
            }
            if(id==0L) id=db.insertOrThrow("Stuff",null,v) else check(db.update("Stuff",v,"id=?",arrayOf(id.toString()))==1)
            db.delete("Stuff_Tag","stuff=?",arrayOf(id.toString()))
            item.tags.ifEmpty { setOf(1L) }.forEach { tag -> db.execSQL("INSERT INTO Stuff_Tag(stuff,tag) VALUES(?,?)",arrayOf(id,tag)) }
        }
        return id
    }
    fun status(id: Long, status: Int) { require(status in 0..2); writableDatabase.execSQL("UPDATE Stuff SET status=? WHERE id=?",arrayOf<Any>(status,id)) }
    fun deleteItem(id: Long) { writableDatabase.delete("Stuff","id=?",arrayOf(id.toString())) }
    fun delivered(item: Stuff) { writableDatabase.execSQL("UPDATE Stuff SET delivered=? WHERE id=?",arrayOf<Any>(Rules.token(item),item.id)) }
    fun saveTag(id: Long?, name: String, icon: String, color: Int) {
        require(name.isNotBlank()) { "請輸入標籤名稱" }; require(id != 1L) { "不可編輯預設標籤" }
        require(tags().none { it.id != id && it.name == name.trim() }) { "標籤名稱已存在" }
        val v = ContentValues().apply { put("name",name.trim()); put("icon",icon); put("color",color) }
        if(id==null) { v.put("sort_order", (tags().maxOfOrNull { it.order } ?: -1)+1); writableDatabase.insertOrThrow("Tag",null,v) }
        else writableDatabase.update("Tag",v,"id=?",arrayOf(id.toString()))
    }
    fun deleteTag(id: Long) { require(id!=1L); transaction { db ->
        db.delete("Tag","id=?",arrayOf(id.toString()))
        db.execSQL("INSERT INTO Stuff_Tag(stuff,tag) SELECT id,1 FROM Stuff WHERE NOT EXISTS(SELECT 1 FROM Stuff_Tag WHERE stuff=Stuff.id)")
    } }
    fun reorder(ids: List<Long>) { require(ids.toSet()==tags().map { it.id }.toSet() && ids.distinct().size==ids.size); transaction { db -> ids.forEachIndexed { n,id -> db.execSQL("UPDATE Tag SET sort_order=? WHERE id=?",arrayOf<Any>(n,id)) } } }
    private fun transaction(block: (SQLiteDatabase)->Unit) { val db=writableDatabase; db.beginTransaction(); try { block(db); db.setTransactionSuccessful() } finally { db.endTransaction() } }
}
