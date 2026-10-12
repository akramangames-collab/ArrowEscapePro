package com.arrowescape.pro;

/** Board-space targeting; independent of zoom and display density. */
public final class BoosterGeometry {
    private BoosterGeometry() {}
    public static boolean circleTouchesSegment(float cx, float cy, float radius,
            float ax, float ay, float bx, float by) {
        float dx=bx-ax,dy=by-ay,length=dx*dx+dy*dy;
        float t=length==0?0:Math.max(0,Math.min(1,((cx-ax)*dx+(cy-ay)*dy)/length));
        float x=ax+t*dx-cx,y=ay+t*dy-cy;
        return x*x+y*y<=radius*radius;
    }
    public static boolean rowTouchesSegment(float row, float halfWidth,
            float ay, float by) {
        return Math.min(ay,by)<=row+halfWidth && Math.max(ay,by)>=row-halfWidth;
    }
}
