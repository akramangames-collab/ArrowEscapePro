package com.arrowescape.pro;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Consistent vector navigation icons, independent of device emoji fonts. */
final class RouteNavIcon extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    RouteNavIcon(int kind,int color){this.kind=kind;p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas c){
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
        p.setStyle(Paint.Style.STROKE);
        if(kind==0){c.drawLine(6,17,12,6,p);c.drawLine(12,6,19,17,p);p.setStyle(Paint.Style.FILL);c.drawCircle(6,17,3,p);c.drawCircle(12,6,3,p);c.drawCircle(19,17,3,p);}
        else if(kind==1){c.drawRoundRect(new RectF(4,5,21,21),3,3,p);c.drawLine(4,10,21,10,p);c.drawLine(8,3,8,7,p);c.drawLine(17,3,17,7,p);p.setStyle(Paint.Style.FILL);for(int x=8;x<20;x+=5)for(int y=14;y<20;y+=4)c.drawCircle(x,y,1,p);}
        else if(kind==2){Path cart=new Path();cart.moveTo(2,4);cart.lineTo(5,4);cart.lineTo(8,16);cart.lineTo(19,16);cart.lineTo(22,7);cart.lineTo(6,7);c.drawPath(cart,p);p.setStyle(Paint.Style.FILL);c.drawCircle(9,21,1.8f,p);c.drawCircle(18,21,1.8f,p);}
        else if(kind==3){c.drawRoundRect(new RectF(7,3,18,14),3,3,p);c.drawArc(new RectF(2,4,9,12),90,180,false,p);c.drawArc(new RectF(16,4,23,12),-90,180,false,p);c.drawLine(12,14,12,20,p);c.drawLine(7,21,18,21,p);}
        else {c.drawCircle(12,7,4,p);c.drawArc(new RectF(4,12,20,27),180,180,false,p);c.drawLine(4,20,20,20,p);}
        c.restore();
    }
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
