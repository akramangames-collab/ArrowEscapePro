package com.arrowescape.pro;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Lightweight animated rescue stage using the same mascot as the game identity. */
final class RescueHeroView extends View {
 private final Paint p=new Paint(3);private RescueCharacterArt art;private final Path shape=new Path();
 RescueHeroView(Context c){super(c);setContentDescription("Rescue mascot in a golden cage, surrounded by escape arrows");}
 @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2,cy=h*.46f,u=Math.min(w/12,h/7);
  boolean reduce=getContext().getSharedPreferences("arrow_escape_pro",0).getBoolean("reduce_motion",false);float phase=reduce?0:android.os.SystemClock.uptimeMillis()/1100f;
  p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx,cy,u*5,new int[]{0x5551E0C8,0x0005262D},null,Shader.TileMode.CLAMP));c.drawOval(cx-u*5,cy-u*3,cx+u*5,cy+u*3,p);p.setShader(null);
  p.setColor(0xFF0C6470);c.drawOval(cx-u*4.2f,cy+u*2,cx+u*4.2f,cy+u*3,p);p.setColor(0xFF28A8AC);c.drawOval(cx-u*3.6f,cy+u*2,cx+u*3.6f,cy+u*2.65f,p);
  p.setColor(0xFF163F42);c.drawRoundRect(cx-u*2.5f,cy+u*1.7f,cx+u*2.5f,cy+u*2.4f,u*.4f,u*.4f,p);p.setColor(0xFF2E7570);c.drawOval(cx-u*2.5f,cy+u*1.35f,cx+u*2.5f,cy+u*2.1f,p);
  p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(u*.3f);p.setColor(0xFF35DAD7);
  shape.reset();shape.moveTo(cx-u*3.1f,cy+u*1.5f);shape.cubicTo(cx-u*5.1f,cy,cx-u*4.4f,cy-u*2.8f,cx-u*2.4f,cy-u*2.8f);c.drawPath(shape,p);
  p.setStyle(Paint.Style.FILL);shape.reset();shape.moveTo(cx-u*2.2f,cy-u*2.8f);shape.lineTo(cx-u*2.9f,cy-u*3.25f);shape.lineTo(cx-u*2.9f,cy-u*2.35f);shape.close();c.drawPath(shape,p);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.14f);p.setColor(0xFFFFD778);c.drawRoundRect(cx-u*1.9f,cy-u*1.9f,cx+u*1.9f,cy+u*1.8f,u*.35f,u*.35f,p);c.drawArc(cx-u*1.85f,cy-u*3.1f,cx+u*1.85f,cy-u*.8f,180,180,false,p);
  if(art==null)art=new RescueCharacterArt(getContext());art.draw(c,RescueCharacterArt.HOPEFUL,cx,cy+u*1.6f,u*3.5f,u*2.9f);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.12f);p.setColor(0xFFE5A63C);for(int i:new int[]{-1,1})c.drawLine(cx+i*u*1.25f,cy-u*1.9f,cx+i*u*1.25f,cy+u*1.8f,p);
  p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFCC66);c.drawRoundRect(cx-u*2.1f,cy+u*1.7f,cx+u*2.1f,cy+u*2,u*.14f,u*.14f,p);
  float keyX=cx+u*3.65f,keyY=cy-u*1.7f+(float)Math.sin(phase)*u*.12f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.17f);c.drawCircle(keyX,keyY,u*.37f,p);c.drawLine(keyX,keyY+u*.35f,keyX,keyY+u*1.25f,p);c.drawLine(keyX,keyY+u*.9f,keyX+u*.3f,keyY+u*.9f,p);p.setStyle(Paint.Style.FILL);
  p.setColor(0xFF63DAD0);for(int i=0;i<8;i++){float a=i*.79f+phase*.08f;c.drawCircle(cx+(float)Math.cos(a)*u*4.6f,cy+(float)Math.sin(a)*u*2.3f,u*.04f,p);}
  if(!reduce&&isShown()&&hasWindowFocus())postInvalidateDelayed(80);
 }
 @Override protected void onDetachedFromWindow(){if(art!=null){art.release();art=null;}super.onDetachedFromWindow();}
}
