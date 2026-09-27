package cn.pegasus.setup;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

/** Adds visible touch feedback without replacing the view's normal background. */
final class KpaTouchFeedback {
    private KpaTouchFeedback() { }

    static void apply(View view) {
        Drawable normal=view.getBackground();
        if(normal instanceof RippleDrawable)return;
        if(normal==null)normal=new ColorDrawable(Color.TRANSPARENT);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x66ffffff),normal,null));
    }
}
