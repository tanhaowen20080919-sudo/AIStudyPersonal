package cn.study.personal;

import org.json.*;
import java.time.LocalDate;
import java.util.*;

final class Data {
    static String[] SUBJECTS = {"语文", "数学", "英语", "物理", "化学", "地理"};
    static final String[] CAUSES = {"知识漏洞", "概念理解错误", "公式遗忘", "思路卡住", "方法不熟", "审题错误", "计算错误", "粗心", "其他"};
    static final String[] MASTERY = {"未掌握", "正在强化", "已掌握"};
    static final String[] STATUS = {"未开始", "进行中", "已完成", "放弃"};
    static final String[] EXAM_TYPES = {"周测", "月考", "期中", "期末", "模考", "其他"};
    static JSONObject obj(Object... pairs) {
        JSONObject o = new JSONObject();
        try { for (int i=0;i<pairs.length;i+=2) o.put((String)pairs[i],pairs[i+1]); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
        return o;
    }
    static void set(JSONObject o,String key,Object value) { try {o.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);} }
    static JSONObject copy(JSONObject o) { try{return new JSONObject(o.toString());}catch(Exception e){throw new IllegalArgumentException(e);} }
    static String today() { return LocalDate.now().toString(); }
    static String ago(int n) { return LocalDate.now().minusDays(n).toString(); }
    static String id() { return UUID.randomUUID().toString(); }
    static String number(double d) { return d == Math.rint(d) ? String.valueOf((long)d) : String.format(Locale.CHINA,"%.1f",d); }
    static void require(boolean yes,String message) {if(!yes)throw new IllegalArgumentException(message);}
    static void date(String value) { try { require(value.matches("\\d{4}-\\d{2}-\\d{2}"),"日期格式为 YYYY-MM-DD"); LocalDate d=LocalDate.parse(value); require(d.getYear()>=2000 && d.getYear()<=2100,"日期超出范围"); }catch(Exception e){throw new IllegalArgumentException("请填写有效日期，如 2026-09-21");} }
    static void text(JSONObject o,String k,int max,boolean required) {String s=o.optString(k,"");require(s.length()<=max && (!required||!s.trim().isEmpty()),"请检查「"+k+"」是否为空或过长");}
    static double numeric(JSONObject o,String k,double min,double max) {double n=o.optDouble(k,Double.NaN);require(Double.isFinite(n)&&n>=min&&n<=max,"「"+k+"」须在 "+number(min)+"～"+number(max)+" 之间");return n;}
    static void integer(JSONObject o,String k,int min,int max){double n=numeric(o,k,min,max);require(n==Math.rint(n),"「"+k+"」须为整数");}
    static void oneOf(String value,String[] allowed){require(Arrays.asList(allowed).contains(value),"未知选项："+value);}
    static void validate(String kind,JSONObject o) {
        require(o!=null,"数据条目不能为空");
        require(o.optString("id").matches("[A-Za-z0-9_-]{8,80}"),"记录 ID 不合法");
        date(o.optString("date"));
        switch(kind){
            case "exams":
                text(o,"title",100,true); text(o,"note",2000,false); integer(o,"rank",0,1000000);
                if(o.has("gradeRank"))integer(o,"gradeRank",0,1000000);if(o.has("examType"))oneOf(o.optString("examType"),EXAM_TYPES);
                JSONObject scores=o.optJSONObject("scores");require(scores!=null&&scores.length()>0&&scores.length()<=20,"请填写 1～20 科成绩");
                Iterator<String> keys=scores.keys();while(keys.hasNext()){String s=keys.next();subject(s);JSONObject v=scores.optJSONObject(s);require(v!=null,"科目成绩结构错误");double m=numeric(v,"max",1,1000);numeric(v,"score",0,m);}break;
            case "study":
                subject(o.optString("subject"));text(o,"title",120,true);text(o,"note",2000,false);integer(o,"minutes",1,1440);integer(o,"focus",1,5);break;
            case "tasks":
                subject(o.optString("subject"));text(o,"title",160,true);integer(o,"minutes",1,1440);integer(o,"priority",1,3);require(o.opt("done") instanceof Boolean,"任务状态错误");
                if(o.has("actualMinutes"))integer(o,"actualMinutes",0,1440);if(o.has("status"))oneOf(o.optString("status"),STATUS);
                if(!o.optString("completedAt").isEmpty())date(o.optString("completedAt"));break;
            case "weak":
                subject(o.optString("subject"));text(o,"title",160,true);text(o,"note",2000,false);oneOf(o.optString("cause"),CAUSES);oneOf(o.optString("mastery"),MASTERY);break;
            case "reports":
                text(o,"title",100,true);text(o,"type",40,true);text(o,"body",60000,true);text(o,"model",100,true);text(o,"snapshot",80000,false);
                numeric(o,"inputTokens",0,100000000);numeric(o,"outputTokens",0,100000000);numeric(o,"totalTokens",0,200000000);break;
            case "usage":
                text(o,"type",40,true);text(o,"model",100,true);text(o,"status",200,true);
                numeric(o,"inputTokens",0,100000000);numeric(o,"outputTokens",0,100000000);numeric(o,"totalTokens",0,200000000);break;
            default:throw new IllegalArgumentException("不支持的数据类型");
        }
    }
    static double total(JSONObject exam,boolean maximum) {
        JSONObject scores=exam.optJSONObject("scores");if(scores==null)return 0;
        double sum=0;Iterator<String> keys=scores.keys();while(keys.hasNext()){JSONObject v=scores.optJSONObject(keys.next());if(v!=null)sum+=v.optDouble(maximum?"max":"score",0);}return sum;
    }
    static double percent(JSONObject exam,String subject){
        if(subject.equals("总分")){double m=total(exam,true);return m>0?100*total(exam,false)/m:0;}
        JSONObject v=exam.optJSONObject("scores").optJSONObject(subject);return v==null?Double.NaN:100*v.optDouble("score")/v.optDouble("max");
    }
    static void subject(String s){require(!s.trim().isEmpty()&&s.length()<=20,"科目名称须为 1～20 个字");}
    static JSONObject defaultProfile(){JSONArray a=new JSONArray();String[] names={"语文","数学","英语","物理","化学","地理"};for(int i=0;i<names.length;i++)a.put(obj("name",names[i],"max",i<3?150:100,"target",0,"targetDate",""));return obj("dailyMinutes",120,"targetScore",600,"examDate","2027-06-07","grade","高三","subjects",a,"theme","跟随系统","aiEnabled",true,"model","deepseek-flash","maxTokens",2048,"inputPrice",0,"outputPrice",0);}
    static void loadSubjects(JSONObject p){JSONArray a=p.optJSONArray("subjects");if(a==null)return;String[] result=new String[a.length()];for(int i=0;i<a.length();i++)result[i]=a.optJSONObject(i).optString("name");SUBJECTS=result;}
    static JSONObject subjectConfig(JSONObject p,String name){JSONArray a=p.optJSONArray("subjects");if(a!=null)for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optString("name").equals(name))return a.optJSONObject(i);return obj("name",name,"max",100,"target",0,"targetDate","");}
    static void validateProfile(JSONObject p){integer(p,"dailyMinutes",10,720);integer(p,"targetScore",1,20000);date(p.optString("examDate"));
        JSONArray a=p.optJSONArray("subjects");if(a!=null){require(a.length()>0&&a.length()<=20,"科目数量须为 1～20");HashSet<String> names=new HashSet<>();for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);require(s!=null,"科目设置错误");subject(s.optString("name"));require(names.add(s.optString("name")),"科目重复");double max=numeric(s,"max",1,1000);numeric(s,"target",0,max);if(!s.optString("targetDate").isEmpty())date(s.optString("targetDate"));}}
        if(p.has("maxTokens"))integer(p,"maxTokens",256,8192);if(p.has("model")){text(p,"model",100,true);require(p.optString("model").matches("[A-Za-z0-9._:-]+"),"模型名称格式不正确");}
        if(p.has("inputPrice"))numeric(p,"inputPrice",0,100000);if(p.has("outputPrice"))numeric(p,"outputPrice",0,100000);
    }
}
