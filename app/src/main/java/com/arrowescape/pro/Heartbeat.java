package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;

/** Fictional rescue tension, driven solely by the game's danger fraction. */
final class Heartbeat {
 private final Context context;private final SharedPreferences prefs;private ToneGenerator tone;private long last;
 Heartbeat(Context c,SharedPreferences p){context=c;prefs=p;}
 static int interval(float danger){return (int)(1200-700*Math.max(0,Math.min(1,danger)));}
 void tick(float danger,long now){
  if(now-last<interval(danger))return;last=now;
  AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
  if(audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL)return;
  if(prefs.getBoolean("heartbeat_sound",false)&&prefs.getBoolean("sound",true)){
   if(tone==null)tone=new ToneGenerator(AudioManager.STREAM_MUSIC,12);
   tone.startTone(ToneGenerator.TONE_PROP_BEEP,45);
  }
  if(danger>.55f&&prefs.getBoolean("heartbeat_vibration",false)&&prefs.getBoolean("haptics",true)&&!prefs.getBoolean("reduce_motion",false)){
   android.os.Vibrator vibrator=(android.os.Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
   if(vibrator!=null&&vibrator.hasVibrator()){
    int strength=prefs.getInt("heartbeat_intensity",1);int amplitude=strength==0?25:strength==2?70:45;
    if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(18,amplitude));
   }
  }
 }
 void release(){if(tone!=null){tone.release();tone=null;}}
}
