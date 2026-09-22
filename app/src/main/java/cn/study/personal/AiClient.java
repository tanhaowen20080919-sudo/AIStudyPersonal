package cn.study.personal;

import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicit foreground button press = at most one HTTP POST. No retries or scheduler. */
final class AiClient {
    static final String ENDPOINT="https://api.deepseek.com/chat/completions";
    private volatile HttpsURLConnection connection;
    private final AtomicBoolean canceled=new AtomicBoolean();
    void cancel(){canceled.set(true);HttpsURLConnection c=connection;if(c!=null)c.disconnect();}
    boolean canceled(){return canceled.get();}
    JSONObject run(String key,String model,int maxTokens,String system,String prompt) throws Exception {
        if(canceled.get())throw new IOException("已取消");
        JSONObject request=Data.obj("model",model,"stream",false,"max_tokens",maxTokens,
            "thinking",Data.obj("type","disabled"),
            "messages",new JSONArray().put(Data.obj("role","system","content",system)).put(Data.obj("role","user","content",prompt)));
        if(system.contains("JSON"))Data.set(request,"response_format",Data.obj("type","json_object"));
        HttpsURLConnection c=(HttpsURLConnection)new URL(ENDPOINT).openConnection();connection=c;
        c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(90000);
        c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        byte[] payload=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(payload.length);
        try{
            if(canceled.get())throw new IOException("已取消");
            try(OutputStream out=c.getOutputStream()){out.write(payload);}
            int code=c.getResponseCode();
            if(code<200||code>=300){
                String reason=code==401?"Key 无效，请重新填写":code==402?"API 余额不足":code==429?"请求过于频繁，请稍后手动重试":code==400?"参数或模型名称不支持，请检查设置":code==503?"DeepSeek 服务繁忙":"服务返回 HTTP "+code;
                throw new IOException(reason+"。未自动重试。");
            }
            try(InputStream in=c.getInputStream()){
                JSONObject result=new JSONObject(read(in,1024*1024));
                if(canceled.get())throw new IOException("已取消");
                return result;
            }
        }finally{c.disconnect();connection=null;}
    }
    static String read(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("文件或响应过大");out.write(buf,0,n);}return out.toString("UTF-8");
    }
}
