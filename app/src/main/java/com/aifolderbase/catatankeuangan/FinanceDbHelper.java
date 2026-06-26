package com.aifolderbase.catatankeuangan;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FinanceDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "catatan_keuangan.db";
    private static final int DB_VERSION = 1;

    public FinanceDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (" +
                "id TEXT PRIMARY KEY, " +
                "type TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "note TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "occurred_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "deleted INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS records");
        onCreate(db);
    }

    public void saveRecord(MoneyRecord record) {
        record.updatedAt = System.currentTimeMillis();
        upsertRecord(record);
    }

    public void deleteRecord(String id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("deleted", 1);
        values.put("updated_at", System.currentTimeMillis());
        db.update("records", values, "id = ?", new String[]{id});
    }

    public void upsertRecord(MoneyRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", record.id);
        values.put("type", record.type);
        values.put("category", record.category);
        values.put("note", record.note);
        values.put("amount", record.amount);
        values.put("occurred_at", record.occurredAt);
        values.put("updated_at", record.updatedAt);
        values.put("deleted", record.deleted ? 1 : 0);
        db.insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<MoneyRecord> getVisibleRecords() {
        return queryRecords("deleted = 0", null, "occurred_at DESC, updated_at DESC");
    }

    public List<MoneyRecord> getAllRecords() {
        return queryRecords(null, null, "updated_at DESC");
    }

    private List<MoneyRecord> queryRecords(String selection, String[] args, String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("records", null, selection, args, null, null, orderBy);
        List<MoneyRecord> records = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                records.add(fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return records;
    }

    public Summary getSummary() {
        Summary summary = new Summary();
        for (MoneyRecord record : getVisibleRecords()) {
            if (MoneyRecord.TYPE_INCOME.equals(record.type)) {
                summary.income += record.amount;
            } else {
                summary.expense += record.amount;
            }
        }
        return summary;
    }

    public JSONObject exportJson() throws JSONException {
        JSONObject root = new JSONObject();
        JSONArray records = new JSONArray();
        for (MoneyRecord record : getAllRecords()) {
            records.put(record.toJson());
        }
        root.put("schemaVersion", 1);
        root.put("updatedAt", System.currentTimeMillis());
        root.put("records", records);
        return root;
    }

    public int mergeJson(JSONObject root) throws JSONException {
        JSONArray incoming = root.optJSONArray("records");
        if (incoming == null) {
            return 0;
        }

        Map<String, MoneyRecord> local = new LinkedHashMap<>();
        for (MoneyRecord record : getAllRecords()) {
            local.put(record.id, record);
        }

        int changed = 0;
        for (int i = 0; i < incoming.length(); i++) {
            MoneyRecord remote = MoneyRecord.fromJson(incoming.getJSONObject(i));
            MoneyRecord current = local.get(remote.id);
            if (current == null || remote.updatedAt > current.updatedAt) {
                upsertRecord(remote);
                changed++;
            }
        }
        return changed;
    }

    private MoneyRecord fromCursor(Cursor cursor) {
        MoneyRecord record = new MoneyRecord();
        record.id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
        record.type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        record.category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
        record.note = cursor.getString(cursor.getColumnIndexOrThrow("note"));
        record.amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
        record.occurredAt = cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at"));
        record.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        record.deleted = cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1;
        return record;
    }

    public static class Summary {
        public double income;
        public double expense;

        public double balance() {
            return income - expense;
        }
    }
}
