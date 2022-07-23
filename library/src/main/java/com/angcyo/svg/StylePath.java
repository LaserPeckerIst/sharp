package com.angcyo.svg;

import android.graphics.Paint;
import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 用来存储一些其他数据的Path
 * <p>
 * 用来标识[Path]是[STROKE] [FILL] [FILL_AND_STROKE]
 *
 * @author <a href="mailto:angcyo@126.com">angcyo</a>
 * @since 2022/06/13
 */
public class StylePath extends Path {

    /**
     * 画笔
     */
    @Nullable
    public Paint paint;

    /**
     * 样式, 优先[paint]属性
     */
    @Nullable
    public Paint.Style style;

    /**
     * 获取路径样式
     */
    @NonNull
    public Paint.Style getPathStyle() {
        if (style != null) {
            return style;
        }
        if (paint != null) {
            return paint.getStyle();
        }
        return Paint.Style.STROKE;
    }
}
