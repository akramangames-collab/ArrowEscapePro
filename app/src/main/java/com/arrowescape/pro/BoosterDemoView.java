package com.arrowescape.pro;
import android.content.Context;
import android.graphics.*;
import android.view.*;
/** Free, isolated demonstration. Straight arrow paths are disjoint and fully clear. */
final class BoosterDemoView extends View {
 private final int kind;private final Paint p=new Paint(3);private final boolean[] removed=new boolean[4];private long started;private boolean running;private String message="Tap RUN DEMO, or tap an arrow to practice";
 BoosterDemoView(Context c,int k){super(c);kind=k;setContentDescription("Free "+BoosterInventory.name(k)+" demonstration; no inventory is consumed");setFocusable(true);}
 private float dp(float n){return n*getResources().getDisplayMetrics().density;}
 private void text(Canvas c,String s,float x,float y,int size,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(AppTypography.bold(getContext()));p.setTextSize(dp(size));c.drawText(s,x,y,p);}
 private int remaining(){int n=0;for(int i=0;i<3;i++)if(!removed[i])n++;return n;}
 private void reset(){for(int i=0;i<4;i++)removed[i]=false;running=false;message="Tap RUN DEMO, or tap an arrow to practice";}
 void runDemo(){reset();running=true;started=android.os.SystemClock.uptimeMillis();
  if(kind==1)removed[0]=true;else if(kind==2){removed[0]=true;removed[1]=true;}else if(kind==3)removed[1]=true;
  message=kind==0?"Hint highlights one legal arrow":kind==1?"Eraser removes your eligible target":kind==2?"Bomb clears eligible arrows in its area":kind==3?"Sweep clears eligible arrows on one row":kind==4?"Safe Scan marks all legal arrows for 8s":"Triple Escape checks and releases three arrows";invalidate();
 }
 @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();long elapsed=android.os.SystemClock.uptimeMillis()-started;
  p.setColor(0xFF082E36);c.drawRoundRect(0,0,w,h,dp(22),dp(22),p);
  if(running&&kind==6)for(int i=0;i<3;i++)if(elapsed>450L*(i+1))removed[i]=true;
  text(c,"FREE PRACTICE · STOCK UNCHANGED",w/2,dp(24),10,0xFFE6B95C);
  for(int i=0;i<4;i++){
   float y=dp(53+i*31);if(removed[i]){text(c,"ESCAPED",w/2,y+dp(4),10,0xFF68D8AE);continue;}
   boolean glow=running&&((kind==0&&i==0)||(kind==4&&i<3&&elapsed<8000));int col=i==3?0xFFFFCE5B:glow?0xFFF9F2CF:0xFF38DFD8;
   p.setColor(col);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(glow?7:5));c.drawLine(w*.23f,y,w*.7f,y,p);p.setStyle(Paint.Style.FILL);
   Path head=new Path();head.moveTo(w*.77f,y);head.lineTo(w*.68f,y-dp(9));head.lineTo(w*.68f,y+dp(9));head.close();c.drawPath(head,p);
   if(i==3)text(c,"KEY · LAST",w*.5f,y+dp(19),9,0xFFE6B95C);
  }
  float by=h-dp(47);p.setColor(0xFFFFC75A);c.drawRoundRect(dp(18),by,w-dp(18),h-dp(10),dp(16),dp(16),p);text(c,running?"REPLAY DEMO":"RUN DEMO",w/2,by+dp(24),12,0xFF17322E);
  // Message is available to accessibility services without crowding the board.
  setContentDescription(message+". Golden key stays until the other arrows escape. No stock used.");
  if(running&&kind==6&&elapsed<1600)postInvalidateDelayed(80);else if(running&&kind==4&&elapsed<8000)postInvalidateDelayed(Math.max(100,8000-elapsed));
 }
 @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP){performClick();if(e.getY()>=getHeight()-dp(52)){runDemo();return true;}
  int row=Math.round((e.getY()-dp(53))/dp(31));if(row>=0&&row<4){if(row==3&&remaining()>0){message="Golden key is protected: clear the other arrows first";}else{removed[row]=true;message="Full path clear · arrow escaped";}announceForAccessibility(message);invalidate();}return true;}return true;}
 @Override public boolean performClick(){super.performClick();return true;}
 boolean keyRemoved(){return removed[3];}
}
