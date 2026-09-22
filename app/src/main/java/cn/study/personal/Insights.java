package cn.study.personal;
import org.json.*;
import java.time.*;
import java.util.*;

final class Insights {
    static boolean between(String d,String from,String to){return d.compareTo(from)>=0&&d.compareTo(to)<=0;}
    static JSONObject statistics(Store db,int days){
        String from=Data.ago(days-1),to=Data.today();List<JSONObject> study=db.list("study"),tasks=db.list("tasks");
        int minutes=0,planned=0,done=0;Set<String> dates=new HashSet<>(),allDates=new HashSet<>();JSONObject subjects=new JSONObject();
        for(JSONObject o:study){String d=o.optString("date");if(d.compareTo(to)<=0)allDates.add(d);if(between(d,from,to)){minutes+=o.optInt("minutes");dates.add(d);String s=o.optString("subject");Data.set(subjects,s,subjects.optInt(s)+o.optInt("minutes"));}}
        for(JSONObject o:tasks)if(between(o.optString("date"),from,to)&&!o.optString("status").equals("放弃")){planned++;if(o.optBoolean("done"))done++;}
        int streak=0;LocalDate d=LocalDate.now();if(!allDates.contains(d.toString()))d=d.minusDays(1);while(allDates.contains(d.toString())){streak++;d=d.minusDays(1);}
        return Data.obj("from",from,"to",to,"minutes",minutes,"studyDays",dates.size(),"streakDays",streak,"tasks",planned,"completedTasks",done,"completionRate",planned==0?0:done*100.0/planned,"subjectMinutes",subjects);
    }
    static JSONObject snapshot(Store store,String type,JSONObject options,Set<String> included){
        int days=type.equals("AI 周报")?7:30;JSONObject root=Data.obj("today",Data.today(),"function",type,"options",options,"profile",store.profile(),"statistics",statistics(store,days));
        // Settings unrelated to analysis are never sent.
        JSONObject profile=root.optJSONObject("profile");for(String k:new String[]{"model","aiEnabled","maxTokens","inputPrice","outputPrice","theme"})profile.remove(k);
        root.remove("statistics");if(included.contains("study")||included.contains("tasks")){
            JSONObject stat=statistics(store,days);if(!included.contains("study")){stat.remove("minutes");stat.remove("studyDays");stat.remove("streakDays");stat.remove("subjectMinutes");}if(!included.contains("tasks")){stat.remove("tasks");stat.remove("completedTasks");stat.remove("completionRate");}Data.set(root,"statistics",stat);
        }
        JSONObject counts=new JSONObject();int remaining=22000;
        for(String kind:new String[]{"exams","study","tasks","weak"}){
            if(!included.contains(kind))continue;JSONArray selected=new JSONArray();int eligible=0;int cap=kind.equals("exams")?12:kind.equals("study")?80:60;
            for(JSONObject o:store.list(kind)){
                String date=o.optString("date");boolean include=kind.equals("exams")?date.compareTo(Data.today())<=0:kind.equals("study")?between(date,Data.ago(days-1),Data.today()):kind.equals("weak")?true:type.equals("AI 周报")?between(date,Data.ago(6),Data.today()):true;
                if(!include)continue;eligible++;JSONObject trimmed=Data.copy(o);trimmed.remove("id");trimmed.remove("history");String note=trimmed.optString("note");if(note.length()>240)Data.set(trimmed,"note",note.substring(0,240)+"…");int size=trimmed.toString().length();
                if(selected.length()<cap&&size<=remaining){selected.put(trimmed);remaining-=size;}
            }Data.set(root,kind,selected);Data.set(counts,kind,Data.obj("sent",selected.length(),"eligible",eligible));
        }
        Data.set(root,"dataRange",counts);Data.set(root,"limits","成绩最多12次，记录取最近"+days+"天最多80条，任务与薄弱点各最多60条；受总长度限制可能减少。缺失记录不代表没有学习；关联不代表因果。备注最多240字。");return root;
    }
}
