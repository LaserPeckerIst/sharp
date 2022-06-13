package com.angcyo.svg;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pixplicity.sharp.OnSvgElementListener;

/**
 * @author <a href="mailto:angcyo@126.com">angcyo</a>
 * @since 2022/06/13
 */
public class SvgElementListener implements OnSvgElementListener {
    @Override
    public void onSvgStart(@NonNull Canvas canvas, @Nullable RectF bounds) {

    }

    @Override
    public void onSvgEnd(@NonNull Canvas canvas, @Nullable RectF bounds) {

    }

    @Override
    public <T> T onSvgElement(@Nullable String id, @NonNull T element, @Nullable RectF elementBounds, @NonNull Canvas canvas, @Nullable RectF canvasBounds, @Nullable Paint paint) {
        return element;
    }

    @Override
    public <T> void onSvgElementDrawn(@Nullable String id, @NonNull T element, @NonNull Canvas canvas, @Nullable Paint paint) {

    }

    @Override
    public boolean onCanvasDraw(Canvas canvas, DrawElement drawElement) {
        return false;
    }
}
