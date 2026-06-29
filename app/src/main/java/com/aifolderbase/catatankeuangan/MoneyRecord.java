package com.aifolderbase.catatankeuangan;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class MoneyRecord {
    public static final String TYPE_INCOME = "income";
    public static final String TYPE_EXPENSE = "expense";

    public String id;
    public String type;
    public String category;
    public String account;
    public String paymentMethod;
    public String note;
    public double amount;
    public long occurredAt;
    public long updatedAt;
    public boolean deleted;

    public MoneyRecord() {
        id = UUID.randomUUID().toString();
        type = TYPE_EXPENSE;
        category = "";
        account = "Kas";
        paymentMethod = "Tunai";
        note = "";
        amount = 0;
        occurredAt = System.currentTimeMillis();
        updatedAt = occurredAt;
        deleted = false;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("type", type);
        object.put("category", category);
        object.put("account", account);
        object.put("paymentMethod", paymentMethod);
        object.put("note", note);
        object.put("amount", amount);
        object.put("occurredAt", occurredAt);
        object.put("updatedAt", updatedAt);
        object.put("deleted", deleted);
        return object;
    }

    public static MoneyRecord fromJson(JSONObject object) {
        MoneyRecord record = new MoneyRecord();
        record.id = object.optString("id", record.id);
        record.type = object.optString("type", TYPE_EXPENSE);
        record.category = object.optString("category", "");
        record.account = object.optString("account", "Kas");
        record.paymentMethod = object.optString("paymentMethod", "Tunai");
        record.note = object.optString("note", "");
        record.amount = object.optDouble("amount", 0);
        record.occurredAt = object.optLong("occurredAt", System.currentTimeMillis());
        record.updatedAt = object.optLong("updatedAt", record.occurredAt);
        record.deleted = object.optBoolean("deleted", false);
        return record;
    }
}
