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
    private static final int DB_VERSION = 2;

    public FinanceDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (" +
                "id TEXT PRIMARY KEY, " +
                "type TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "account TEXT NOT NULL DEFAULT 'Kas', " +
                "payment_method TEXT NOT NULL DEFAULT 'Tunai', " +
                "note TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "occurred_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE budgets (" +
                "category TEXT PRIMARY KEY, " +
                "monthly_limit REAL NOT NULL, " +
                "updated_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN account TEXT NOT NULL DEFAULT 'Kas'");
            db.execSQL("ALTER TABLE records ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'Tunai'");
            db.execSQL("CREATE TABLE IF NOT EXISTS budgets (" +
                    "category TEXT PRIMARY KEY, " +
                    "monthly_limit REAL NOT NULL, " +
                    "updated_at INTEGER NOT NULL)");
        }
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
        values.put("account", record.account);
        values.put("payment_method", record.paymentMethod);
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

    public List<MoneyRecord> getRecentRecords(int limit) {
        List<MoneyRecord> all = getVisibleRecords();
        if (all.size() <= limit) {
            return all;
        }
        return new ArrayList<>(all.subList(0, limit));
    }

    public List<MoneyRecord> searchRecords(String keyword, String type) {
        StringBuilder selection = new StringBuilder("deleted = 0");
        List<String> args = new ArrayList<>();
        if (type != null && type.length() > 0) {
            selection.append(" AND type = ?");
            args.add(type);
        }
        if (keyword != null && keyword.trim().length() > 0) {
            selection.append(" AND (category LIKE ? OR account LIKE ? OR payment_method LIKE ? OR note LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return queryRecords(selection.toString(), args.toArray(new String[0]), "occurred_at DESC, updated_at DESC");
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
        return getSummaryBetween(0, Long.MAX_VALUE);
    }

    public Summary getSummaryBetween(long start, long end) {
        Summary summary = new Summary();
        for (MoneyRecord record : getVisibleRecords()) {
            if (record.occurredAt < start || record.occurredAt > end) {
                continue;
            }
            if (MoneyRecord.TYPE_INCOME.equals(record.type)) {
                summary.income += record.amount;
            } else {
                summary.expense += record.amount;
            }
        }
        return summary;
    }

    public List<String> getDistinctValues(String column) {
        List<String> values = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + column + " FROM records WHERE deleted = 0 AND " + column + " <> '' ORDER BY " + column, null);
        try {
            while (cursor.moveToNext()) {
                values.add(cursor.getString(0));
            }
        } finally {
            cursor.close();
        }
        return values;
    }

    public Map<String, Double> getExpenseByCategory(long start, long end) {
        Map<String, Double> data = new LinkedHashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT category, SUM(amount) FROM records WHERE deleted = 0 AND type = ? AND occurred_at BETWEEN ? AND ? GROUP BY category ORDER BY SUM(amount) DESC",
                new String[]{MoneyRecord.TYPE_EXPENSE, String.valueOf(start), String.valueOf(end)}
        );
        try {
            while (cursor.moveToNext()) {
                data.put(cursor.getString(0), cursor.getDouble(1));
            }
        } finally {
            cursor.close();
        }
        return data;
    }

    public void saveBudget(String category, double monthlyLimit) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("category", category);
        values.put("monthly_limit", monthlyLimit);
        values.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict("budgets", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteBudget(String category) {
        getWritableDatabase().delete("budgets", "category = ?", new String[]{category});
    }

    public List<Budget> getBudgets() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("budgets", null, null, null, null, null, "category ASC");
        List<Budget> budgets = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                budgets.add(new Budget(
                        cursor.getString(cursor.getColumnIndexOrThrow("category")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("monthly_limit"))
                ));
            }
        } finally {
            cursor.close();
        }
        return budgets;
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
        JSONArray budgets = new JSONArray();
        for (Budget budget : getBudgets()) {
            JSONObject item = new JSONObject();
            item.put("category", budget.category);
            item.put("monthlyLimit", budget.monthlyLimit);
            budgets.put(item);
        }
        root.put("budgets", budgets);
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
        JSONArray budgets = root.optJSONArray("budgets");
        if (budgets != null) {
            for (int i = 0; i < budgets.length(); i++) {
                JSONObject item = budgets.getJSONObject(i);
                String category = item.optString("category", "");
                double limit = item.optDouble("monthlyLimit", 0);
                if (category.length() > 0 && limit > 0) {
                    saveBudget(category, limit);
                }
            }
        }
        return changed;
    }

    private MoneyRecord fromCursor(Cursor cursor) {
        MoneyRecord record = new MoneyRecord();
        record.id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
        record.type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        record.category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
        record.account = cursor.getString(cursor.getColumnIndexOrThrow("account"));
        record.paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("payment_method"));
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

    public static class Budget {
        public final String category;
        public final double monthlyLimit;

        public Budget(String category, double monthlyLimit) {
            this.category = category;
            this.monthlyLimit = monthlyLimit;
        }
    }
}
