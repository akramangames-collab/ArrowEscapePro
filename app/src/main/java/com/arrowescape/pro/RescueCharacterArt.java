package com.arrowescape.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.IOException;
import java.io.InputStream;

/** One transparent atlas, decoded once; expressions share a consistent foot anchor. */
final class RescueCharacterArt {
    static final int WORRIED=0, STARTLED=1, HOPEFUL=2, HAPPY=3;
    private final Bitmap atlas;
    private final Rect[] frames=new Rect[4];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    RescueCharacterArt(Context context) {
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inScaled=false;
        options.inSampleSize=2;
        try(InputStream stream=context.getAssets().open("characters/rescue-boy-expressions.png")) {
            atlas=BitmapFactory.decodeStream(stream,null,options);
        } catch(IOException e) {
            throw new IllegalStateException("Bundled rescue character is missing",e);
        }
        if(atlas==null)throw new IllegalStateException("Invalid rescue character atlas");
        // Trim transparent padding only at draw time, without modifying the asset.
        for(int state=0;state<4;state++) {
            int left=(state%2)*atlas.getWidth()/2, right=(state%2+1)*atlas.getWidth()/2;
            int top=(state/2)*atlas.getHeight()/2, bottom=(state/2+1)*atlas.getHeight()/2;
            int x0=right,y0=bottom,x1=left,y1=top;
            for(int y=top;y<bottom;y++)for(int x=left;x<right;x++) {
                if((atlas.getPixel(x,y)>>>24)>24) {
                    x0=Math.min(x0,x);x1=Math.max(x1,x+1);
                    y0=Math.min(y0,y);y1=Math.max(y1,y+1);
                }
            }
            frames[state]=x0<x1&&y0<y1?new Rect(x0,y0,x1,y1):new Rect(left,top,right,bottom);
        }
    }

    void draw(Canvas canvas,int state,float centerX,float feetY,float height,float maxWidth) {
        if(atlas.isRecycled())return;
        Rect source=frames[Math.max(0,Math.min(3,state))];
        float scale=Math.min(height/source.height(),maxWidth/source.width());
        float width=source.width()*scale,drawHeight=source.height()*scale;
        canvas.drawBitmap(atlas,source,new RectF(centerX-width/2,feetY-drawHeight,centerX+width/2,feetY),paint);
    }

    void release(){if(!atlas.isRecycled())atlas.recycle();}
}
