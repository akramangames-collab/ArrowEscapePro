package com.arrowescape.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Two-arrow, unscored practice puzzle, completely separate from campaign data. */
final class TutorialPracticeView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable complete;
    private boolean blockerRemoved, cleared;
    private String message="Tap the upward arrow first";
    TutorialPracticeView(Context context,Runnable complete){super(context);this.complete=complete;setFocusable(true);setContentDescription("Practice: clear the upward arrow, then the right arrow. No hearts or coins are used.");}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){
        c.drawColor(0xFF08172D);float w=getWidth(),h=getHeight();
        paint.setColor(0xFF23466E);
        for(float x=dp(16);x<w;x+=dp(20))for(float y=dp(16);y<h-dp(42);y+=dp(20))c.drawCircle(x,y,dp(1),paint);
        if(!cleared)arrow(c,new float[]{w*.18f,h*.55f,w*.18f,h*.4f,w*.55f,h*.4f},1,0,0xFF27D8EE);
        if(!blockerRemoved)arrow(c,new float[]{w*.82f,h*.6f,w*.68f,h*.6f,w*.68f,h*.2f},0,-1,0xFFB091EF);
        paint.setColor(0xFFDCEDF9);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(13));c.drawText(message,w/2,h-dp(17),paint);
    }
    private void arrow(Canvas c,float[] pts,int dx,int dy,int color){
        Path path=new Path();path.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(color);c.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);float x=pts[pts.length-2],y=pts[pts.length-1],s=dp(8);Path head=new Path();head.moveTo(x+dx*s,y+dy*s);head.lineTo(x-dx*s-dy*s*.7f,y-dy*s+dx*s*.7f);head.lineTo(x-dx*s+dy*s*.7f,y-dy*s-dx*s*.7f);head.close();c.drawPath(head,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;performClick();
        float x=e.getX()/getWidth(),y=e.getY()/getHeight();
        if(!blockerRemoved&&((Math.abs(x-.68f)<.1f&&y>.12f&&y<.67f)||(x>.60f&&x<.90f&&Math.abs(y-.6f)<.08f))){clearBlocker();}
        else if(!cleared&&((Math.abs(x-.18f)<.1f&&y>.32f&&y<.62f)||(x>.1f&&x<.61f&&Math.abs(y-.4f)<.08f))){
            if(!blockerRemoved){message="Blocked: clear the purple arrow";invalidate();announceForAccessibility(message);}else clearRemaining();
        }
        return true;
    }
    private void clearBlocker(){blockerRemoved=true;message="Clear path! Tap the right arrow";invalidate();announceForAccessibility(message);}
    private void clearRemaining(){if(cleared)return;cleared=true;invalidate();complete.run();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,blockerRemoved?"Clear the right arrow":"Clear the upward arrow"));}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){if(action==AccessibilityNodeInfo.ACTION_CLICK){if(!blockerRemoved)clearBlocker();else clearRemaining();return true;}return super.performAccessibilityAction(action,args);}
}
