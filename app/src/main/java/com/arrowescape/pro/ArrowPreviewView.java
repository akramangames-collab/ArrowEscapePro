package com.arrowescape.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A real vector arrow preview; it never changes the equipped game style. */
final class ArrowPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int style, theme;
    ArrowPreviewView(Context context,int style,int theme) {
        super(context); this.style=style; this.theme=theme;
        setContentDescription("Bent arrow appearance preview");
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int[] backgrounds={0xFF08172D,0xFF05192F,0xFF220D1F,0xFF051F1D,0xFF050718,0xFF260C0A};
        canvas.drawColor(backgrounds[Math.floorMod(theme,backgrounds.length)]);
        paint.setColor(0xFF23466E);
        for(float x=dp(16);x<getWidth();x+=dp(18))for(float y=dp(16);y<getHeight();y+=dp(18))canvas.drawCircle(x,y,dp(1),paint);
        float x=getWidth()*.2f,y=getHeight()*.65f,tip=getWidth()*.8f;
        Path path=new Path();path.moveTo(x,y);path.lineTo(getWidth()*.45f,y);path.lineTo(getWidth()*.45f,getHeight()*.35f);path.lineTo(tip,getHeight()*.35f);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        if(style==4){paint.setColor(0x5537E4ED);paint.setStrokeWidth(dp(12));canvas.drawPath(path,paint);}
        paint.setColor(0xFF31DFEF);paint.setStrokeWidth(dp(style==1?2:style==2?4:3));canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);
        Path head=new Path();float ty=getHeight()*.35f;head.moveTo(tip+dp(7),ty);head.lineTo(tip-dp(5),ty-dp(7));head.lineTo(tip-dp(5),ty+dp(7));if(style==3){head.reset();head.moveTo(tip-dp(6),ty-dp(7));head.lineTo(tip+dp(3),ty);head.lineTo(tip-dp(6),ty+dp(7));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));}else head.close();canvas.drawPath(head,paint);paint.setStyle(Paint.Style.FILL);
        if(style==1){paint.setColor(0x8863DBF5);for(int i=1;i<=5;i++)canvas.drawCircle(tip-dp(12+i*6),ty,dp(2)*(6-i)/5,paint);}
    }
}
