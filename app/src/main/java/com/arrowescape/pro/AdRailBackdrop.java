package com.arrowescape.pro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;

/** Labels only the non-clickable buffer above the SDK-owned ad creative. */
final class AdRailBackdrop extends GradientDrawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float baseline;
    AdRailBackdrop(float textSize,float baseline){setColor(0xFF050B1E);paint.setColor(0xFF8AA5C5);paint.setTextSize(textSize);paint.setTextAlign(Paint.Align.CENTER);this.baseline=baseline;}
    @Override public void draw(Canvas canvas){super.draw(canvas);canvas.drawText("ADVERTISEMENT",getBounds().centerX(),getBounds().top+baseline,paint);}
}
