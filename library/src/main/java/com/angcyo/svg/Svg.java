package com.angcyo.svg;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Bitmap;
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
import com.pixplicity.sharp.SharpPicture;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:angcyo@126.com">angcyo</a>
 * @since 2022/06/13
 */
public class Svg {

    /**
     * [path] 转成 [bitmap] 对象
     */
    public static Bitmap pathToBitmap(Path path, Paint.Style drawStyle) {
        RectF pathRect = new RectF();
        path.computeBounds(pathRect, true);
        Bitmap bitmap = Bitmap.createBitmap((int) Math.ceil(pathRect.width()), (int) Math.ceil(pathRect.height()), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-pathRect.left, -pathRect.top);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(3f);
        paint.setStyle(drawStyle);
        canvas.drawPath(path, paint);
        return bitmap;
    }

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
        final List<Path> pathList = new ArrayList<>();
        sharp.setOnElementListener(new SvgElementListener() {
            @Override
            public boolean onCanvasDraw(Canvas canvas, DrawElement drawElement) {
                if (drawElement == null || drawElement.element == null || drawElement.readingDefs) {
                    return false;
                }
                //拦截
                StylePath path = null;
                if (drawElement.type == DrawElement.DrawType.PATH) {
                    path = drawElement.createCustomPath(color);
                    path.addPath((Path) drawElement.element);
                } else if (drawElement.type != DrawElement.DrawType.TEXT &&
                        drawElement.type != DrawElement.DrawType.IMAGE) {
                    RectF rect = null;
                    if (drawElement.element instanceof RectF) {
                        rect = (RectF) drawElement.element;
                    }
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

                    pathBounds.left = min(pathBounds.left, pathRect.left);
                    pathBounds.top = min(pathBounds.top, pathRect.top);
                    pathBounds.right = max(pathBounds.right, pathRect.right);
                    pathBounds.bottom = max(pathBounds.bottom, pathRect.bottom);
                }

                return true;
            }
        });
        //触发解析, 之后才有回调
        SharpPicture sharpPicture = sharp.getSharpPicture();
        if (pathBounds.width() <= 0 || pathBounds.height() <= 0) {
            return null;
        }

        SharpDrawable sharpDrawable = loadPathList(pathList, pathBounds, drawStyle, pathPaint, viewWidth, viewHeight);
        sharpDrawable.sharpPicture = sharpPicture;
        return sharpDrawable;
    }

    public static SharpDrawable loadPathList(List<Path> pathList,
                                             RectF pathBounds,
                                             Paint.Style drawStyle,
                                             Paint pathPaint,
                                             int viewWidth,
                                             int viewHeight) {

        if (pathBounds == null || pathBounds.isEmpty()) {
            pathBounds = computeBounds(pathList, true);
        }

        //绘制
        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording((int) Math.ceil(pathBounds.width()), (int) Math.ceil(pathBounds.height()));
        canvas.translate(-pathBounds.left, -pathBounds.top);

        if (pathPaint != null && viewWidth > 0 && viewHeight > 0) {
            float strokeWidth = pathPaint.getStrokeWidth();
            float scaleWidth = viewWidth / pathBounds.width();
            float scaleHeight = viewHeight / pathBounds.height();
            float scale = max(scaleWidth, scaleHeight);
            pathPaint.setStrokeWidth(strokeWidth / scale);
        }

        for (int i = 0; i < pathList.size(); i++) {
            Path path = pathList.get(i);
            Paint drawPathPaint = null;
            if (pathPaint == null) {
                //未强制画笔
                if (path instanceof StylePath) {
                    StylePath stylePath = (StylePath) path;
                    Paint.Style paintStyle = null;
                    if (stylePath.paint != null) {
                        paintStyle = stylePath.paint.getStyle();
                    }
                    if (drawStyle == null) {
                        //没有指定绘制样式, 则使用默认的
                        drawPathPaint = stylePath.paint;
                    } else {
                        if (drawStyle == Paint.Style.STROKE) {
                            //强制指定[STROKE]
                            if (stylePath.paint != null) {
                                stylePath.paint.setStrokeWidth(1f);//强制使用1个像素
                            }
                        }
                        if (paintStyle == Paint.Style.FILL_AND_STROKE || paintStyle == drawStyle) {
                            //强制指定[FILL_AND_STROKE]
                            stylePath.paint.setStyle(drawStyle);
                        } else {
                            if (stylePath.paint != null) {
                                stylePath.paint.setStyle(drawStyle);
                            }
                        }
                        drawPathPaint = stylePath.paint;
                    }
                } else {
                    drawPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    if (drawStyle == null) {
                        drawPathPaint.setStyle(Paint.Style.STROKE);
                    } else {
                        drawPathPaint.setStyle(drawStyle);
                    }
                }
            } else {
                //强制使用了画笔
                drawPathPaint = pathPaint;
            }
            //draw
            canvas.drawPath(path, drawPathPaint);
        }
        picture.endRecording();

        //result
        SharpDrawable drawable = new SharpDrawable(picture);
        drawable.pathList = pathList;
        drawable.pathBounds = pathBounds;
        drawable.setBounds((int) pathBounds.left, (int) pathBounds.top,
                (int) Math.ceil(pathBounds.right), (int) Math.ceil(pathBounds.bottom));
        return drawable;
    }

    /**
     * 计算一组[Path]的bounds
     */
    private static RectF computeBounds(List<Path> pathList, boolean exact) {
        if (pathList.isEmpty()) {
            return new RectF();
        }
        RectF bounds = new RectF();
        bounds.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);
        RectF pathRect = new RectF();
        for (Path path : pathList) {
            path.computeBounds(pathRect, exact);
            bounds.left = min(bounds.left, pathRect.left);
            bounds.top = min(bounds.top, pathRect.top);
            bounds.right = max(bounds.right, pathRect.right);
            bounds.bottom = max(bounds.bottom, pathRect.bottom);
        }
        return bounds;
    }
}
