package com.arrowescape.pro;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Original vector cage environment, with the bundled rescue character. */
final class RescueHeroView extends View {
 private final Paint p=new Paint(3);private RescueCharacterArt art;
 RescueHeroView(Context c){super(c);setContentDescription("A hopeful rescue character inside a golden cage");}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2,cy=h*.55f,u=Math.min(w/10,h/6);
  boolean reduce=getContext().getSharedPreferences("arrow_escape_pro",0).getBoolean("reduce_motion",false);
  float phase=reduce?0:android.os.SystemClock.uptimeMillis()/1000f;
  p.setShader(new RadialGradient(cx,cy,u*4,new int[]{0x5522D3EE,0x0010264A},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,u*4,p);p.setShader(null);
  p.setColor(0xFF22D3EE);for(int i=0;i<12;i++){float a=i*.524f+phase*.1f;c.drawCircle(cx+(float)Math.cos(a)*u*3.7f,cy+(float)Math.sin(a)*u*2,2+(i%3),p);}
  if(art==null)art=new RescueCharacterArt(getContext());art.draw(c,RescueCharacterArt.HOPEFUL,cx,cy+u*1.8f,u*3.7f,u*2.9f);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.12f);p.setColor(0xFFE5B869);
  RectF cage=new RectF(cx-u*2,cy-u*2.3f,cx+u*2,cy+u*1.9f);c.drawRoundRect(cage,u*.45f,u*.45f,p);
  for(int i=-1;i<=1;i++)c.drawLine(cx+i*u, cage.top,cx+i*u,cage.bottom,p);
  p.setStyle(Paint.Style.FILL);p.setColor(0xFF22D3EE);c.drawRoundRect(cx-u*2,cy+u*1.8f,cx+u*2,cy+u*2,u*.12f,u*.12f,p);
  if(!reduce&&isShown()&&hasWindowFocus())postInvalidateDelayed(50);
 }
 @Override protected void onDetachedFromWindow(){if(art!=null){art.release();art=null;}super.onDetachedFromWindow();}
}
