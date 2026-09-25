package cn.study.personal;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import java.time.*;
import java.util.Calendar;

/** 纯本地提醒：由用户在「我的 → 通知设置」中开启，默认全部关闭。不联网、不调用 AI。 */
public final class NotifyReceiver extends BroadcastReceiver {
    static final String CHANNEL = "study-remind";
    static final int REQ_PLAN = 1001;
    static final int REQ_EXAM = 1002;

    @Override
    public void onReceive(Context c, Intent i) {
        String kind = i.getStringExtra("kind");
        String text = i.getStringExtra("text");
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "学习提醒", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("你自己开启的本地学习提醒");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setContentTitle("学习总控台")
                .setContentText(text == null ? "" : text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(kind == null ? 1 : Math.abs(kind.hashCode()), b.build());
        schedule(c); // 每日提醒触发后，顺延下一天
    }

    /** 根据 profile 重新排期。全部关闭时取消已排期的提醒。 */
    static void schedule(Context c) {
        cancel(c);
        JSONObject p;
        try {
            p = new Store(c).profile();
        } catch (Exception e) {
            return;
        }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (p.optBoolean("notifPlan", false)) {
            long at = nextDaily(p.optString("notifPlanTime", "21:30"));
            Intent in = new Intent(c, NotifyReceiver.class).putExtra("kind", "plan").putExtra("text", "今天的计划完成了吗？花一分钟记录一下。");
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ_PLAN, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
        if (p.optBoolean("notifExam", false)) {
            try {
                LocalDate exam = LocalDate.parse(p.optString("examDate", ""));
                LocalDate fire = exam.minusDays(Math.max(1, p.optInt("notifExamDays", 7)));
                Calendar cal = Calendar.getInstance();
                cal.set(fire.getYear(), fire.getMonthValue() - 1, fire.getDayOfMonth(), 8, 0, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (cal.getTimeInMillis() > System.currentTimeMillis()) {
                    long left = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), exam);
                    Intent in = new Intent(c, NotifyReceiver.class).putExtra("kind", "exam")
                            .putExtra("text", "距离目标考试还有 " + left + " 天，检查一下今日计划。");
                    PendingIntent pi = PendingIntent.getBroadcast(c, REQ_EXAM, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                }
            } catch (Exception ignored) {
            }
        }
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int req : new int[]{REQ_PLAN, REQ_EXAM}) {
            Intent in = new Intent(c, NotifyReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, req, in, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }

    private static long nextDaily(String hhmm) {
        int h = 21, m = 30;
        try {
            String[] s = hhmm.split(":");
            h = Integer.parseInt(s[0]);
            m = Integer.parseInt(s[1]);
        } catch (Exception ignored) {
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }
}
