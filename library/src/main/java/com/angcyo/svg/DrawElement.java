package com.angcyo.svg;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pixplicity.sharp.Sharp;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Sharp绘制元素
 *
 * @author <a href="mailto:angcyo@126.com">angcyo</a>
 * @since 2022/06/13
 */
public class DrawElement {

    @Nullable
    public String viewBoxStr;
    @Nullable
    public String widthStr;
    @Nullable
    public String heightStr;

    /**
     * svg标签中的width/height对应的值
     */
    public RectF svgRect;

    /**
     * 是否在`<defs>`标签内
     */
    public Boolean readingDefs;

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
     * 数据的名称
     */
    @Nullable
    public String dataName;

    /**
     * 路径数据计算出来的bounds
     */
    public RectF pathBounds;

    /**
     * 当前元素绘制时作用的矩阵
     */
    @Nullable
    public Matrix matrix;

    /**
     * 当前元素绘制时Canvas的矩阵
     */
    @NonNull
    public Matrix canvasMatrix;

    /**
     * 分组信息
     */
    @Nullable
    public Sharp.SvgHandler.SvgGroup svgGroup;

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
        //drawBitmap
        IMAGE,
    }

    public StylePath createCustomPath(int color) {
        StylePath path = new StylePath();
        path.paint = new Paint(paint);
        if (color != Color.TRANSPARENT) {
            path.paint.setColor(color); //强制使用颜色
        }
        return path;
    }

    public void updateStack(Stack<Matrix> matrixStack, Stack<Sharp.SvgHandler.SvgGroup> groupStack) {
        if (!matrixStack.isEmpty()) {
            matrix = matrixStack.peek();
        }
        if (!groupStack.isEmpty()) {
            svgGroup = groupStack.peek();
        }
    }

    /**
     * 将一堆点转成svg path数据
     */
    public void updatePointsData(ArrayList<Float> points, boolean close) {
        if (points != null) {
            int size = points.size();
            if (size > 0) {
                StringBuilder builder = new StringBuilder();
                boolean isFirst = true;
                for (int i = 0; i < size; i += 2) {
                    if (isFirst) {
                        builder.append("M");
                    } else {
                        builder.append("L");
                    }
                    builder.append(points.get(i)).append(",");
                    if (i + 1 < size) {
                        builder.append(points.get(i + 1));
                    }
                    isFirst = false;
                }
                if (close) {
                    builder.append("Z");
                }
                data = builder.toString();
            }
        }
    }
}
