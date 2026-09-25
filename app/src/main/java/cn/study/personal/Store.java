package cn.study.personal;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/**
 * 本地 SQLite 存储。数据库 study-personal.db，版本 2。
 * v1 -> v2 迁移：records 表结构不变（payload 为 JSON，新增 kind 直接复用该表），
 * 不删除任何数据、不重建表。奖励与积分以 kind=rewards / kind=ledger 存入 records 表。
 */
final class Store extends SQLiteOpenHelper {
    static final String[] KINDS = {"exams", "study", "tasks", "weak", "reports", "usage", "rewards", "ledger"};
    static final String[] V1_KINDS = {"exams", "study", "tasks", "weak", "reports", "usage"};
    static final int BACKUP_VERSION = 2;

    Store(Context c) {
        super(c, "study-personal.db", null, 2);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY NOT NULL,kind TEXT NOT NULL,date TEXT NOT NULL,payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX records_kind_date ON records(kind,date)");
        db.execSQL("CREATE TABLE metadata (name TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 -> v2：表结构无需变更。新 kind（rewards/ledger）直接存入 records 表。
        // 模型默认值等数据迁移在 MainActivity.migrate() 中按 profile 安全处理。
        if (oldVersion < 2) {
            // 显式空迁移：绝不删数据。
        }
    }

    List<JSONObject> list(String kind) {
        ArrayList<JSONObject> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("records", new String[]{"payload"}, "kind=?", new String[]{kind}, null, null, "date DESC, rowid DESC")) {
            while (c.moveToNext()) try {
                result.add(new JSONObject(c.getString(0)));
            } catch (JSONException e) {
                throw new IllegalStateException("本地数据读取失败", e);
            }
        }
        return result;
    }

    JSONObject find(String kind, String id) {
        try (Cursor c = getReadableDatabase().query("records", new String[]{"payload"}, "kind=? AND id=?", new String[]{kind, id}, null, null, null)) {
            if (c.moveToFirst()) return new JSONObject(c.getString(0));
        } catch (JSONException e) {
            throw new IllegalStateException("本地数据读取失败", e);
        }
        return null;
    }

    void put(String kind, JSONObject o) {
        Data.validate(kind, o);
        putUnchecked(getWritableDatabase(), kind, o);
    }

    private void putUnchecked(SQLiteDatabase db, String kind, JSONObject o) {
        ContentValues v = new ContentValues();
        v.put("id", o.optString("id"));
        v.put("kind", kind);
        v.put("date", o.optString("date"));
        v.put("payload", o.toString());
        if (db.insertWithOnConflict("records", null, v, SQLiteDatabase.CONFLICT_REPLACE) < 0)
            throw new IllegalStateException("保存失败，请检查存储空间");
    }

    void delete(String kind, String id) {
        getWritableDatabase().delete("records", "kind=? AND id=?", new String[]{kind, id});
    }

    void deleteLedgerForRef(String refId) {
        getWritableDatabase().delete("records", "kind='ledger' AND id LIKE ?", new String[]{"%-" + refId});
    }

    JSONObject profile() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM metadata WHERE name='profile'", null)) {
            if (c.moveToFirst()) try {
                return new JSONObject(c.getString(0));
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            return Data.defaultProfile();
        }
    }

    void profile(JSONObject p) {
        Data.validateProfile(p);
        ContentValues v = new ContentValues();
        v.put("name", "profile");
        v.put("value", p.toString());
        getWritableDatabase().insertWithOnConflict("metadata", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------- 积分（幂等：同一规则+同一来源 => 固定 id，重复写入不会重复计分） ----------

    static String ledgerId(String rule, String refId) {
        return "pt-" + rule + "-" + refId;
    }

    /** 按规则给积分；规则关闭或分值为 0 则不计。同一来源重复调用不会重复加分。 */
    void award(String rule, String refId, String refKind, int points, String note) {
        if (points == 0) return;
        JSONObject o = Data.obj("id", ledgerId(rule, refId), "date", Data.today(),
                "rule", rule, "refId", refId, "refKind", refKind, "points", points, "note", note == null ? "" : note);
        put("ledger", o);
    }

    void unaward(String rule, String refId) {
        delete("ledger", ledgerId(rule, refId));
    }

    int balance() {
        int sum = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM records WHERE kind='ledger'", null)) {
            while (c.moveToNext()) try {
                sum += new JSONObject(c.getString(0)).optInt("points");
            } catch (JSONException ignored) {
            }
        }
        return sum;
    }

    // ---------- 备份 v2 ----------

    JSONObject backup() {
        JSONObject root = Data.obj("format", "AIStudyPersonal", "backupSchemaVersion", BACKUP_VERSION,
                "exportedAt", java.time.Instant.now().toString(), "profile", profile());
        for (String kind : KINDS) Data.set(root, kind, new JSONArray(list(kind)));
        return root;
    }

    /** 返回记录条数。兼容 v1（version=1）与 v2（backupSchemaVersion=2）备份。 */
    static int validateBackup(JSONObject root) {
        Data.require(root != null, "备份文件为空");
        Data.require(root.optString("format").equals("AIStudyPersonal"), "不是本 App 的备份");
        int schema = root.has("backupSchemaVersion") ? root.optInt("backupSchemaVersion", -1)
                : (root.optInt("version", -1) == 1 ? 1 : -1);
        Data.require(schema == 1 || schema == 2, "不支持的备份版本");
        JSONObject profile = root.optJSONObject("profile");
        Data.require(profile != null, "备份缺少设置");
        Data.validateProfile(profile);
        HashSet<String> ids = new HashSet<>();
        int count = 0;
        for (String kind : KINDS) {
            JSONArray a = root.optJSONArray(kind);
            if (schema == 1 && (kind.equals("rewards") || kind.equals("ledger"))) {
                if (a == null) continue; // v1 备份没有奖励数据
            }
            Data.require(a != null, "备份缺少 " + kind);
            Data.require(a.length() <= 10000, "单类记录超过 10000 条");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                Data.validate(kind, o);
                Data.require(ids.add(o.optString("id")), "备份有重复 ID");
                count++;
            }
        }
        return count;
    }

    int restore(JSONObject root, boolean replace) {
        int total = validateBackup(root);
        int schema = root.has("backupSchemaVersion") ? root.optInt("backupSchemaVersion", 1)
                : (root.optInt("version", -1) == 1 ? 1 : 1);
        String[] kinds = schema == 1 ? V1_KINDS : KINDS;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (replace) db.delete("records", null, null);
            for (String kind : kinds) {
                JSONArray a = root.optJSONArray(kind);
                if (a == null) continue;
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (!replace) {
                        try (Cursor c = db.rawQuery("SELECT id FROM records WHERE id=?", new String[]{o.optString("id")})) {
                            if (c.moveToFirst()) continue;
                        }
                    }
                    putUnchecked(db, kind, o);
                }
            }
            if (replace) profile(root.optJSONObject("profile"));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return total;
    }

    void saveAi(JSONObject usage, JSONObject report) {
        Data.validate("usage", usage);
        if (report != null) Data.validate("reports", report);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putUnchecked(db, "usage", usage);
            if (report != null) putUnchecked(db, "reports", report);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void addTasks(JSONArray tasks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject o = tasks.optJSONObject(i);
                Data.validate("tasks", o);
                putUnchecked(db, "tasks", o);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("records", null, null);
            db.delete("metadata", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
