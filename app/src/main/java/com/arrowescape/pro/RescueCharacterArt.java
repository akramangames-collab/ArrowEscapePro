package com.arrowescape.pro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Drawable rescue character that can never fail to load because of missing artwork. */
final class RescueCharacterArt {
    static final int WORRIED=0, STARTLED=1, HOPEFUL=2, HAPPY=3;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    RescueCharacterArt(android.content.Context unused){}
    void draw(Canvas c,int state,float cx,float feet,float height,float maxWidth){
        float s=Math.min(height/110f,maxWidth/62f);
        c.save();c.translate(cx,feet);c.scale(s,s);c.translate(0,-110);
        p.setStyle(Paint.Style.FILL);p.setColor(0x33000000);
        c.drawOval(new RectF(-25,105,25,112),p);
        p.setColor(0xFF334659);c.drawRoundRect(new RectF(-18,88,-5,108),4,4,p);
        c.drawRoundRect(new RectF(5,88,18,108),4,4,p);
        p.setColor(0xFFF4E2BF);
        c.drawRoundRect(new RectF(-27,102,-2,109),5,5,p);
        c.drawRoundRect(new RectF(2,102,27,109),5,5,p);
        p.setColor(0xFF007EB9);c.drawRoundRect(new RectF(-24,52,24,97),11,11,p);
        p.setColor(0xFF2AA8D5);c.drawRoundRect(new RectF(-18,55,18,94),8,8,p);
        p.setColor(0xFFF2BC83);
        c.drawCircle(0,34,24,p);c.drawOval(new RectF(-26,28,-20,42),p);
        c.drawOval(new RectF(20,28,26,42),p);
        p.setColor(0xFF35261E);
        c.drawOval(new RectF(-24,10,23,24),p);
        Path hair=new Path();hair.moveTo(-24,22);hair.quadTo(-30,2,-8,7);
        hair.quadTo(7,-2,14,8);hair.quadTo(28,4,22,29);hair.lineTo(16,18);
        hair.quadTo(-1,26,-13,19);hair.lineTo(-24,29);hair.close();c.drawPath(hair,p);
        p.setColor(0xFF1B2E3B);
        if(state==STARTLED){
            c.drawCircle(-9,35,3.9f,p);c.drawCircle(9,35,3.9f,p);
            p.setColor(0xFF7B3D2F);c.drawOval(new RectF(-4,43,4,53),p);
        }else{
            c.drawOval(new RectF(-12,31,-7,38),p);c.drawOval(new RectF(7,31,12,38),p);
            p.setColor(0xFF7B3D2F);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);
            RectF mouth=state==HAPPY?new RectF(-8,40,8,52):new RectF(-6,47,6,53);
            c.drawArc(mouth,state==WORRIED?180:0,180,false,p);p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xFFF2BC83);
        c.drawRoundRect(new RectF(-29,67,-19,84),4,4,p);
        c.drawRoundRect(new RectF(19,67,29,84),4,4,p);
        p.setColor(0xFF0D5A8D);c.drawRoundRect(new RectF(-26,61,-17,76),4,4,p);
        c.drawRoundRect(new RectF(17,61,26,76),4,4,p);
        p.setColor(0xFFF3D66C);c.drawCircle(0,63,3,p);
        c.restore();
    }
    void release(){}
}
