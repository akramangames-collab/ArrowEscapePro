package com.arrowescape.pro;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;

/** Best-effort local daytime reminders. No exact alarm privilege or background polling. */
public final class RescueReminder extends BroadcastReceiver {
    static boolean active;
    private static final String CHANNEL="daily_rescue";
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("arrow_escape_pro",Context.MODE_PRIVATE);}
    static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,91,new Intent(c,RescueReminder.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void schedule(Context c){
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);manager.cancel(alarm(c));
        var p=prefs(c);if(!p.getBoolean("notifications",false))return;
        ZonedDateTime now=ZonedDateTime.now();boolean frequent=p.getBoolean("reminders_frequent",false);
        boolean played=p.getLong("last_played_local_day",-1)==GameDay.today();
        ZonedDateTime next=null;
        for(int d=0;d<3&&next==null;d++)for(int h:new int[]{10,12,14,16,18,20}){
            if(!frequent&&h!=18)continue;if(d==0&&played)continue;
            ZonedDateTime candidate=now.toLocalDate().plusDays(d).atTime(h,0).atZone(now.getZone());
            if(candidate.isAfter(now)){next=candidate;break;}
        }
        if(next!=null)manager.setWindow(AlarmManager.RTC,next.toInstant().toEpochMilli(),30*60*1000L,alarm(c));
    }
    public static void played(Context c){prefs(c).edit().putLong("last_played_local_day",GameDay.today()).commit();((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(91);schedule(c);}
    @Override public void onReceive(Context c,Intent intent){
        if(!"android.intent.action.BOOT_COMPLETED".equals(intent.getAction())&&!"android.intent.action.TIMEZONE_CHANGED".equals(intent.getAction())&&!"android.intent.action.TIME_SET".equals(intent.getAction()))deliver(c);
        schedule(c);
    }
    private void deliver(Context c){
        var p=prefs(c);long now=System.currentTimeMillis();int hour=LocalTime.now().getHour();
        if(!p.getBoolean("notifications",false)||active||hour<10||hour>=21||p.getLong("last_played_local_day",-1)==GameDay.today()||now-p.getLong("last_notification_time",0)<7200000L)return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        boolean daily=p.getBoolean("daily_reminders",false);boolean streak=p.getBoolean("streak_reminders",false);
        if(!daily&&!streak)return;
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(!nm.areNotificationsEnabled())return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Rescue reminders",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(c,MainActivity.class).putExtra("open_daily",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap=PendingIntent.getActivity(c,91,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Arrow Escape").setContentText(daily?"Your daily rescue challenge is ready!":"A rescue puzzle is waiting for you!").setContentIntent(tap).setAutoCancel(true);
        if(p.edit().putLong("last_notification_time",now).commit())nm.notify(91,b.build());
    }
}
