package com.arrowescape.pro;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;

/** Bundled type avoids OEM custom fonts changing the approved visual hierarchy. */
final class AppTypography {
    private static Typeface regular,bold;
    private static Typeface load(Context context,int weight){
        if(Build.VERSION.SDK_INT>=26)return new Typeface.Builder(context.getAssets(),"fonts/manrope.ttf")
            .setWeight(weight).setFontVariationSettings("'wght' "+weight).build();
        return Typeface.create(Typeface.createFromAsset(context.getAssets(),"fonts/manrope.ttf"),weight>=700?Typeface.BOLD:Typeface.NORMAL);
    }
    static Typeface regular(Context context){if(regular==null)regular=load(context,500);return regular;}
    static Typeface bold(Context context){if(bold==null)bold=load(context,800);return bold;}
    static Typeface base(Context context){return regular(context);}
}
