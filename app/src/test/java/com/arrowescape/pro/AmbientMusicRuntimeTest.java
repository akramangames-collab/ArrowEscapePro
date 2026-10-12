package com.arrowescape.pro;

import android.media.AudioManager;
import android.content.Context;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import android.media.MediaPlayer;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaPlayer;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={30,35},application=ArrowApplication.class)
public class AmbientMusicRuntimeTest {
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @Test public void toggleFocusAndForegroundControlPlayback()throws Exception{
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(32000,0));
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            AmbientMusic music=(AmbientMusic)field(c.get(),"music");
            MediaPlayer player=(MediaPlayer)field(music,"player");
            assertNotNull(player);assertTrue(player.isPlaying());assertTrue(player.isLooping());assertEquals(.65f,Shadows.shadowOf(player).getLeftVolume(),.001f);
            c.get().onWindowFocusChanged(true);assertTrue(player.isPlaying());
            AudioManager.OnAudioFocusChangeListener listener=(AudioManager.OnAudioFocusChangeListener)field(music,"listener");
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);assertFalse(player.isPlaying());
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);assertTrue(player.isPlaying());
            music.setEnabled(false);assertFalse(player.isPlaying());music.resume();assertFalse(player.isPlaying());
            music.setEnabled(true);music.resume();assertTrue(player.isPlaying());
            c.pause();assertFalse(player.isPlaying());
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);assertFalse(player.isPlaying());
            c.resume();assertTrue(player.isPlaying());
            music.release();assertNull(field(music,"player"));
        }finally{ShadowMediaPlayer.resetStaticState();}
    }    @Test public void windowFocusRetriesMusicWhenInitialFocusWasDenied()throws Exception{
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(32000,0));
        AudioManager audio=(AudioManager)RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE);
        Shadows.shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            AmbientMusic music=(AmbientMusic)field(c.get(),"music");assertNull(field(music,"player"));
            Shadows.shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            c.get().onWindowFocusChanged(true);MediaPlayer player=(MediaPlayer)field(music,"player");assertNotNull(player);assertTrue(player.isPlaying());
            c.pause();assertFalse(player.isPlaying());
        }finally{ShadowMediaPlayer.resetStaticState();}
    }

}
