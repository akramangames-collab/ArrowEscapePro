package com.arrowescape.pro;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.*;
/** The same original game lockup on the lobby, introduction and developer page. */
final class GameLogoView extends View {
 private Bitmap logo;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 GameLogoView(Context context){super(context);setContentDescription("Arrow Escape: Rescue Puzzle logo");}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
  if(logo==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;try(InputStream in=getContext().getAssets().open("branding/arrow-rescue-logo.png")){logo=BitmapFactory.decodeStream(in,null,o);}catch(IOException e){throw new IllegalStateException("Missing game logo",e);}}
  if(logo==null)return;float s=Math.min(getWidth()/(float)logo.getWidth(),getHeight()/(float)logo.getHeight());float w=logo.getWidth()*s,h=logo.getHeight()*s;
  canvas.drawBitmap(logo,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),p);
 }
 @Override protected void onDetachedFromWindow(){if(logo!=null){logo.recycle();logo=null;}super.onDetachedFromWindow();}
}
