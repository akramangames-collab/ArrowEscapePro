package com.arrowescape.pro;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Restored visual cage, character, key box and moving-key sequence from the v42 rescue feature. */
final class RescueCageScene {
    private static final int GOLD=0xFFFFC83D;
    private static final int WHITE=0xFFF9FAFB;
    private static final int MUTED=0xFFA5BED8;
    private RescueCageScene(){}
    static void draw(Canvas c,Paint p,CageRescue rescue,RescueCharacterArt character,
                     float cx,float cy,float unit,float sy,float progress,long now,
                     boolean reduceMotion){
        c.save();
        RectF shelter=new RectF(cx-unit*4.35f,cy-sy*4.35f,cx+unit*4.35f,cy+sy*4.35f);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new android.graphics.RadialGradient(cx,cy,sy*5f,
                new int[]{0xFF17435D,0xFF10263F,0xFF0B192D},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(shelter,unit*.9f,unit*.9f,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(unit*.10f);p.setColor(0xFF447A91);
        c.drawRoundRect(shelter,unit*.9f,unit*.9f,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0x3322D3EE);
        c.drawOval(new RectF(cx-unit*3.6f,cy+sy*1.55f,cx+unit*3.6f,cy+sy*2.85f),p);
        p.setColor(0xFF12394D);
        c.drawOval(new RectF(cx-unit*2.7f,cy+sy*1.85f,cx+unit*2.7f,cy+sy*2.65f),p);
        RectF ring=new RectF(cx-unit*2.7f,cy+sy*1.85f,cx+unit*2.7f,cy+sy*2.65f);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(unit*.12f);p.setColor(0xFF1B637A);
        c.drawOval(ring,p);p.setColor(GOLD);c.drawArc(ring,-90,Math.max(12,360*progress),false,p);
        int expression=rescue.cageOpened()?RescueCharacterArt.HAPPY:
                progress>=.35f?RescueCharacterArt.HOPEFUL:RescueCharacterArt.WORRIED;
        float bounce=reduceMotion?0f:(float)Math.sin(now/480.0)*sy*.055f;
        character.draw(c,expression,cx,cy+sy*2.26f+bounce,sy*5.35f,unit*5.4f);
        drawCage(c,p,rescue,cx,cy,unit,sy);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFF233E55);
        RectF bubble=new RectF(cx-unit*3.85f,cy-sy*4.04f,cx+unit*3.85f,cy-sy*3.27f);
        c.drawRoundRect(bubble,unit*.3f,unit*.3f,p);
        p.setColor(WHITE);p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(unit*.43f);p.setFakeBoldText(true);
        String message=rescue.cageOpened()?"I'm free!":rescue.boxOpened()?
                "Here comes the key!":"Find the key!";
        c.drawText(message,cx,bubble.centerY()+unit*.15f,p);p.setFakeBoldText(false);
        drawKeyBox(c,p,rescue,cx,cy,unit,sy);
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(unit*.39f);p.setColor(rescue.cageOpened()?GOLD:MUTED);
        c.drawText(rescue.cageOpened()?"Cage open!":rescue.boxOpened()?"Unlocking cage...":"Find gold arrow",
                cx-unit*3.65f,cy+sy*3.25f,p);
        p.setColor(MUTED);p.setTextSize(unit*.34f);
        c.drawText(Math.round(progress*100)+"% CLEAR",cx-unit*3.65f,cy+sy*3.82f,p);
        p.setStyle(Paint.Style.FILL);c.restore();
    }
    private static void drawCage(Canvas c,Paint p,CageRescue r,float cx,float cy,float u,float sy){
        RectF frame=new RectF(cx-u*3.5f,cy-sy*2.94f,cx+u*3.5f,cy+sy*2.63f);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(u*.17f);
        p.setShader(new LinearGradient(0,frame.top,0,frame.bottom,0xFFB1CAD6,0xFF456680,Shader.TileMode.CLAMP));
        c.drawRoundRect(frame,u*.65f,sy*.58f,p);
        c.drawLine(frame.left,cy-sy*2.15f,frame.right,cy-sy*2.15f,p);
        float open=r.doorProgress(), left=frame.left+u*.16f,right=frame.right-u*.16f;
        for(int i=1;i<=3;i++){
            float offset=u*(i*.95f+open*(3.34f-i*.95f));
            c.drawLine(cx-offset,cy-sy*2.1f,cx-offset,cy+sy*2.48f,p);
            c.drawLine(cx+offset,cy-sy*2.1f,cx+offset,cy+sy*2.48f,p);
        }
        c.drawRoundRect(new RectF(left,cy-sy*2.1f,left+(cx-left)*(1-open),cy+sy*2.48f),u*.10f,u*.10f,p);
        c.drawRoundRect(new RectF(right-(right-cx)*(1-open),cy-sy*2.1f,right,cy+sy*2.48f),u*.10f,u*.10f,p);
        p.setShader(null);
        float lockX=cx+u*3.34f*open,lockY=cy+sy*1.15f;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.12f);p.setColor(GOLD);
        c.drawArc(new RectF(lockX-u*.32f,lockY-sy*.50f,lockX+u*.32f,lockY+sy*.16f),
                180,180-65*open,false,p);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(lockX-u*.50f,lockY,lockX+u*.50f,lockY+sy*.66f),u*.12f,u*.12f,p);
        p.setColor(0xFF352B10);c.drawCircle(lockX,lockY+sy*.25f,u*.10f,p);
        c.drawRect(lockX-u*.055f,lockY+sy*.26f,lockX+u*.055f,lockY+sy*.48f,p);
        p.setStrokeCap(Paint.Cap.SQUARE);
    }
    private static void drawKeyBox(Canvas c,Paint p,CageRescue r,float cx,float cy,float u,float sy){
        float x=cx+u*2.8f,y=cy+sy*3.35f;
        RectF body=new RectF(x-u*.85f,y-sy*.42f,x+u*.85f,y+sy*.42f);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFF5C3F1F);
        c.drawRoundRect(body,u*.12f,u*.12f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u*.08f);p.setColor(GOLD);
        c.drawRoundRect(body,u*.12f,u*.12f,p);
        c.save();c.rotate(-55*r.lidProgress(),body.left,body.top);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFFA27632);
        c.drawRoundRect(new RectF(body.left,body.top-sy*.24f,body.right,body.top+sy*.10f),u*.10f,u*.10f,p);
        c.restore();
        p.setColor(GOLD);
        c.drawRoundRect(new RectF(x-u*.17f,y-sy*.2f,x+u*.17f,y+sy*.21f),u*.04f,u*.04f,p);
        p.setColor(0xFF392A15);c.drawCircle(x,y-sy*.035f,u*.07f,p);
        p.setTextAlign(Paint.Align.CENTER);p.setColor(MUTED);p.setTextSize(u*.31f);
        c.drawText("KEY BOX",x,cy+sy*4.08f,p);
        if(r.boxOpened()&&r.doorProgress()<1){
            float t=r.keyProgress(),ease=t*t*(3-2*t),door=r.doorProgress();
            float keyX=x+(cx-x)*ease+u*3.34f*door;
            float keyY=y+(cy+sy*1.40f-y)*ease-sy*1.2f*(float)Math.sin(Math.PI*t);
            c.save();c.rotate(-18+18*t,keyX,keyY);
            drawKey(c,p,keyX-u*.7125f,keyY,u*.75f);c.restore();
        }
        p.setStyle(Paint.Style.FILL);
    }
    static void drawKey(Canvas c,Paint p,float x,float y,float size){
        p.setColor(GOLD);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(size*.15f);
        c.drawCircle(x,y,size*.25f,p);c.drawLine(x+size*.23f,y,x+size*.95f,y,p);
        c.drawLine(x+size*.72f,y,x+size*.72f,y+size*.25f,p);
        c.drawLine(x+size*.95f,y,x+size*.95f,y+size*.25f,p);
        p.setStyle(Paint.Style.FILL);
    }
}
