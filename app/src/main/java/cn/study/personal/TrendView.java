package cn.study.personal;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

final class TrendView extends View {
    private final float[] values;
    private final String[] labels;
    private final boolean bars;
    private final float scaleMax;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    TrendView(Context c,float[] v,String[] l,boolean b,float max){super(c);values=v;labels=l;bars=b;scaleMax=Math.max(1,max);setContentDescription(b?"近七天每日学习分钟数柱状图":"考试得分率趋势图");setMinimumHeight(dp(185));}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float left=dp(34),right=getWidth()-dp(14),top=dp(18),bottom=getHeight()-dp(30),h=bottom-top;
        p.setTextSize(dp(10));p.setStrokeWidth(dp(1));
        for(int i=0;i<3;i++){float y=top+h*i/2;p.setColor(0xffe6eceb);c.drawLine(left,y,right,y,p);p.setColor(0xff7a8b88);c.drawText(Data.number(scaleMax*(2-i)/2),dp(0),y+dp(4),p);}
        if(values.length==0){p.setColor(0xff7a8b88);p.setTextSize(dp(13));c.drawText("录入成绩后，在这里看到变化",left,top+h/2,p);return;}
        Path line=new Path();float step=(right-left)/Math.max(1,bars?values.length:values.length-1);
        for(int i=0;i<values.length;i++){
            float x=left+step*(bars?i+.5f:(values.length==1?.5f:i));float y=bottom-h*Math.min(scaleMax,values[i])/scaleMax;
            p.setColor(0xff286d63);
            if(bars)c.drawRoundRect(x-step*.28f,y,x+step*.28f,bottom,dp(5),dp(5),p);
            else{if(i==0)line.moveTo(x,y);else line.lineTo(x,y);c.drawCircle(x,y,dp(3.5f),p);}
            p.setTextSize(dp(10));p.setColor(0xff526b66);p.setTextAlign(Paint.Align.CENTER);
            if(values.length<=8||i==0||i==values.length-1||i%3==0)c.drawText(labels[i],x,bottom+dp(20),p);
            if(bars&&values[i]>0)c.drawText(Data.number(values[i]),x,y-dp(6),p);p.setTextAlign(Paint.Align.LEFT);
        }
        if(!bars){p.setColor(0xff286d63);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));c.drawPath(line,p);p.setStyle(Paint.Style.FILL);}
    }
}
