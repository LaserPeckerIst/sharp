package com.angcyo.svg;

import static com.angcyo.svg.DrawElement.DrawType.PATH;
import static com.angcyo.svg.DrawElement.DrawType.TEXT;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pixplicity.sharp.Sharp;
import com.pixplicity.sharp.SharpDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:angcyo@126.com">angcyo</a>
 * @since 2022/06/13
 */
public class Svg {

    /**
     * 图片转SVG
     */
    @Nullable
    public static String imageToSVG(@NonNull String imagePath) {
        String svgstring = null;
        try {
            svgstring = ImageTracerAndroid.imageToSVG(imagePath, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return svgstring;
    }

    /**
     * 将Svg数据转换成Drawable
     */
    @NonNull
    public static SharpDrawable loadSvgDrawable(@NonNull String svgData) {
        return Sharp.loadString(svgData).getDrawable();
    }

    /**
     * 将Svg中的路径数据转换成Drawable. 只提取路径
     * [color] 强制颜色, 0表示不指定, 使用原生的颜色
     * [drawStyle] 强制使用此样式绘制. null表示不限制
     * [pathPaint] 强制使用此画笔, 此时[color] [drawStyle]属性无效
     */
    @Nullable
    public static SharpDrawable loadSvgPathDrawable(@NonNull String svgData, final int color, Paint.Style drawStyle, Paint pathPaint, int viewWidth, int viewHeight) {
        Sharp sharp = Sharp.loadString(svgData);
        return loadSvgPathDrawable(sharp, color, drawStyle, pathPaint, viewWidth, viewHeight);
    }

    @Nullable
    public static SharpDrawable loadSvgPathDrawable(@NonNull String svgData, final int color, Paint.Style drawStyle, int viewWidth, int viewHeight) {
        return loadSvgPathDrawable(svgData, color, drawStyle, null, viewWidth, viewHeight);
    }

    /**
     * [pathPaint] 强制指定路径的画笔, 会覆盖[drawStyle]参数
     * [viewWidth] [viewHeight] 当前[SharpDrawable]需要显示在的可视化宽高, 用来scale[pathPaint.setStrokeWidth]线的宽度
     * 负数不生效, 并且需要使用[pathPaint]参数, 才能生效
     */
    @Nullable
    public static SharpDrawable loadSvgPathDrawable(Sharp sharp, final int color, Paint.Style drawStyle, Paint pathPaint, int viewWidth, int viewHeight) {
        final RectF pathBounds = new RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);
        final List<CustomPath> pathList = new ArrayList<>();
        sharp.setOnElementListener(new SvgElementListener() {
            @Override
            public boolean onCanvasDraw(Canvas canvas, DrawElement drawElement) {
                //拦截
                CustomPath path = null;
                if (drawElement.type == PATH) {
                    path = drawElement.createCustomPath(color);
                    path.addPath((Path) drawElement.element);
                } else if (drawElement.type != TEXT) {
                    RectF rect = (RectF) drawElement.element;
                    switch (drawElement.type) {
                        case LINE:
                            path = drawElement.createCustomPath(color);
                            path.moveTo(rect.left, rect.top);
                            path.lineTo(rect.right, rect.bottom);
                            break;
                        case OVAL:
                            path = drawElement.createCustomPath(color);
                            path.addOval(rect, Path.Direction.CW);
                            break;
                        case ROUND_RECT:
                            path = drawElement.createCustomPath(color);
                            path.addRoundRect(rect, drawElement.rx, drawElement.ry, Path.Direction.CW);
                            break;
                        default:
                            break;
                    }
                }

                if (path != null) {
                    Matrix matrix = canvas.getMatrix();
                    path.transform(matrix);
                    RectF pathRect = new RectF();
                    path.computeBounds(pathRect, true);
                    pathList.add(path);

                    pathBounds.left = Math.min(pathBounds.left, pathRect.left);
                    pathBounds.top = Math.min(pathBounds.top, pathRect.top);
                    pathBounds.right = Math.max(pathBounds.right, pathRect.right);
                    pathBounds.bottom = Math.max(pathBounds.bottom, pathRect.bottom);
                }

                return true;
            }
        });
        //触发解析, 之后才有回调
        sharp.getSharpPicture();
        if (pathBounds.width() <= 0 || pathBounds.height() <= 0) {
            return null;
        }

        //绘制
        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording((int) Math.ceil(pathBounds.width()), (int) Math.ceil(pathBounds.height()));
        canvas.translate(-pathBounds.left, -pathBounds.top);

        if (pathPaint != null && viewWidth > 0 && viewHeight > 0) {
            float strokeWidth = pathPaint.getStrokeWidth();
            float scaleWidth = viewWidth / pathBounds.width();
            float scaleHeight = viewHeight / pathBounds.height();
            float scale = Math.max(scaleWidth, scaleHeight);
            pathPaint.setStrokeWidth(strokeWidth / scale);
        }

        for (int i = 0; i < pathList.size(); i++) {
            CustomPath path = pathList.get(i);
            if (pathPaint == null) {
                Paint.Style paintStyle = path.paint.getStyle();
                if (drawStyle == null) {
                    canvas.drawPath(path, path.paint);
                } else {
                    if (drawStyle == Paint.Style.STROKE) {
                        path.paint.setStrokeWidth(1f);//强制使用1个像素
                    }
                    if (paintStyle == Paint.Style.FILL_AND_STROKE || paintStyle == drawStyle) {
                        path.paint.setStyle(drawStyle);
                        canvas.drawPath(path, path.paint);
                    } else {
                        path.paint.setStyle(drawStyle);
                        canvas.drawPath(path, path.paint);
                    }
                }
            } else {
                canvas.drawPath(path, pathPaint);
            }
        }
        picture.endRecording();

        SharpDrawable drawable = new SharpDrawable(picture);
        drawable.pathList = pathList;
        drawable.setBounds((int) pathBounds.left, (int) pathBounds.top, (int) Math.ceil(pathBounds.right), (int) Math.ceil(pathBounds.bottom));
        return drawable;
    }

}
