package com.angcyo.svg;

import android.graphics.Color;
import android.graphics.Paint;

/**
 * Sharp绘制元素
 *
 * @author <a href="mailto:angcyo@126.com">angcyo</a>
 * @since 2022/06/13
 */
public class DrawElement {

    /**
     * 绘制使用的笔
     */
    public Paint paint;

    /**
     * 绘制的元素
     */
    public Object element;

    /**
     * 元素的类型
     */
    public DrawType type;

    /**
     * 圆角矩形的2个参数
     */
    public float rx;
    public float ry;

    public enum DrawType {
        //drawRoundRect
        ROUND_RECT,
        //drawLine
        LINE,
        //drawOval
        OVAL,
        //drawPath
        PATH,
        //drawText
        TEXT,
    }

    public CustomPath createCustomPath(int color) {
        CustomPath path = new CustomPath();
        path.paint = new Paint(paint);
        if (color != Color.TRANSPARENT) {
            path.paint.setColor(color); //强制使用颜色
        }
        return path;
    }
}
