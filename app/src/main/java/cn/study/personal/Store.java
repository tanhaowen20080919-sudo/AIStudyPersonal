package cn.study.personal;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

final class Store extends SQLiteOpenHelper {
    static final String[] KINDS={"exams","study","tasks","weak","reports","usage"};
    Store(Context c){super(c,"study-personal.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY NOT NULL,kind TEXT NOT NULL,date TEXT NOT NULL,payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX records_kind_date ON records(kind,date)");
        db.execSQL("CREATE TABLE metadata (name TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("需要数据迁移，未修改原数据");}
    List<JSONObject> list(String kind){
        ArrayList<JSONObject> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("records",new String[]{"payload"},"kind=?",new String[]{kind},null,null,"date DESC, rowid DESC")){
            while(c.moveToNext())try{result.add(new JSONObject(c.getString(0)));}catch(JSONException e){throw new IllegalStateException("本地数据读取失败",e);}
        }return result;
    }
    void put(String kind,JSONObject o){Data.validate(kind,o);putUnchecked(getWritableDatabase(),kind,o);}
    private void putUnchecked(SQLiteDatabase db,String kind,JSONObject o){
        ContentValues v=new ContentValues();v.put("id",o.optString("id"));v.put("kind",kind);v.put("date",o.optString("date"));v.put("payload",o.toString());
        if(db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("保存失败，请检查存储空间");
    }
    void delete(String kind,String id){getWritableDatabase().delete("records","kind=? AND id=?",new String[]{kind,id});}
    JSONObject profile(){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM metadata WHERE name='profile'",null)){if(c.moveToFirst())try{return new JSONObject(c.getString(0));}catch(JSONException e){throw new IllegalStateException(e);}return Data.defaultProfile();}}
    void profile(JSONObject p){Data.validateProfile(p);ContentValues v=new ContentValues();v.put("name","profile");v.put("value",p.toString());getWritableDatabase().insertWithOnConflict("metadata",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    JSONObject backup(){JSONObject root=Data.obj("format","AIStudyPersonal","version",1,"exportedAt",java.time.Instant.now().toString(),"profile",profile());for(String kind:KINDS)Data.set(root,kind,new JSONArray(list(kind)));return root;}
    static int validateBackup(JSONObject root){
        Data.require(root.optString("format").equals("AIStudyPersonal")&&root.optInt("version")==1,"不是本 App 的 v1 备份");
        JSONObject profile=root.optJSONObject("profile");Data.require(profile!=null,"备份缺少设置");Data.validateProfile(profile);
        HashSet<String> ids=new HashSet<>();int count=0;
        for(String kind:KINDS){JSONArray a=root.optJSONArray(kind);Data.require(a!=null,"备份缺少 "+kind);Data.require(a.length()<=10000,"单类记录超过 10000 条");
            for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);Data.validate(kind,o);Data.require(ids.add(o.optString("id")),"备份有重复 ID");count++;}}
        return count;
    }
    int restore(JSONObject root,boolean replace){
        int total=validateBackup(root);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(replace)db.delete("records",null,null);
            for(String kind:KINDS){JSONArray a=root.optJSONArray(kind);for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);
                if(!replace){try(Cursor c=db.rawQuery("SELECT id FROM records WHERE id=?",new String[]{o.optString("id")})){if(c.moveToFirst())continue;}}
                putUnchecked(db,kind,o);
            }}
            if(replace)profile(root.optJSONObject("profile"));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}return total;
    }
    void saveAi(JSONObject usage,JSONObject report){
        Data.validate("usage",usage);if(report!=null)Data.validate("reports",report);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{putUnchecked(db,"usage",usage);if(report!=null)putUnchecked(db,"reports",report);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    void addTasks(JSONArray tasks){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<tasks.length();i++){JSONObject o=tasks.optJSONObject(i);Data.validate("tasks",o);putUnchecked(db,"tasks",o);}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void clear(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("records",null,null);db.delete("metadata",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
