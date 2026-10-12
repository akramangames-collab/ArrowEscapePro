package com.arrowescape.pro;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class BoosterIcon extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind,color;
    BoosterIcon(int kind,int color){this.kind=kind;this.color=color;}
    @Override public void draw(Canvas c){
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/48f,getBounds().height()/48f);p.setStyle(Paint.Style.FILL);p.setColor(color);
        if(kind==1){c.save();c.rotate(-28,24,24);c.drawRoundRect(new RectF(13,8,35,39),5,5,p);p.setColor(0xFFDFF5FF);c.drawRoundRect(new RectF(13,30,35,39),3,3,p);p.setColor(0x55FFFFFF);c.drawRect(16,13,32,17,p);c.restore();}
        else if(kind==2){c.drawCircle(23,28,15,p);c.drawRoundRect(new RectF(20,9,29,16),2,2,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawArc(new RectF(25,3,39,17),170,140,false,p);p.setStyle(Paint.Style.FILL);p.setColor(0x77FFFFFF);c.drawCircle(18,22,4,p);p.setColor(0xFFFFD76B);c.drawCircle(39,7,3,p);}
        else {p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(7,16,39,16,p);c.drawLine(9,32,39,32,p);p.setStyle(Paint.Style.FILL);Path a=new Path();a.moveTo(42,16);a.lineTo(32,8);a.lineTo(32,24);a.close();c.drawPath(a,p);a.reset();a.moveTo(6,32);a.lineTo(16,24);a.lineTo(16,40);a.close();c.drawPath(a,p);}
        p.setStyle(Paint.Style.FILL);c.restore();
    }
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
