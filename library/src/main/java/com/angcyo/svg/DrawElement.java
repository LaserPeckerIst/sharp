package com.angcyo.svg;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Stack;

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
     * <p>
     * 当[type == DrawElement.DrawType.TEXT]时的文本内容
     * [Sharp.SvgHandler.SvgText]
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

    /**
     * 元素的数据, 比如: 路径数据, 文本数据
     */
    @Nullable
    public String data;

    /**
     * 路径数据计算出来的bounds
     */
    public RectF pathBounds;

    /**
     * 当前元素绘制时作用的矩阵
     */
    @Nullable
    public Matrix matrix;

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

    public StylePath createCustomPath(int color) {
        StylePath path = new StylePath();
        path.paint = new Paint(paint);
        if (color != Color.TRANSPARENT) {
            path.paint.setColor(color); //强制使用颜色
        }
        return path;
    }

    public void updateMatrix(Stack<Matrix> stack) {
        if (!stack.isEmpty()) {
            matrix = stack.peek();
        }
    }

    /**
     * 将一堆点转成svg path数据
     */
    public void updatePointsData(ArrayList<Float> points) {
        if (points != null) {
            int size = points.size();
            if (size > 0) {
                StringBuilder builder = new StringBuilder();
                builder.append("m");
                for (int i = 0; i < size; i += 2) {
                    builder.append(points.get(i)).append(",");
                    if (i + 1 < size) {
                        builder.append(points.get(i + 1)).append(" ");
                    }
                }
                builder.append("Z");
                data = builder.toString();
            }
        }
    }
}
