/*
    Copyright 2011, 2015 Pixplicity, Larva Labs LLC and Google, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    Sharp is heavily based on prior work. It was originally forked from
        https://github.com/pents90/svg-android
    And changes from other forks have been consolidated:
        https://github.com/b2renger/svg-android
        https://github.com/mindon/svg-android
        https://github.com/josefpavlik/svg-android
 */

package com.pixplicity.sharp;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.angcyo.svg.DrawElement;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Scanner;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Entry point for parsing SVG files for Android.
 * Use one of the various static methods for parsing SVGs by resource, asset or input stream.
 * Optionally, a single color can be searched and replaced in the SVG while parsing.
 * You can also parse an svg path directly.
 *
 * @author Larva Labs, LLC
 * @see #loadResource(android.content.res.Resources, int)
 * @see #loadAsset(android.content.res.AssetManager, String)
 * @see #loadString(String)
 * @see #loadInputStream(java.io.InputStream)
 * @see #loadPath(String)
 */
public abstract class Sharp {

    static final String TAG = Sharp.class.getSimpleName();

    public static final int LOG_LEVEL_ERROR = 1;
    public static final int LOG_LEVEL_WARN = 2;
    public static final int LOG_LEVEL_INFO = 3;
    static int LOG_LEVEL = LOG_LEVEL_ERROR;

    @IntDef({LOG_LEVEL_ERROR, LOG_LEVEL_WARN, LOG_LEVEL_INFO})
    public @interface LogLevel {
    }

    private static String sAssumedUnit;
    private static HashMap<String, String> sTextDynamic = null;

    private final SvgHandler mSvgHandler;

    private OnSvgElementListener mOnElementListener;
    private AssetManager mAssetManager;

    /**
     * 单位
     */
    enum Unit {
        PERCENT("%"),
        PT("pt"),
        PX("px"),
        MM("mm", 100);

        public final String mAbbreviation;
        public final float mScaleFactor;

        Unit(String abbreviation) {
            this(abbreviation, 1f);
        }

        Unit(String abbreviation, float scaleFactor) {
            mAbbreviation = abbreviation;
            mScaleFactor = scaleFactor;
        }

        public static Unit matches(String value) {
            for (Unit unit : Unit.values()) {
                if (value.endsWith(unit.mAbbreviation)) {
                    return unit;
                }
            }
            return null;
        }
    }

    public static void setLogLevel(@LogLevel int logLevel) {
        LOG_LEVEL = logLevel;
    }

    @SuppressWarnings("unused")
    public static void prepareTexts(HashMap<String, String> texts) {
        sTextDynamic = texts;
    }

    /**
     * Parse SVG data from an input stream.
     *
     * @param svgData the input stream, with SVG XML data in UTF-8 character encoding.
     * @return this Sharp object
     */
    @SuppressWarnings("unused")
    public static Sharp loadInputStream(final InputStream svgData) {
        return new Sharp() {
            @Override
            protected InputStream getInputStream() {
                return svgData;
            }

            @Override
            protected void close(InputStream inputStream) {
            }
        };
    }

    /**
     * Parse SVG data from a text.
     *
     * @param svgData the text containing SVG XML data.
     * @return this Sharp object
     */
    @SuppressWarnings("unused")
    public static Sharp loadString(final String svgData) {
        return new Sharp() {
            @Override
            protected InputStream getInputStream() {
                return new ByteArrayInputStream(svgData.getBytes());
            }

            @Override
            protected void close(InputStream inputStream) {
            }
        };
    }

    /**
     * Parse SVG data from an Android application resource.
     *
     * @param resources the Android context resources.
     * @param resId     the ID of the raw resource SVG.
     * @return this Sharp object
     */
    @SuppressWarnings("unused")
    public static Sharp loadResource(final Resources resources, final int resId) {
        return new Sharp() {
            @Override
            protected InputStream getInputStream() {
                InputStream inputStream = resources.openRawResource(resId);
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    // Read the contents of the resource if we're not on the main thread
                    inputStream = readInputStream(inputStream);
                }
                return inputStream;
            }

            @Override
            protected void close(InputStream inputStream) {
            }
        };
    }

    /**
     * Parse SVG data from an Android application asset.
     *
     * @param assetMngr the Android asset manager.
     * @param svgPath   the path to the SVG file in the application's assets.
     * @return this Sharp object
     */
    @SuppressWarnings("unused")
    public static Sharp loadAsset(final AssetManager assetMngr, final String svgPath) {
        return new Sharp() {
            @Override
            protected InputStream getInputStream() throws IOException {
                InputStream inputStream = assetMngr.open(svgPath);
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    // Read the contents of the resource if we're not on the main thread
                    inputStream = readInputStream(inputStream);
                }
                return inputStream;
            }

            @Override
            protected void close(InputStream inputStream) throws IOException {
                inputStream.close();
            }
        };
    }

    /**
     * Parse SVG data from a file.
     *
     * @param imageFile the input stream, with SVG XML data in UTF-8 character encoding.
     * @return this Sharp object
     */
    @SuppressWarnings("unused")
    public static Sharp loadFile(final File imageFile) {
        return new Sharp() {
            private FileInputStream mFis;

            @Override
            protected InputStream getInputStream() throws FileNotFoundException {
                mFis = new FileInputStream(imageFile);
                return mFis;
            }

            @Override
            protected void close(InputStream inputStream) throws IOException {
                inputStream.close();
                mFis.close();
            }
        };
    }

    /**
     * Parses a single SVG path and returns it as a <code>android.graphics.Path</code> object.
     * An example path is <code>M250,150L150,350L350,350Z</code>, which draws a triangle.
     *
     * @param pathString the SVG path, see the specification <a href="http://www.w3.org/TR/SVG/paths.html">here</a>.
     */
    @SuppressWarnings("unused")
    public static Path loadPath(String pathString) {
        return doPath(pathString);
    }

    @NonNull
    private static InputStream readInputStream(InputStream inputStream) {
        StringBuilder svgData = new StringBuilder();
        Scanner scanner = new Scanner(inputStream);
        String lineSeparator = System.getProperty("line.separator");
        // Try-with-resources is only for API level 19 and up
        //noinspection TryFinallyCanBeTryWithResources
        try {
            while (scanner.hasNextLine()) {
                svgData.append(scanner.nextLine()).append(lineSeparator);
            }
        } finally {
            scanner.close();
        }
        inputStream = new ByteArrayInputStream(svgData.toString().getBytes());
        return inputStream;
    }

    public static void checkAssumedUnits(String unit) {
        if (sAssumedUnit == null) {
            sAssumedUnit = unit;
        }
        if (!sAssumedUnit.equals(unit)) {
            throw new IllegalStateException("Mixing units; SVG contains both " + sAssumedUnit + " and " + unit);
        }
    }

    private Sharp() {
        //Log.d(TAG, "Parsing SVG...");
        sAssumedUnit = null;
        mSvgHandler = new SvgHandler(this);
    }

    private AssetManager getAssetManager() {
        return mAssetManager;
    }

    @SuppressWarnings("unused")
    public Sharp setOnElementListener(OnSvgElementListener onElementListener) {
        mOnElementListener = onElementListener;
        return this;
    }

    protected abstract InputStream getInputStream() throws IOException;

    protected abstract void close(InputStream inputStream) throws IOException;

    @SuppressWarnings("unused")
    public Sharp withAssets(AssetManager assetManager) {
        mAssetManager = assetManager;
        return this;
    }

    @SuppressWarnings("unused")
    public void into(@NonNull final View view) {
        SharpDrawable.prepareView(view);
        if (view instanceof ImageView) {
            final SharpDrawable drawable = getDrawable();
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // Set it immediately if on the main thread
                ((ImageView) view).setImageDrawable(drawable);
            } else {
                // Otherwise, set it on through the view's Looper
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        ((ImageView) view).setImageDrawable(drawable);
                    }
                });
            }
        } else {
            intoBackground(view);
        }
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void intoBackground(final View view) {
        final SharpDrawable drawable = getDrawable(view);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Set it immediately if on the main thread
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                view.setBackgroundDrawable(drawable);
            } else {
                view.setBackground(drawable);
            }
        } else {
            // Otherwise, set it on through the view's Looper
            view.post(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        view.setBackgroundDrawable(drawable);
                    } else {
                        view.setBackground(drawable);
                    }
                }
            });
        }
    }

    /**
     * Processes the SVG and provides the resulting drawable. Runs on the main thread.
     */
    @SuppressWarnings("unused")
    public SharpDrawable getDrawable() {
        return getSharpPicture().getDrawable();
    }

    /**
     * Processes the SVG and provides the resulting drawable. Runs on the main thread.
     *
     * @deprecated Use {@link #getDrawable()} instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public SharpDrawable getDrawable(View view) {
        return getSharpPicture().getDrawable(view);
    }

    /**
     * Processes the SVG and provides the resulting drawable. Runs in a background thread.
     */
    @SuppressWarnings("unused")
    public void getDrawable(final View view, final DrawableCallback callback) {
        getSharpPicture(new PictureCallback() {
            @Override
            public void onPictureReady(SharpPicture sharpPicture) {
                SharpDrawable drawable = sharpPicture.getDrawable(view);
                callback.onDrawableReady(drawable);
            }
        });
    }

    /**
     * read入口
     * [SharpPicture]
     */
    private SharpPicture getSharpPicture(InputStream inputStream) throws SvgParseException {
        if (inputStream == null) {
            throw new NullPointerException("An InputStream must be provided");
        }
        try {
            //入口
            mSvgHandler.read(inputStream);
        } finally {
            try {
                close(inputStream);
            } catch (IOException e) {
                //noinspection ThrowFromFinallyBlock
                throw new SvgParseException(e);
            }
        }
        SharpPicture result = new SharpPicture(mSvgHandler.mPicture, mSvgHandler.mBounds);
        // Skip bounds if it was an empty pic
        if (!Float.isInfinite(mSvgHandler.mLimits.top)) {
            result.setLimits(mSvgHandler.mLimits);
        }
        return result;
    }

    @SuppressWarnings("unused")
    public SharpPicture getSharpPicture() throws SvgParseException {
        InputStream inputStream = null;
        try {
            inputStream = getInputStream();
            return getSharpPicture(inputStream);
        } catch (IOException e) {
            throw new SvgParseException(e);
        } finally {
            try {
                if (inputStream != null) {
                    close(inputStream);
                }
            } catch (IOException e) {
                //noinspection ThrowFromFinallyBlock
                throw new SvgParseException(e);
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    public AsyncTask<Void, Void, SharpPicture> getSharpPicture(final PictureCallback callback) {
        return new AsyncTask<Void, Void, SharpPicture>() {
            @Override
            protected SharpPicture doInBackground(Void... params) {
                InputStream inputStream = null;
                try {
                    inputStream = getInputStream();
                    return getSharpPicture(inputStream);
                } catch (IOException e) {
                    throw new SvgParseException(e);
                } finally {
                    try {
                        if (inputStream != null) {
                            close(inputStream);
                        }
                    } catch (IOException e) {
                        //noinspection ThrowFromFinallyBlock
                        throw new SvgParseException(e);
                    }
                }
            }

            @Override
            protected void onPostExecute(SharpPicture sharpPicture) {
                callback.onPictureReady(sharpPicture);
            }
        }.execute();
    }

    /**
     * 从字符串中解析出所哟的浮点数字
     */
    private static ArrayList<Float> parseNumbers(String s) {
        //Log.d(TAG, "Parsing numbers from: '" + s + "'");
        int n = s.length();
        int p = 0;
        ArrayList<Float> numbers = new ArrayList<>();
        boolean skipChar = false;
        for (int i = 1; i < n; i++) {
            if (skipChar) {
                skipChar = false;
                continue;
            }
            char c = s.charAt(i);
            switch (c) {
                // This ends the parsing, as we are on the next element
                case 'M':
                case 'm':
                case 'Z':
                case 'z':
                case 'L':
                case 'l':
                case 'H':
                case 'h':
                case 'V':
                case 'v':
                case 'C':
                case 'c':
                case 'S':
                case 's':
                case 'Q':
                case 'q':
                case 'T':
                case 't':
                case 'a':
                case 'A':
                case ')': {
                    String str = s.substring(p, i);
                    if (str.trim().length() > 0) {
                        //Log.d(TAG, "  Last: " + str);
                        Float f = Float.parseFloat(str);
                        numbers.add(f);
                    }
                    return numbers;
                }
                case 'e':
                case 'E': {
                    // exponent in float number - skip eventual minus sign following the exponent
                    skipChar = true;
                    break;
                }
                case '\n':
                case '\t':
                case ' ':
                case ',':
                case '-': {
                    String str = s.substring(p, i);
                    // Just keep moving if multiple whitespace
                    if (str.trim().length() > 0) {
                        //Log.d(TAG, "  Next: " + str);
                        Float f = Float.parseFloat(str);
                        numbers.add(f);
                        if (c == '-') {
                            p = i;
                        } else {
                            p = i + 1;
                            skipChar = true;
                        }
                    } else {
                        p++;
                    }
                    break;
                }
            }
        }
        String last = s.substring(p);
        if (last.length() > 0) {
            //Log.d(TAG, "  Last: " + last);
            try {
                numbers.add(Float.parseFloat(last));
            } catch (NumberFormatException nfe) {
                // Just white-space, forget it
            }
        }
        return numbers;
    }

    private static ArrayList<Float> readTransform(String attr, String type) {
        int i = attr.indexOf(type + "(");
        if (i > -1) {
            i += type.length() + 1;
            int j = attr.indexOf(")", i);
            if (j > -1) {
                ArrayList<Float> np = parseNumbers(attr.substring(i, j));
                if (np.size() > 0) {
                    return np;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Matrix parseTransform(String s) {
        Matrix matrix = null;

        if (s.startsWith("matrix(")) {
            ArrayList<Float> np = parseNumbers(s.substring("matrix(".length()));
            if (np.size() == 6) {
                //noinspection ConstantConditions
                if (matrix == null) {
                    matrix = new Matrix();
                }
                matrix.setValues(new float[]{
                        // Row 1
                        np.get(0),
                        np.get(2),
                        np.get(4),
                        // Row 2
                        np.get(1),
                        np.get(3),
                        np.get(5),
                        // Row 3
                        0,
                        0,
                        1,
                });
            }
        }

        ArrayList<Float> np = readTransform(s, "scale");
        if (np != null) {
            float sx = np.get(0);
            float sy = sx;
            if (np.size() > 1) {
                sy = np.get(1);
            }
            if (matrix == null) {
                matrix = new Matrix();
            }
            matrix.postScale(sx, sy);
        }

        np = readTransform(s, "skewX");
        if (np != null) {
            float angle = np.get(0);
            if (matrix == null) {
                matrix = new Matrix();
            }
            matrix.preSkew((float) Math.tan(angle), 0);
        }

        np = readTransform(s, "skewY");
        if (np != null) {
            float angle = np.get(0);
            if (matrix == null) {
                matrix = new Matrix();
            }
            matrix.preSkew(0, (float) Math.tan(angle));
        }

        np = readTransform(s, "rotate");
        if (np != null) {
            float angle = np.get(0);
            float cx, cy;
            if (matrix == null) {
                matrix = new Matrix();
            }
            if (np.size() > 2) {
                cx = np.get(1);
                cy = np.get(2);
                matrix.preRotate(angle, cx, cy);
            } else {
                matrix.preRotate(angle);
            }
        }

        np = readTransform(s, "translate");
        if (np != null) {
            float tx = np.get(0);
            float ty = 0;
            if (np.size() > 1) {
                ty = np.get(1);
            }
            if (matrix == null) {
                matrix = new Matrix();
            }
            matrix.postTranslate(tx, ty);
        }

        return matrix;
    }

    /**
     * 路径解析
     * This is where the hard-to-parse paths are handled.
     * Uppercase rules are absolute positions, lowercase are relative.
     * Types of path rules:
     * <p/>
     * <ol>
     * <li>M/m - (x y)+ - Move to (without drawing)
     * <li>Z/z - (no params) - Close path (back to starting point)
     * <li>L/l - (x y)+ - Line to
     * <li>H/h - x+ - Horizontal ine to
     * <li>V/v - y+ - Vertical line to
     * <li>C/c - (mX1 y1 x2 y2 x y)+ - Cubic bezier to
     * <li>S/s - (x2 y2 x y)+ - Smooth cubic bezier to (shorthand that assumes the x2, y2 from previous C/S is the mX1, y1 of this bezier)
     * <li>Q/q - (mX1 y1 x y)+ - Quadratic bezier to
     * <li>T/t - (x y)+ - Smooth quadratic bezier to (assumes previous control point is "reflection" of last one w.r.t. to current point)
     * </ol>
     * <p/>
     * Numbers are separate by whitespace, comma or nothing at all (!) if they are self-delimiting, (ie. begin with a - sign)
     * <p>
     * 例如: M14,85l3,9h72c0,0,5-9,4-10c-2-2-79,0-79,1
     *
     * @param s the path text from the XML
     */
    @NonNull
    private static Path doPath(@NonNull String s) {
        int n = s.length();
        SvgParserHelper ph = new SvgParserHelper(s, 0);
        ph.skipWhitespace();
        Path p = new Path();
        float lastX = 0;
        float lastY = 0;
        float lastX1 = 0;
        float lastY1 = 0;
        float subPathStartX = 0;
        float subPathStartY = 0;
        char prevCmd = 0;
        while (ph.pos < n) {
            char cmd = s.charAt(ph.pos);

            //获取命令
            switch (cmd) {
                case '.':
                case '-':
                case '+':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    if (prevCmd == 'm' || prevCmd == 'M') {
                        cmd = (char) (((int) prevCmd) - 1);
                        break;
                    } else if (("lhvcsqta").indexOf(Character.toLowerCase(prevCmd)) >= 0) {
                        cmd = prevCmd;
                        break;
                    }
                default: {
                    ph.advance();
                    prevCmd = cmd;
                }
            }

            //解析命令
            boolean wasCurve = false; //是否是曲线
            switch (cmd) {
                case 'Z':
                case 'z': {
                    // Close path
                    p.close();
                    p.moveTo(subPathStartX, subPathStartY);
                    lastX = subPathStartX;
                    lastY = subPathStartY;
                    lastX1 = subPathStartX;
                    lastY1 = subPathStartY;
                    wasCurve = true;
                    break;
                }
                case 'M':
                case 'm': {
                    // Move
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        //2023-11-6 修复
                        //subPathStartX += x;
                        //subPathStartY += y;
                        subPathStartX = lastX + x;
                        subPathStartY = lastY + y;
                        p.rMoveTo(x, y);
                        lastX += x;
                        lastY += y;
                    } else {
                        subPathStartX = x;
                        subPathStartY = y;
                        p.moveTo(x, y);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                }
                case 'L':
                case 'l': {
                    // Line
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        p.rLineTo(x, y);
                        lastX += x;
                        lastY += y;
                    } else {
                        p.lineTo(x, y);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                }
                case 'H':
                case 'h': {
                    // Horizontal line
                    float x = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        p.rLineTo(x, 0);
                        lastX += x;
                    } else {
                        p.lineTo(x, lastY);
                        lastX = x;
                    }
                    break;
                }
                case 'V':
                case 'v': {
                    // Vertical line
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        p.rLineTo(0, y);
                        lastY += y;
                    } else {
                        p.lineTo(lastX, y);
                        lastY = y;
                    }
                    break;
                }
                case 'C':
                case 'c': {
                    // Cubic Bézier (six parameters)
                    wasCurve = true;
                    float x1 = ph.nextFloat();
                    float y1 = ph.nextFloat();
                    float x2 = ph.nextFloat();
                    float y2 = ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        // Relative coordinates
                        x1 += lastX;
                        y1 += lastY;
                        x2 += lastX;
                        y2 += lastY;
                        x += lastX;
                        y += lastY;
                    }
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    lastX1 = x2;
                    lastY1 = y2;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'S':
                case 's': {
                    // Shorthand cubic Bézier (four parameters)
                    wasCurve = true;
                    float x2 = ph.nextFloat();
                    float y2 = ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        // Relative coordinates
                        x2 += lastX;
                        x += lastX;
                        y2 += lastY;
                        y += lastY;
                    }
                    float x1 = 2 * lastX - lastX1;
                    float y1 = 2 * lastY - lastY1;
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    lastX1 = x2;
                    lastY1 = y2;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'Q':
                case 'q': {
                    // Quadratic Bézier (four parameters)
                    wasCurve = true;
                    float x1 = ph.nextFloat();
                    float y1 = ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        // Relative coordinates
                        x1 += lastX;
                        x += lastX;
                        y1 += lastY;
                        y += lastY;
                    }
                    p.quadTo(x1, y1, x, y);
                    lastX1 = x1;
                    lastY1 = y1;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'T':
                case 't': {
                    // Shorthand quadratic Bézier (two parameters)
                    wasCurve = true;
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        // Relative coordinates
                        x += lastX;
                        y += lastY;
                    }
                    float x1 = 2 * lastX - lastX1;
                    float y1 = 2 * lastY - lastY1;
                    p.quadTo(x1, y1, x, y);
                    lastX1 = x1;
                    lastY1 = y1;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'A':
                case 'a': {
                    // Elliptical arc
                    float rx = ph.nextFloat();
                    float ry = ph.nextFloat();
                    float theta = ph.nextFloat();
                    int largeArc = ph.nextFlag();
                    int sweepArc = ph.nextFlag();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (Character.isLowerCase(cmd)) {
                        x += lastX;
                        y += lastY;
                    }
                    drawArc(p, lastX, lastY, x, y, rx, ry, theta, largeArc, sweepArc);
                    lastX = x;
                    lastY = y;
                    break;
                }
            }
            if (!wasCurve) {
                lastX1 = lastX;
                lastY1 = lastY;
            }
            ph.skipWhitespace();
        }
        return p;
    }

    private static float angle(float y1, float x1, float y2, float x2) {
        return (float) Math.toDegrees(Math.atan2(y1, x1) - Math.atan2(y2, x2)) % 360;
    }

    private static final RectF arcRectf = new RectF();
    private static final Matrix arcMatrix = new Matrix();
    private static final Matrix arcMatrix2 = new Matrix();

    private static void drawArc(Path p, float lastX, float lastY, float x, float y, float rx, float ry, float theta, int largeArc, int sweepArc) {
        //Log.d("drawArc", "from (" + lastX + "," + lastY + ") to (" + x + ","+ y + ") r=(" + rx + "," + ry + ") theta=" + theta + " flags="+ largeArc + "," + sweepArc);

        // http://www.w3.org/TR/SVG/implnote.html#ArcImplementationNotes

        if (rx == 0 || ry == 0) {
            p.lineTo(x, y);
            return;
        }

        if (x == lastX && y == lastY) {
            return; // nothing to draw
        }

        rx = Math.abs(rx);
        ry = Math.abs(ry);

        final float thrad = theta * (float) Math.PI / 180;
        final float st = (float) Math.sin(thrad);
        final float ct = (float) Math.cos(thrad);

        final float xc = (lastX - x) / 2;
        final float yc = (lastY - y) / 2;
        final float x1t = ct * xc + st * yc;
        final float y1t = -st * xc + ct * yc;

        final float x1ts = x1t * x1t;
        final float y1ts = y1t * y1t;
        float rxs = rx * rx;
        float rys = ry * ry;

        float lambda = (x1ts / rxs + y1ts / rys) * 1.001f; // add 0.1% to be sure that no out of range occurs due to limited precision
        if (lambda > 1) {
            float lambdasr = (float) Math.sqrt(lambda);
            rx *= lambdasr;
            ry *= lambdasr;
            rxs = rx * rx;
            rys = ry * ry;
        }

        final float R = (float) Math.sqrt((rxs * rys - rxs * y1ts - rys * x1ts) / (rxs * y1ts + rys * x1ts))
                * ((largeArc == sweepArc) ? -1 : 1);
        final float cxt = R * rx * y1t / ry;
        final float cyt = -R * ry * x1t / rx;
        final float cx = ct * cxt - st * cyt + (lastX + x) / 2;
        final float cy = st * cxt + ct * cyt + (lastY + y) / 2;

        final float th1 = angle(1, 0, (x1t - cxt) / rx, (y1t - cyt) / ry);
        float dth = angle((x1t - cxt) / rx, (y1t - cyt) / ry, (-x1t - cxt) / rx, (-y1t - cyt) / ry);

        if (sweepArc == 0 && dth > 0) {
            dth -= 360;
        } else if (sweepArc != 0 && dth < 0) {
            dth += 360;
        }

        // draw
        if ((theta % 360) == 0) {
            // no rotate and translate need
            arcRectf.set(cx - rx, cy - ry, cx + rx, cy + ry);
            p.arcTo(arcRectf, th1, dth);
        } else {
            // this is the hard and slow part :-)
            arcRectf.set(-rx, -ry, rx, ry);

            arcMatrix.reset();
            arcMatrix.postRotate(theta);
            arcMatrix.postTranslate(cx, cy);
            arcMatrix.invert(arcMatrix2);

            p.transform(arcMatrix2);
            p.arcTo(arcRectf, th1, dth);
            p.transform(arcMatrix);
        }
    }

    private static ArrayList<Float> getNumberParseAttr(String name, Attributes attributes) {
        int n = attributes.getLength();
        for (int i = 0; i < n; i++) {
            if (attributes.getLocalName(i).equals(name)) {
                return parseNumbers(attributes.getValue(i));
            }
        }
        return null;
    }

    private static String getStringAttr(String name, Attributes attributes) {
        int n = attributes.getLength();
        for (int i = 0; i < n; i++) {
            if (attributes.getLocalName(i).equals(name)) {
                return attributes.getValue(i);
            }
        }
        return null;
    }

    private static Float getFloatAttr(String name, Attributes attributes) {
        return getFloatAttr(name, attributes, null);
    }

    private static Float getFloatAttr(String name, Attributes attributes, Float defaultValue) {
        String value = getStringAttr(name, attributes);
        return parseFloat(value, defaultValue);
    }

    private static Float parseFloat(String value, Float defaultValue) {
        if (value == null) {
            return defaultValue;
        } else {
            float scaleFactor = 1f;
            Unit unit = Unit.matches(value);
            if (unit != null) {
                value = value.substring(0, value.length() - unit.mAbbreviation.length());
            }
            float valueF = Float.parseFloat(value);
            if (unit != null) {
                switch (unit) {
                    case PT:
                        valueF = valueF + 0.5f;
                        break;
                    case PERCENT:
                        valueF = valueF / 100f;
                        break;
                    case MM:
                        valueF = valueF * 96f / 25.4f;
                        break;
                }
                checkAssumedUnits(unit.mAbbreviation);
                scaleFactor = unit.mScaleFactor;
            }
            return valueF * scaleFactor;
        }
    }

    //<editor-fold desc="回调">

    private void onSvgStart(@NonNull Canvas canvas, @Nullable RectF bounds) {
        if (mOnElementListener != null) {
            mOnElementListener.onSvgStart(canvas, bounds);
        }
    }

    private void onSvgEnd(@NonNull Canvas canvas, @Nullable RectF bounds) {
        if (mOnElementListener != null) {
            mOnElementListener.onSvgEnd(canvas, bounds);
        }
    }

    private <T> T onSvgElement(@Nullable String id,
                               @NonNull T element,
                               @Nullable RectF elementBounds,
                               @NonNull Canvas canvas,
                               @Nullable RectF canvasBounds,
                               @Nullable Paint paint) {
        if (mOnElementListener != null) {
            return mOnElementListener.onSvgElement(id, element, elementBounds, canvas, canvasBounds, paint);
        }
        return element;
    }

    private <T> void onSvgElementDrawn(@Nullable String id,
                                       @NonNull T element,
                                       @NonNull Canvas canvas,
                                       @Nullable Paint paint) {
        if (mOnElementListener != null) {
            mOnElementListener.onSvgElementDrawn(id, element, canvas, paint);
        }
    }

    private boolean onCanvasDraw(Canvas canvas, DrawElement drawElement) {
        if (mOnElementListener != null) {
            return mOnElementListener.onCanvasDraw(canvas, drawElement);
        }
        return false;
    }

    //</editor-fold desc="回调">

    private static class Gradient {

        private String mId;
        private String mXlink;
        private boolean mIsLinear;
        private float mX1, mY1, mX2, mY2;
        private float mX, mY, mRadius;
        private ArrayList<Float> mPositions = new ArrayList<>();
        private ArrayList<Integer> mColors = new ArrayList<>();
        private Matrix mMatrix = null;

        public Shader mShader = null;
        public boolean mBoundingBox = false;
        public TileMode mTileMode;

        public void inherit(Gradient parent) {
            Gradient child = this;
            child.mXlink = parent.mId;
            child.mPositions = parent.mPositions;
            child.mColors = parent.mColors;
            if (child.mMatrix == null) {
                child.mMatrix = parent.mMatrix;
            } else if (parent.mMatrix != null) {
                Matrix m = new Matrix(parent.mMatrix);
                m.preConcat(child.mMatrix);
                child.mMatrix = m;
            }
        }
    }

    private static class StyleSet {
        HashMap<String, String> styleMap = new HashMap<>();

        private StyleSet(String string) {
            String[] styles = string.split(";");
            for (String s : styles) {
                String[] style = s.split(":");
                if (style.length == 2) {
                    styleMap.put(style[0].trim(), style[1].trim());
                }
            }
        }

        public String getStyle(String name) {
            return styleMap.get(name);
        }
    }

    private static class Properties {

        StyleSet mStyles = null;
        Attributes mAttrs;

        private HashMap<String, HashMap<String, String>> mClsStyle;

        private Properties(Attributes attrs, HashMap<String, HashMap<String, String>> clsStyle) {
            mAttrs = attrs;
            mClsStyle = clsStyle;
            String styleAttr = getStringAttr("style", attrs);
            if (styleAttr != null) {
                mStyles = new StyleSet(styleAttr);
            }
        }

        private String getAttr(String name) {
            return getAttr(name, false);
        }

        public String getAttr(String name, boolean isClass) {
            String v = null;
            if (mStyles != null) {
                v = mStyles.getStyle(name);
            }
            if (v == null) {
                v = getStringAttr(name, mAttrs);
            }
            if (v == null && !isClass) {
                String clsStr = getAttr("class", true);
                if (clsStr == null || clsStr.isEmpty()) {
                    //no op
                } else {
                    String[] clss = clsStr.split(" ");
                    for (String cls : clss) {
                        String style = getClassStyle(cls.trim(), name);
                        if (style != null) {
                            v = style;
                        }
                    }
                }
            }
            return v;
        }

        /**
         * 获取指定类, 指定的属性对应的值
         * [className] 不包含.的类名
         */
        private String getClassStyle(String className, String name) {
            HashMap<String, String> clsMap = mClsStyle.get("." + className);
            if (clsMap != null) {
                return clsMap.get(name);
            }
            return null;
        }

        public String getString(String name) {
            return getAttr(name);
        }

        private Integer rgb(int r, int g, int b) {
            return ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
        }

        private int parseNum(String v) throws NumberFormatException {
            if (v.endsWith("%")) {
                v = v.substring(0, v.length() - 1);
                return Math.round(Float.parseFloat(v) / 100 * 255);
            }
            return Integer.parseInt(v);
        }

        public Integer getColor(String name) {
            String v = getAttr(name);
            if (v == null) {
                return null;
            } else if (v.startsWith("#")) {
                try {
                    int c = Integer.parseInt(v.substring(1), 16);
                    if (v.length() == 4) {
                        // short form color, i.e. #FFF
                        c = hex3Tohex6(c);
                    }
                    return c;
                } catch (NumberFormatException nfe) {
                    return null;
                }
            } else if (v.startsWith("rgb(") && v.endsWith(")")) {
                String[] values = v.substring(4, v.length() - 1).split(",");
                try {
                    return rgb(parseNum(values[0]), parseNum(values[1]), parseNum(values[2]));
                } catch (NumberFormatException nfe) {
                    return null;
                } catch (ArrayIndexOutOfBoundsException e) {
                    return null;
                }
            } else if (v.toLowerCase(Locale.US).startsWith("currentcolor")) {
                //当前的颜色
                return getColor("color");
            } else {
                //颜色映射
                return SvgColors.mapColor(v);
            }
        }

        // convert 0xRGB into 0xRRGGBB
        private int hex3Tohex6(int x) {
            return (x & 0xF00) << 8 | (x & 0xF00) << 12 |
                    (x & 0xF0) << 4 | (x & 0xF0) << 8 |
                    (x & 0xF) << 4 | (x & 0xF);
        }

        public Float getFloat(String name, float defaultValue) {
            Float v = getFloat(name);
            if (v == null) {
                return defaultValue;
            } else {
                return v;
            }
        }

        public Float getFloat(String name) {
            String v = getAttr(name);
            if (v == null) {
                return null;
            } else {
                try {
                    return Float.parseFloat(v);
                } catch (NumberFormatException nfe) {
                    return null;
                }
            }
        }
    }

    /**
     * SVG处理类
     */
    public static class SvgHandler extends DefaultHandler {

        //<editor-fold desc="内部属性">

        private final Sharp mSharp;
        private Picture mPicture;

        private Canvas mCanvas;
        private Paint mStrokePaint;
        private boolean mStrokeSet = false;
        private Stack<Paint> mStrokePaintStack = new Stack<>();
        private Stack<Boolean> mStrokeSetStack = new Stack<>();

        private Paint mFillPaint;
        private boolean mFillSet = false;
        private Stack<Paint> mFillPaintStack = new Stack<>();
        private Stack<Boolean> mFillSetStack = new Stack<>();

        // Scratch rect (so we aren't constantly making new ones)
        private RectF mLine = new RectF();
        private RectF mRect = new RectF();
        private RectF mBounds = null;
        private String viewBoxStr;
        private String widthStr;
        private String heightStr;
        private RectF mLimits = new RectF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        private Stack<Boolean> mTransformStack = new Stack<>();
        private Stack<Matrix> mMatrixStack = new Stack<>();

        private HashMap<String, Gradient> mGradientMap = new HashMap<>();
        private Gradient mGradient = null;

        private final Stack<SvgText> mTextStack = new Stack<>();
        private final Stack<SvgGroup> mGroupStack = new Stack<>();

        private HashMap<String, String> mDefs = new HashMap<>();

        //是否正在读取defs
        private boolean mReadingDefs = false;

        //是否正在读取style
        private boolean mReadingStyle = false;

        //css class 对应的样式表
        private HashMap<String, HashMap<String, String>> clsStyle = new HashMap<>();

        //样式文本存放
        private StringBuilder mStyleText;

        private Stack<String> mReadIgnoreStack = new Stack<>();

        //</editor-fold desc="内部属性">

        private SvgHandler(Sharp sharp) {
            mSharp = sharp;
        }

        //<editor-fold desc="触发解析回调">

        private void onSvgStart() {
            mSharp.onSvgStart(mCanvas, mBounds);
        }

        private void onSvgEnd() {
            mSharp.onSvgEnd(mCanvas, mBounds);
        }

        private <T> T onSvgElement(@Nullable String id,
                                   @NonNull T element,
                                   @Nullable RectF elementBounds,
                                   @Nullable Paint paint) {
            return mSharp.onSvgElement(id, element, elementBounds, mCanvas, mBounds, paint);
        }

        private <T> void onSvgElementDrawn(@Nullable String id,
                                           @NonNull T element,
                                           @Nullable Paint paint) {
            mSharp.onSvgElementDrawn(id, element, mCanvas, paint);
        }

        private boolean onCanvasDraw(Canvas canvas, DrawElement drawElement) {
            return mSharp.onCanvasDraw(canvas, drawElement);
        }

        //</editor-fold desc="触发解析回调">

        //<editor-fold desc="解析入口">

        /**
         * 读取解析的入口
         */
        public void read(InputStream in) {
            mPicture = new Picture();
            try {
                long start = System.currentTimeMillis();
                if (in.markSupported()) {
                    //GZIP支持
                    in.mark(4);
                    byte[] magic = new byte[2];
                    int r = in.read(magic, 0, 2);
                    int magicInt = (magic[0] + (((int) magic[1]) << 8)) & 0xffff;
                    in.reset();
                    if (r == 2 && magicInt == GZIPInputStream.GZIP_MAGIC) {
                        if (LOG_LEVEL >= LOG_LEVEL_INFO) {
                            Log.d(TAG, "SVG is gzipped");
                        }
                        in = new GZIPInputStream(in);
                    }
                }
                //XML文档解析
                SAXParserFactory spf = SAXParserFactory.newInstance();
                SAXParser sp = spf.newSAXParser();
                XMLReader xr = sp.getXMLReader();
                xr.setContentHandler(this);
                xr.parse(new InputSource(in));
                //解析结束
                if (sTextDynamic != null) {
                    sTextDynamic.clear();
                    sTextDynamic = null;
                }
                if (LOG_LEVEL >= LOG_LEVEL_INFO) {
                    Log.v(TAG, "Parsing complete in " + (System.currentTimeMillis() - start) + " ms.");
                }
            } catch (IOException | SAXException | ParserConfigurationException e) {
                Log.e(TAG, "Failed parsing SVG", e);
                throw new SvgParseException(e);
            }
        }

        //</editor-fold desc="解析入口">

        //<editor-fold desc="xml属性解析">

        private final Matrix gradMatrix = new Matrix();

        private boolean doFill(Properties atts, RectF boundingBox) {
            if ("none".equals(atts.getString("display"))) {
                return false;
            }
            String fillString = atts.getString("fill");
            if (fillString != null) {
                if (fillString.startsWith("url(#")) {
                    // It's a gradient fill, look it up in our map
                    String id = fillString.substring("url(#".length(), fillString.length() - 1);
                    Gradient g = mGradientMap.get(id);
                    Shader shader = null;
                    if (g != null) {
                        shader = g.mShader;
                    }
                    if (shader != null) {
                        //Util.debug("Found shader!");
                        mFillPaint.setShader(shader);
                        if (boundingBox != null) {
                            gradMatrix.set(g.mMatrix);
                            if (g.mBoundingBox) {
                                //Log.d(TAG, "gradient is bounding box");
                                gradMatrix.preTranslate(boundingBox.left, boundingBox.top);
                                gradMatrix.preScale(boundingBox.width(), boundingBox.height());
                            }
                            shader.setLocalMatrix(gradMatrix);
                        }
                        return true;
                    } else {
                        //Log.d(TAG, "Didn't find shader, using black: " + id);
                        mFillPaint.setShader(null);
                        doColor(atts, Color.BLACK, true, mFillPaint);
                        return true;
                    }
                } else if (fillString.equalsIgnoreCase("none")) {
                    mFillPaint.setShader(null);
                    mFillPaint.setColor(Color.TRANSPARENT);
                    // optimization: return false if transparent
                    return false;
                } else {
                    mFillPaint.setShader(null);
                    Integer color = atts.getColor("fill");
                    if (color != null) {
                        doColor(atts, color, true, mFillPaint);
                        return true;
                    } else {
                        if (LOG_LEVEL >= LOG_LEVEL_WARN) {
                            Log.w(TAG, "Unrecognized fill color, using black: " + fillString);
                        }
                        doColor(atts, Color.BLACK, true, mFillPaint);
                        return true;
                    }
                }
            } else {
                if (mFillSet) {
                    // If fill is set, inherit from parent
                    // optimization: return false if transparent
                    return mFillPaint.getColor() != Color.TRANSPARENT;
                } else {
                    // Default is black fill
                    mFillPaint.setShader(null);
                    mFillPaint.setColor(Color.BLACK);
                    return true;
                }
            }
        }

        private boolean doText(Attributes atts, Properties props, Paint paint) {
            if ("none".equals(atts.getValue("display"))) {
                return false;
            }
            Float fontSize = getFloatAttr("font-size", atts);
            if (fontSize == null) {
                fontSize = parseFloat(props.getString("font-size"), null);
            }
            if (fontSize != null) {
                paint.setTextSize(fontSize);
            }
            Typeface typeface = setTypeface(atts, props, mSharp.getAssetManager(), paint.getTypeface());
            if (typeface != null) {
                paint.setTypeface(typeface);
            }
            Align align = getTextAlign(atts);
            if (align != null) {
                paint.setTextAlign(getTextAlign(atts));
            }
            return true;
        }

        private boolean doStroke(Properties atts, RectF boundingBox) {
            return doStroke(atts, boundingBox, null);
        }

        private boolean doStroke(Properties atts, RectF boundingBox, String defStrokeString) {
            if ("none".equals(atts.getString("display"))) {
                return false;
            }
            String strokeString = atts.getString("stroke");
            if (strokeString == null) {
                strokeString = defStrokeString;
            }
            if (strokeString != null) {
                if (strokeString.equalsIgnoreCase("none")) {
                    mStrokePaint.setShader(null);
                    mStrokePaint.setPathEffect(null);
                    mStrokePaint.setColor(Color.TRANSPARENT);
                    // optimization: return false if transparent
                    return false;
                }
                // Check for other stroke attributes
                Float width = atts.getFloat("stroke-width");
                // Set defaults

                if (width != null) {
                    mStrokePaint.setStrokeWidth(width);
                }

                String dashArray = atts.getString("stroke-dasharray");
                if (dashArray != null && !dashArray.equalsIgnoreCase("none")) {
                    String[] splitDashArray = dashArray.split(", ?");
                    float[] intervals = new float[splitDashArray.length];
                    for (int i = 0; i < splitDashArray.length; i++) {
                        intervals[i] = Float.parseFloat(splitDashArray[i]);
                    }
                    mStrokePaint.setPathEffect(new DashPathEffect(intervals, 0));
                } else {
                    mStrokePaint.setPathEffect(null);
                }

                String linecap = atts.getString("stroke-linecap");
                if ("round".equals(linecap)) {
                    mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
                } else if ("square".equals(linecap)) {
                    mStrokePaint.setStrokeCap(Paint.Cap.SQUARE);
                } else if ("butt".equals(linecap)) {
                    mStrokePaint.setStrokeCap(Paint.Cap.BUTT);
                }
                String linejoin = atts.getString("stroke-linejoin");
                if ("miter".equals(linejoin)) {
                    mStrokePaint.setStrokeJoin(Paint.Join.MITER);
                } else if ("round".equals(linejoin)) {
                    mStrokePaint.setStrokeJoin(Paint.Join.ROUND);
                } else if ("bevel".equals(linejoin)) {
                    mStrokePaint.setStrokeJoin(Paint.Join.BEVEL);
                }

                // Display the stroke
                mStrokePaint.setStyle(Paint.Style.STROKE);

                if (strokeString.startsWith("url(#")) {
                    // It's a gradient stroke, look it up in our map
                    String id = strokeString.substring("url(#".length(), strokeString.length() - 1);
                    Gradient g = mGradientMap.get(id);
                    Shader shader = null;
                    if (g != null) {
                        shader = g.mShader;
                    }
                    if (shader != null) {
                        //Util.debug("Found shader!");
                        mStrokePaint.setShader(shader);
                        if (boundingBox != null) {
                            gradMatrix.set(g.mMatrix);
                            if (g.mBoundingBox) {
                                //Log.d(TAG, "gradient is bounding box");
                                gradMatrix.preTranslate(boundingBox.left, boundingBox.top);
                                gradMatrix.preScale(boundingBox.width(), boundingBox.height());
                            }
                            shader.setLocalMatrix(gradMatrix);
                        }
                        return true;
                    } else {
                        if (LOG_LEVEL >= LOG_LEVEL_WARN) {
                            Log.w(TAG, "Didn't find shader, using black: " + id);
                        }
                        mStrokePaint.setShader(null);
                        doColor(atts, Color.BLACK, true, mStrokePaint);
                        return true;
                    }
                } else {
                    Integer color = atts.getColor("stroke");
                    if (color != null) {
                        doColor(atts, color, false, mStrokePaint);
                        return true;
                    } else {
                        if (LOG_LEVEL >= LOG_LEVEL_WARN) {
                            Log.w(TAG, "Unrecognized stroke color, using black: " + strokeString);
                        }
                        doColor(atts, Color.BLACK, true, mStrokePaint);
                        return true;
                    }
                }
            } else {
                if (mStrokeSet) {
                    // If stroke is set, inherit from parent
                    // optimization: return false if transparent
                    return mStrokePaint.getColor() != Color.TRANSPARENT;
                } else {
                    // Default is no stroke
                    mStrokePaint.setShader(null);
                    mStrokePaint.setColor(Color.TRANSPARENT);
                    // optimization: return false if transparent
                    return false;
                }
            }
        }

        private Gradient doGradient(boolean isLinear, Attributes atts) {
            Gradient gradient = new Gradient();
            gradient.mId = getStringAttr("id", atts);
            gradient.mIsLinear = isLinear;
            if (isLinear) {
                gradient.mX1 = getFloatAttr("x1", atts, 0f);
                gradient.mX2 = getFloatAttr("x2", atts, 1f);
                gradient.mY1 = getFloatAttr("y1", atts, 0f);
                gradient.mY2 = getFloatAttr("y2", atts, 0f);
            } else {
                gradient.mX = getFloatAttr("cx", atts, 0f);
                gradient.mY = getFloatAttr("cy", atts, 0f);
                gradient.mRadius = getFloatAttr("r", atts, 0f);
            }
            String transform = getStringAttr("gradientTransform", atts);
            if (transform != null) {
                gradient.mMatrix = parseTransform(transform);
            }
            String spreadMethod = getStringAttr("spreadMethod", atts);
            if (spreadMethod == null) {
                spreadMethod = "pad";
            }

            gradient.mTileMode = (spreadMethod.equals("reflect")) ? Shader.TileMode.MIRROR :
                    (spreadMethod.equals("repeat")) ? Shader.TileMode.REPEAT :
                            Shader.TileMode.CLAMP;

            String unit = getStringAttr("gradientUnits", atts);
            if (unit == null) {
                unit = "objectBoundingBox";
            }
            gradient.mBoundingBox = !unit.equals("userSpaceOnUse");

            String xlink = getStringAttr("href", atts);
            if (xlink != null) {
                if (xlink.startsWith("#")) {
                    xlink = xlink.substring(1);
                }
                gradient.mXlink = xlink;
            }
            return gradient;
        }

        private void finishGradients() {
            for (Gradient gradient : mGradientMap.values()) {
                if (gradient.mXlink != null) {
                    Gradient parent = mGradientMap.get(gradient.mXlink);
                    if (parent != null) {
                        gradient.inherit(parent);
                    }
                }
                int[] colors = new int[gradient.mColors.size()];
                for (int i = 0; i < colors.length; i++) {
                    colors[i] = gradient.mColors.get(i);
                }
                float[] positions = new float[gradient.mPositions.size()];
                for (int i = 0; i < positions.length; i++) {
                    positions[i] = gradient.mPositions.get(i);
                }
                if (colors.length == 0) {
                    if (LOG_LEVEL >= LOG_LEVEL_WARN) {
                        Log.w(TAG, "Failed to parse gradient for id " + gradient.mId);
                    }
                }
                if (gradient.mIsLinear) {
                    gradient.mShader = new LinearGradient(gradient.mX1, gradient.mY1, gradient.mX2, gradient.mY2, colors, positions, gradient.mTileMode);
                } else {
                    gradient.mShader = new RadialGradient(gradient.mX, gradient.mY, gradient.mRadius, colors, positions, gradient.mTileMode);
                }

            }
        }

        /**
         * 完成样式解析
         */
        private void finishStyle() {
            //CSS样式文本内容, 解析css样式表
            String styleContent = mStyleText.toString();
            String[] styles = styleContent.split("\\}");
            for (String style : styles) {
                String[] parts = style.split("\\{");
                if (parts.length < 2) {
                } else {
                    String selector = parts[0].trim();
                    String properties = parts[1].trim();
                    // 处理样式信息
                    String[] selectors = selector.split(",");
                    HashMap<String, String> styleMap = parseStyle(properties);
                    for (String cls : selectors) {
                        String key = cls.trim();
                        HashMap<String, String> old = clsStyle.get(key);
                        if (old == null) {
                            clsStyle.put(cls.trim(), styleMap);
                        } else {
                            old.putAll(styleMap);
                        }
                    }
                }
            }
        }

        private HashMap<String, String> parseStyle(String properties) {
            HashMap<String, String> map = new HashMap<>();
            String[] props = properties.split(";");
            for (String prop : props) {
                String[] kv = prop.split(":");
                map.put(kv[0].trim(), kv[1].trim());
            }
            return map;
        }

        private void doColor(Properties atts, Integer color, boolean fillMode, Paint paint) {
            int c = (0xFFFFFF & color) | 0xFF000000;
            paint.setShader(null);
            paint.setColor(c);
            Float opacity = atts.getFloat("opacity");
            Float opacity2 = atts.getFloat(fillMode ? "fill-opacity" : "stroke-opacity");
            if (opacity == null) {
                opacity = opacity2;
            } else if (opacity2 != null) {
                opacity *= opacity2;
            }
            if (opacity == null) {
                paint.setAlpha(255);
            } else {
                paint.setAlpha((int) (255 * opacity));
            }
        }

        private boolean hidden = false;
        private int hiddenLevel = 0;
        private boolean boundsMode = false;

        private void doLimits(float x, float y) {
            if (x < mLimits.left) {
                mLimits.left = x;
            }
            if (x > mLimits.right) {
                mLimits.right = x;
            }
            if (y < mLimits.top) {
                mLimits.top = y;
            }
            if (y > mLimits.bottom) {
                mLimits.bottom = y;
            }
        }

        final private RectF limitRect = new RectF();

        private void doLimits(RectF box, Paint paint) {
            Matrix m = mMatrixStack.peek();
            m.mapRect(limitRect, box);
            float width2 = (paint == null) ? 0 : mStrokePaint.getStrokeWidth() / 2;
            doLimits(limitRect.left - width2, limitRect.top - width2);
            doLimits(limitRect.right + width2, limitRect.bottom + width2);
        }

        private void doLimits(RectF box) {
            doLimits(box, null);
        }

        private void pushTransform(Attributes atts) {
            final String transform = getStringAttr("transform", atts);
            boolean pushed = transform != null;
            mTransformStack.push(pushed);
            if (pushed) {
                mCanvas.save();
                final Matrix matrix = parseTransform(transform);
                if (matrix != null) {
                    mCanvas.concat(matrix);
                    matrix.postConcat(mMatrixStack.peek());
                    mMatrixStack.push(matrix);
                }
            }
        }

        private void popTransform() {
            if (mTransformStack.pop()) {
                mCanvas.restore();
                mMatrixStack.pop();
            }
        }

        private void hide() {
            if (!hidden) {
                hidden = true;
                hiddenLevel = 1;
            } else {
                hiddenLevel++;
            }
        }

        private void unhide() {
            if (hidden) {
                hiddenLevel--;
                if (hiddenLevel == 0) {
                    hidden = false;
                }
            }
        }

        private Align getTextAlign(Attributes atts) {
            String align = getStringAttr("text-anchor", atts);
            if (align == null) {
                return null;
            }
            if ("middle".equals(align)) {
                return Align.CENTER;
            } else if ("end".equals(align)) {
                return Align.RIGHT;
            } else {
                return Align.LEFT;
            }
        }

        private Typeface setTypeface(Attributes atts, Properties props, AssetManager assetManager, Typeface defaultTypeface) {
            // Prefer a dedicated attribute
            String family = getStringAttr("font-family", atts);
            if (family == null) {
                // Fall back to reading from "style" attribute
                family = props.getString("font-family");
            }
            // Prefer a dedicated attribute
            String style = getStringAttr("font-style", atts);
            if (style == null) {
                // Fall back to reading from "style" attribute
                style = props.getString("font-style");
            }
            // Prefer a dedicated attribute
            String weight = getStringAttr("font-weight", atts);
            if (weight == null) {
                // Fall back to reading from "style" attribute
                weight = props.getString("font-weight");
            }

            // Set the style parameters
            int styleParam = Typeface.NORMAL;
            if ("italic".equals(style)) {
                styleParam |= Typeface.ITALIC;
            }
            if ("bold".equals(weight)) {
                styleParam |= Typeface.BOLD;
            }

            Typeface plain;
            if (family != null) {
                // Attempt to load the typeface
                if (assetManager != null) {
                    Pattern pattern = Pattern.compile("'(.+?)'(?:,'(.+?)')*");
                    Matcher matcher = pattern.matcher(family);
                    if (matcher.matches()) {
                        for (int i = 1; i < matcher.groupCount() + 1; i++) {
                            if (matcher.group(i) != null) {
                                family = matcher.group(i);
                            }
                        }
                    }
                    // Compose a filename
                    String typefaceFile = "fonts/" + family + ".ttf";
                    try {
                        plain = Typeface.createFromAsset(assetManager, typefaceFile);
                        if (LOG_LEVEL >= LOG_LEVEL_INFO) {
                            Log.d(TAG, "Loaded typeface from assets: " + typefaceFile);
                        }
                    } catch (RuntimeException e) {
                        boolean found = true;
                        try {
                            String[] fonts = assetManager.list("fonts/");
                            found = false;
                            for (String font : fonts) {
                                if (typefaceFile.equals(font)) {
                                    found = true;
                                }
                            }
                        } catch (IOException e1) {
                            if (LOG_LEVEL >= LOG_LEVEL_ERROR) {
                                Log.e(TAG, "Failed listing assets directory for /fonts", e);
                            }
                        }
                        if (!found) {
                            if (LOG_LEVEL >= LOG_LEVEL_ERROR) {
                                Log.e(TAG, "Typeface is missing from assets: " + typefaceFile);
                            }
                        } else {
                            if (LOG_LEVEL >= LOG_LEVEL_ERROR) {
                                Log.e(TAG, "Failed to create typeface from assets: " + typefaceFile, e);
                            }
                        }
                        plain = null;
                    }
                    if (plain != null) {
                        // Adapt the type face with the style
                        return Typeface.create(plain, styleParam);
                    }
                } else {
                    if (LOG_LEVEL >= LOG_LEVEL_ERROR) {
                        Log.e(TAG, "Typefaces can only be loaded if assets are provided; " +
                                "invoke " + Sharp.class.getSimpleName() + " with .withAssets()");
                    }
                }
            }
            if (defaultTypeface == null) {
                return Typeface.create(family, styleParam);
            } else {
                return Typeface.create(defaultTypeface, styleParam);
            }
        }

        //</editor-fold desc="xml属性解析">

        //<editor-fold desc="xml文档处理">

        @Override
        public void startDocument() throws SAXException {
            // Set up prior to parsing a doc
            mStrokePaint = new Paint();
            mStrokePaint.setAntiAlias(true);
            mStrokePaint.setStyle(Paint.Style.STROKE);

            mFillPaint = new Paint();
            mFillPaint.setAntiAlias(true);
            mFillPaint.setStyle(Paint.Style.FILL);

            mMatrixStack.push(new Matrix());
        }

        @Override
        public void endDocument() throws SAXException {
            // Clean up after parsing a doc
            mDefs.clear();
            mMatrixStack.clear();
        }

        private DrawElement createDrawElement(DrawElement.DrawType type) {
            DrawElement drawElement = new DrawElement();
            drawElement.type = type;
            drawElement.svgRect = mBounds;
            drawElement.viewBoxStr = viewBoxStr;
            drawElement.widthStr = widthStr;
            drawElement.heightStr = heightStr;
            drawElement.canvasMatrix = mCanvas.getMatrix();
            drawElement.readingDefs = mReadingDefs;
            drawElement.updateStack(mMatrixStack, mGroupStack);
            return drawElement;
        }

        /**
         * 接收元素开始的通知
         * [localName] 不带前缀的元素名字
         * [qName] 带前缀的元素名字
         */
        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
            if (!mReadIgnoreStack.empty()) {
                // Ignore
                return;
            }
            String id = getStringAttr("id", atts);

            // Reset paint opacity
            mStrokePaint.setAlpha(255);
            mFillPaint.setAlpha(255);
            // Ignore everything but rectangles in bounds mode
            if (boundsMode) {
                if (localName.equals("rect")) {
                    Float x = getFloatAttr("x", atts);
                    if (x == null) {
                        x = 0f;
                    }
                    Float y = getFloatAttr("y", atts);
                    if (y == null) {
                        y = 0f;
                    }
                    Float width = getFloatAttr("width", atts);
                    Float height = getFloatAttr("height", atts);
                    mBounds = new RectF(x, y, x + width, y + height);
                }
                return;
            }

            if (!hidden && localName.equals("use")) {
                localName = "path";
            }

            if (localName.equals("svg")) {
                float x = 0, y = 0, width = -1, height = -1;
                viewBoxStr = getStringAttr("viewBox", atts);
                widthStr = getStringAttr("width", atts);
                heightStr = getStringAttr("height", atts);
                if (viewBoxStr != null) {
                    // Prefer viewBox
                    String[] coords = viewBoxStr.split(" ");
                    if (coords.length == 4) {
                        x = parseFloat(coords[0], 0f);
                        y = parseFloat(coords[1], 0f);
                        width = parseFloat(coords[2], -1f);
                        height = parseFloat(coords[3], -1f);
                    }
                } else {
                    Float svgWidth = getFloatAttr("width", atts);
                    Float svgHeight = getFloatAttr("height", atts);
                    if (svgWidth != null && svgHeight != null) {
                        width = (int) Math.ceil(svgWidth);
                        height = (int) Math.ceil(svgHeight);
                    }
                }
                if (width < 0 || height < 0) {
                    width = 100;
                    height = 100;
                    if (LOG_LEVEL >= LOG_LEVEL_WARN) {
                        Log.w(TAG, "Element '" + localName + "' does not provide its dimensions; using " + width + "x" + height);
                    }
                }
                mBounds = new RectF(x, y, x + width, y + height);
                //Log.d(TAG, "svg boundaries: " + mBounds);
                mCanvas = mPicture.beginRecording(
                        (int) Math.ceil(mBounds.width()),
                        (int) Math.ceil(mBounds.height()));
                mCanvas.translate(-mBounds.left, -mBounds.top);
                //Log.d(TAG, "canvas size: " + mCanvas.getWidth() + "x" + mCanvas.getHeight());
                onSvgStart();
            } else if (localName.equals("defs")) {
                mReadingDefs = true;
            } else if (localName.equals("style")) {
                //style 样式标签
                mReadingStyle = true;
                mStyleText = new StringBuilder();
            } else if (localName.equals("linearGradient")) {
                mGradient = doGradient(true, atts);
            } else if (localName.equals("radialGradient")) {
                mGradient = doGradient(false, atts);
            } else if (localName.equals("stop")) {
                if (mGradient != null) {
                    Properties props = new Properties(atts, clsStyle);
                    float offset = props.getFloat("offset", 0);
                    int color = props.getColor("stop-color");
                    float alpha = props.getFloat("stop-opacity", 1);
                    int alphaInt = Math.round(255 * alpha);
                    color |= (alphaInt << 24);
                    mGradient.mPositions.add(offset);
                    mGradient.mColors.add(color);
                }
            } else if (localName.equals("g")) {
                Properties props = new Properties(atts, clsStyle);
                // Check to see if this is the "bounds" layer
                if ("bounds".equalsIgnoreCase(id)) {
                    boundsMode = true;
                }
                if (hidden) {
                    hiddenLevel++;
                }
                // Go in to hidden mode if display is "none"
                if ("none".equals(props.getString("display"))) {
                    if (!hidden) {
                        hidden = true;
                        hiddenLevel = 1;
                    }
                }

                // If the group has an applied opacity, start drawing in a new canvas
                Float opacity = getFloatAttr("opacity", atts);
                if (opacity == null) {
                    opacity = props.getFloat("opacity");
                }
                if (opacity != null && opacity < 1f) {
                    // FIXME Ideally, we should compute the bounds of the enclosed group, and create
                    //       the layer exactly to its size; see issue #6
                    // Apply inverse of matrix to correct for any transformations
                    // It's okay to use getMatrix() here as we may assume its a software layer
                    Matrix m = mCanvas.getMatrix();
                    m.invert(m);
                    RectF r = new RectF(0, 0, mCanvas.getWidth(), mCanvas.getHeight());
                    m.mapRect(r);
                    // Store the layer with the opacity value
                    mCanvas.saveLayerAlpha(r, (int) (255 * opacity), Canvas.ALL_SAVE_FLAG);
                } else {
                    mCanvas.save();
                }

                pushTransform(atts);

                mFillPaintStack.push(new Paint(mFillPaint));
                mStrokePaintStack.push(new Paint(mStrokePaint));
                mFillSetStack.push(mFillSet);
                mStrokeSetStack.push(mStrokeSet);

                doFill(props, null);
                doStroke(props, null);

                mFillSet |= (props.getString("fill") != null);
                mStrokeSet |= (props.getString("stroke") != null);

                SvgGroup group = new SvgGroup(id);
                mGroupStack.push(group);
                // FIXME compute bounds before drawing?
                onSvgElement(id, group, null, null);
            } else if (!hidden && localName.equals("rect")) {
                Float x = getFloatAttr("x", atts, 0f);
                Float y = getFloatAttr("y", atts, 0f);

                Float width = getFloatAttr("width", atts);
                Float height = getFloatAttr("height", atts);
                Float rx = getFloatAttr("rx", atts);
                Float ry = getFloatAttr("ry", atts);
                if (ry == null) {
                    ry = rx;
                }
                if (rx == null) {
                    rx = ry;
                }
                if (rx == null || rx < 0) {
                    rx = 0f;
                }
                if (ry == null || ry < 0) {
                    ry = 0f;
                }
                if (rx > width / 2) {
                    rx = width / 2;
                }
                if (ry > height / 2) {
                    ry = height / 2;
                }
                pushTransform(atts);
                Properties props = new Properties(atts, clsStyle);
                mRect.set(x, y, x + width, y + height);

                DrawElement drawElement = createDrawElement(DrawElement.DrawType.ROUND_RECT);
                drawElement.element = mRect;
                drawElement.rx = rx;
                drawElement.ry = ry;
                drawElement.dataName = props.getString("data-name");
                if (doFill(props, mRect)) {
                    mRect = onSvgElement(id, mRect, mRect, mFillPaint);
                    if (mRect != null) {
                        drawElement.paint = mFillPaint;
                        drawElement.element = mRect;
                        if (!onCanvasDraw(mCanvas, drawElement)) {
                            mCanvas.drawRoundRect(mRect, rx, ry, mFillPaint);
                            onSvgElementDrawn(id, mRect, mFillPaint);
                        }
                    }
                    doLimits(mRect);
                }
                if (doStroke(props, mRect)) {
                    mRect = onSvgElement(id, mRect, mRect, mStrokePaint);
                    if (mRect != null) {
                        drawElement.paint = mStrokePaint;
                        drawElement.element = mRect;
                        if (!onCanvasDraw(mCanvas, drawElement)) {
                            mCanvas.drawRoundRect(mRect, rx, ry, mStrokePaint);
                            onSvgElementDrawn(id, mRect, mStrokePaint);
                        }
                    }
                    doLimits(mRect, mStrokePaint);
                }
                popTransform();
            } else if (!hidden && localName.equals("line")) {
                Float x1 = getFloatAttr("x1", atts);
                Float x2 = getFloatAttr("x2", atts);
                Float y1 = getFloatAttr("y1", atts);
                Float y2 = getFloatAttr("y2", atts);
                Properties props = new Properties(atts, clsStyle);
                if (doStroke(props, mRect, "black")) {
                    pushTransform(atts);
                    mLine.set(x1, y1, x2, y2);
                    mRect.set(mLine);
                    mLine = onSvgElement(id, mLine, mRect, mStrokePaint);
                    if (mLine != null) {
                        DrawElement drawElement = createDrawElement(DrawElement.DrawType.LINE);
                        drawElement.paint = mStrokePaint;
                        drawElement.element = mLine;
                        drawElement.dataName = props.getString("data-name");
                        if (!onCanvasDraw(mCanvas, drawElement)) {
                            mCanvas.drawLine(mLine.left, mLine.top, mLine.right, mLine.bottom, mStrokePaint);
                            onSvgElementDrawn(id, mLine, mStrokePaint);
                        }
                    }
                    doLimits(mRect, mStrokePaint);
                    popTransform();
                }
            } else if (!hidden && (localName.equals("circle") || localName.equals("ellipse"))) {
                Float centerX, centerY, radiusX, radiusY;

                centerX = getFloatAttr("cx", atts);
                centerY = getFloatAttr("cy", atts);
                if (localName.equals("ellipse")) {
                    radiusX = getFloatAttr("rx", atts);
                    radiusY = getFloatAttr("ry", atts);
                } else {
                    radiusX = radiusY = getFloatAttr("r", atts);
                }
                if (centerX != null && centerY != null && radiusX != null && radiusY != null) {
                    pushTransform(atts);
                    Properties props = new Properties(atts, clsStyle);
                    mRect.set(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY);

                    DrawElement drawElement = createDrawElement(DrawElement.DrawType.OVAL);
                    drawElement.dataName = props.getString("data-name");
                    if (doFill(props, mRect)) {
                        mRect = onSvgElement(id, mRect, mRect, mFillPaint);
                        if (mRect != null) {
                            drawElement.paint = mFillPaint;
                            drawElement.element = mRect;
                            if (!onCanvasDraw(mCanvas, drawElement)) {
                                mCanvas.drawOval(mRect, mFillPaint);
                                onSvgElementDrawn(id, mRect, mFillPaint);
                            }
                        }
                        doLimits(mRect);
                    }
                    if (doStroke(props, mRect)) {
                        mRect = onSvgElement(id, mRect, mRect, mStrokePaint);
                        if (mRect != null) {
                            drawElement.paint = mStrokePaint;
                            drawElement.element = mRect;
                            if (!onCanvasDraw(mCanvas, drawElement)) {
                                mCanvas.drawOval(mRect, mStrokePaint);
                                onSvgElementDrawn(id, mRect, mStrokePaint);
                            }
                        }
                        doLimits(mRect, mStrokePaint);
                    }
                    popTransform();
                }
            } else if (!hidden && (localName.equals("polygon") || localName.equals("polyline"))) {
                ArrayList<Float> points = getNumberParseAttr("points", atts);
                if (points != null) {
                    Path p = new Path();
                    if (points.size() > 1) {
                        pushTransform(atts);
                        Properties props = new Properties(atts, clsStyle);
                        p.moveTo(points.get(0), points.get(1));
                        for (int i = 2; i < points.size(); i += 2) {
                            float x = points.get(i);
                            float y = points.get(i + 1);
                            p.lineTo(x, y);
                        }
                        // Don't close a polyline
                        boolean closePath = localName.equals("polygon");
                        if (closePath) {
                            p.close();
                        }
                        p.computeBounds(mRect, false);

                        DrawElement drawElement = createDrawElement(DrawElement.DrawType.PATH);
                        drawElement.pathBounds = mRect;
                        drawElement.dataName = props.getString("data-name");
                        drawElement.updatePointsData(points, closePath);
                        if (doFill(props, mRect)) {
                            p = onSvgElement(id, p, mRect, mFillPaint);
                            if (p != null) {
                                drawElement.paint = mFillPaint;
                                drawElement.element = p;
                                if (!onCanvasDraw(mCanvas, drawElement)) {
                                    mCanvas.drawPath(p, mFillPaint);
                                    onSvgElementDrawn(id, p, mFillPaint);
                                }
                            }
                            doLimits(mRect);
                        }
                        if (doStroke(props, mRect)) {
                            p = onSvgElement(id, p, mRect, mStrokePaint);
                            if (p != null) {
                                drawElement.paint = mStrokePaint;
                                drawElement.element = p;
                                if (!onCanvasDraw(mCanvas, drawElement)) {
                                    mCanvas.drawPath(p, mStrokePaint);
                                    onSvgElementDrawn(id, p, mStrokePaint);
                                }
                            }
                            doLimits(mRect, mStrokePaint);
                        }
                        popTransform();
                    }
                }
            } else if (!hidden && localName.equals("path")) {
                String d = getStringAttr("d", atts);

                if (mReadingDefs) {
                    mDefs.put(id, getStringAttr("d", atts));
                    return;
                } else if (TextUtils.isEmpty(d)) {
                    String href = getStringAttr("href", atts);
                    if (href != null && href.startsWith("#")) {
                        href = href.substring(1);
                    }
                    if (href != null && mDefs.containsKey(href)) {
                        d = mDefs.get(href);
                    }
                    if (TextUtils.isEmpty(d)) {
                        return;
                    }
                }
                Path p = doPath(d);
                pushTransform(atts);
                Properties props = new Properties(atts, clsStyle);
                p.computeBounds(mRect, false);

                DrawElement drawElement = createDrawElement(DrawElement.DrawType.PATH);
                drawElement.data = d;
                drawElement.pathBounds = mRect;
                drawElement.dataName = props.getString("data-name");
                if (doFill(props, mRect)) {
                    p = onSvgElement(id, p, mRect, mFillPaint);
                    if (p != null) {
                        drawElement.paint = mFillPaint;
                        drawElement.element = p;
                        if (!onCanvasDraw(mCanvas, drawElement)) {
                            mCanvas.drawPath(p, mFillPaint);
                            onSvgElementDrawn(id, p, mFillPaint);
                        }
                    }
                    doLimits(mRect);
                }
                if (doStroke(props, mRect)) {
                    p = onSvgElement(id, p, mRect, mStrokePaint);
                    if (p != null) {
                        drawElement.paint = mStrokePaint;
                        drawElement.element = p;
                        if (!onCanvasDraw(mCanvas, drawElement)) {
                            mCanvas.drawPath(p, mStrokePaint);
                            onSvgElementDrawn(id, p, mStrokePaint);
                        }
                    }
                    doLimits(mRect, mStrokePaint);
                }
                popTransform();
            } else if (!hidden && localName.equals("image")) {
                //解析svg标签中的image标签
                Properties props = new Properties(atts, clsStyle);


                if (!"none".equals(props.getString("display"))) {
                    pushTransform(atts);
                    Float width = getFloatAttr("width", atts);
                    Float height = getFloatAttr("height", atts);
                    String href = getStringAttr("href", atts);

                    if (href != null && href.startsWith("#")) {
                        href = href.substring(1);
                    }
                    if (href != null && mDefs.containsKey(href)) {
                        href = mDefs.get(href);
                    }
                    //如果href是base64图片数据格式
                    if (href != null && href.startsWith("data:image/")) {
                        //data:image/png;base64,iVBORw0KGgoAAAAN
                        int index = href.indexOf("base64,");
                        if (index > 0) {
                            href = href.substring(index + 7);
                        }
                        byte[] bytes = Base64.decode(href, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                        if (bitmap != null) {
                            if (width == null) {
                                width = (float) bitmap.getWidth();
                            }
                            if (height == null) {
                                height = (float) bitmap.getHeight();
                            }
                            mRect.set(0, 0, width, height);
                            DrawElement drawElement = createDrawElement(DrawElement.DrawType.IMAGE);
                            drawElement.paint = mStrokePaint;
                            drawElement.element = bitmap;
                            drawElement.dataName = props.getString("data-name");

                            if (!onCanvasDraw(mCanvas, drawElement)) {
                                mCanvas.drawBitmap(bitmap, null, mRect, mStrokePaint);
                                onSvgElementDrawn(id, bitmap, mStrokePaint);
                            }
                        }
                    }
                }
                popTransform();
            } else if (!hidden && localName.equals("text")) {
                pushTransform(atts);
                mTextStack.push(new SvgText(atts, mTextStack.isEmpty() ? null : mTextStack.peek()));
            } else if (!hidden && localName.equals("tspan")) {
                mTextStack.push(new SvgText(atts, mTextStack.isEmpty() ? null : mTextStack.peek()));
            } else if (!hidden && localName.equals("clipPath")) {
                hide();
                if (LOG_LEVEL >= LOG_LEVEL_WARN) {
                    Log.w(TAG, "Unsupported SVG command: " + localName);
                }
            } else if (!hidden) {
                switch (localName) {
                    case "metadata":
                        // Ignore, including children //需要忽略的元素以及子元素
                        mReadIgnoreStack.push(localName);
                        break;
                    default:
                        if (LOG_LEVEL >= LOG_LEVEL_WARN) {
                            //无法识别的svg指令
                            Log.w(TAG, "Unrecognized SVG command: " + localName);
                        }
                        break;
                }
            }
        }

        /**
         * 接收元素结束的通知
         */
        @Override
        public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
            if (!mReadIgnoreStack.empty() && localName.equals(mReadIgnoreStack.peek())) {
                // Ignore
                mReadIgnoreStack.pop();
                return;
            }
            switch (localName) {
                case "svg":
                    onSvgEnd();
                    mPicture.endRecording();
                    break;
                case "text":
                case "tspan":
                    if (!mTextStack.isEmpty()) {
                        SvgText text = mTextStack.pop();
                        if (text != null) {
                            text.render(mCanvas);
                        }
                    }
                    if (localName.equals("text")) {
                        popTransform();
                    }
                    break;
                case "linearGradient":
                case "radialGradient":
                    if (mGradient.mId != null) {
                        mGradientMap.put(mGradient.mId, mGradient);
                    }
                    break;
                case "defs":
                    finishGradients();
                    mReadingDefs = false;
                    break;
                case "style":
                    finishStyle();
                    mReadingStyle = false;
                    break;
                case "g":
                    SvgGroup group = mGroupStack.pop();
                    onSvgElementDrawn(group.id, group, null);

                    if (boundsMode) {
                        boundsMode = false;
                    }
                    // Break out of hidden mode
                    unhide();
                    // Clear gradient map
                    //gradientRefMap.clear();
                    popTransform();
                    mFillPaint = mFillPaintStack.pop();
                    mFillSet = mFillSetStack.pop();
                    mStrokePaint = mStrokePaintStack.pop();
                    mStrokeSet = mStrokeSetStack.pop();

                    // Restore the previous canvas
                    mCanvas.restore();
                    break;
                case "clipPath":
                    // Break out of hidden mode
                    unhide();
                    break;
            }
        }

        /**
         * 接收字符的通知, 标签之间的字符串内容. 可以通过此方法修改字符编码, 也可以通过此方法, 剔除字符串两边的无效字符
         */
        @Override
        public void characters(char[] ch, int start, int length) {
            if (!mTextStack.isEmpty()) {
                mTextStack.peek().setText(ch, start, length);
            }
            if (mReadingStyle) {
                mStyleText.append(ch, start, length);
            }
        }

        //</editor-fold desc="xml文档处理">

        public class SvgGroup {

            @Nullable
            public final String id;

            public SvgGroup(@Nullable String id) {
                this.id = id;
            }
        }

        /**
         * Holds text properties as these are only applied with the end tag is encountered.
         */
        public class SvgText {

            public final static int LEFT = 0;
            public final static int CENTER = 1;
            public final static int RIGHT = 2;

            public final static int BOTTOM = 0;
            public final static int MIDDLE = 1;
            public final static int TOP = 2;

            public final String id;
            public final float x, y;
            public float xOffset, yOffset;
            public final String[] xCoords;
            public TextPaint stroke = null, fill = null;
            public String text;
            public int hAlign = LEFT, vAlign = BOTTOM;
            public RectF bounds = new RectF();
            public String dataName;

            public SvgText(Attributes atts, SvgText parentText) {
                id = getStringAttr("id", atts);
                dataName = getStringAttr("data-name", atts);
                String xStr = getStringAttr("x", atts);
                if (xStr != null && (xStr.contains(",") || xStr.contains(" "))) {
                    // x is a comma- or space-separated list of coordinates; see:
                    // http://www.w3.org/TR/SVG/text.html#TSpanElementXAttribute
                    x = parentText != null ? parentText.x : 0f;
                    xCoords = xStr.split("[, ]");
                } else {
                    // x is a single coordinate
                    x = parseFloat(xStr, parentText != null ? parentText.x : 0f);
                    xCoords = parentText != null ? parentText.xCoords : null;
                }
                y = getFloatAttr("y", atts, parentText != null ? parentText.y : 0f);
                text = null;

                Properties props = new Properties(atts, clsStyle);
                if (doFill(props, null)) {
                    fill = new TextPaint(parentText != null && parentText.fill != null
                            ? parentText.fill
                            : mFillPaint);
                    // Fix for https://code.google.com/p/android/issues/detail?id=39755
                    fill.setLinearText(true);
                    doText(atts, props, fill);
                }
                if (doStroke(props, null)) {
                    stroke = new TextPaint(parentText != null && parentText.stroke != null
                            ? parentText.stroke
                            : mStrokePaint);
                    // Fix for https://code.google.com/p/android/issues/detail?id=39755
                    stroke.setLinearText(true);
                    doText(atts, props, stroke);
                }
                // Horizontal alignment
                String halign = getStringAttr("text-align", atts);
                if (halign == null) {
                    halign = props.getString("text-align");
                }
                if (halign == null && parentText != null) {
                    hAlign = parentText.hAlign;
                } else {
                    if ("center".equals(halign)) {
                        hAlign = CENTER;
                    } else if ("right".equals(halign)) {
                        hAlign = RIGHT;
                    }
                }
                // Vertical alignment
                String valign = getStringAttr("alignment-baseline", atts);
                if (valign == null) {
                    valign = props.getString("alignment-baseline");
                }
                if (valign == null && parentText != null) {
                    vAlign = parentText.vAlign;
                } else {
                    if ("middle".equals(valign)) {
                        vAlign = MIDDLE;
                    } else if ("top".equals(valign)) {
                        vAlign = TOP;
                    }
                }
            }

            public void setText(char[] ch, int start, int len) {
                if (text == null) {
                    text = new String(ch, start, len);
                } else {
                    text += new String(ch, start, len);
                }
                if (sTextDynamic != null && sTextDynamic.containsKey(text)) {
                    text = sTextDynamic.get(text);
                }
            }

            public void render(Canvas canvas) {
                if (text == null) {
                    // Nothing to draw
                    return;
                }
                // Correct vertical alignment
                Rect bounds = new Rect();
                Paint paint = stroke == null ? fill : stroke;
                paint.getTextBounds(text, 0, text.length(), bounds);
                //Log.d(TAG, "Adjusting y=" + y + " for boundaries=" + bounds);
                switch (vAlign) {
                    case TOP:
                        yOffset = bounds.height();
                        break;
                    case MIDDLE:
                        yOffset = -bounds.centerY();
                        break;
                    case BOTTOM:
                        // Default; no correction needed
                        break;
                }
                float width = paint.measureText(text);
                // Correct horizontal alignment
                switch (hAlign) {
                    case LEFT:
                        // Default; no correction needed
                        break;
                    case CENTER:
                        xOffset = -width / 2f;
                        break;
                    case RIGHT:
                        xOffset = -width;
                }
                this.bounds.set(x, y, x + width, y + bounds.height());

                //Log.i(TAG, "Drawing: " + text + " " + x + "," + y);
                if (text != null) {
                    if (fill != null) {
                        drawText(canvas, this, true);
                    }
                    if (stroke != null) {
                        drawText(canvas, this, false);
                    }
                }
            }

            private void drawText(Canvas canvas, SvgText text, boolean fill) {
                TextPaint paint = fill ? text.fill : text.stroke;
                text = onSvgElement(id, text, text.bounds, paint);

                DrawElement drawElement = createDrawElement(DrawElement.DrawType.TEXT);
                drawElement.paint = paint;
                drawElement.element = text;
                drawElement.dataName = text.dataName;

                if (!onCanvasDraw(canvas, drawElement)) {
                    if (text != null) {
                        if (text.xCoords != null && text.xCoords.length > 0) {
                            // Draw each glyph separately according to their x coordinates
                            int i = 0;
                            Float thisX = parseFloat(text.xCoords[0], null);
                            Float nextX = 0f;
                            if (thisX != null) {
                                float x = thisX;
                                for (i = 0; i < text.text.length(); i++) {
                                    if (i >= text.xCoords.length) {
                                        // Break early so we can draw the rest of the characters in one go
                                        i--;
                                        break;
                                    }
                                    if (i + 1 < text.xCoords.length) {
                                        nextX = parseFloat(text.xCoords[i + 1], null);
                                        if (nextX == null) {
                                            // Break early so we can draw the rest of the characters in one go
                                            i--;
                                            break;
                                        }
                                    }
                                    // Draw the glyph
                                    String s = new String(new char[]{text.text.charAt(i)});
                                    canvas.drawText(s, x + text.xOffset, text.y + text.yOffset, paint);
                                    x = nextX;
                                }
                            }
                            if (i < text.text.length()) {
                                canvas.drawText(text.text.substring(i), x + text.xOffset, text.y + text.yOffset, paint);
                            }
                        } else {
                            // Draw the entire string //绘制完整的字符串
                            canvas.drawText(text.text, text.x + text.xOffset, text.y + text.yOffset, paint);
                        }
                        onSvgElementDrawn(text.id, text, paint);
                    }
                }
            }
        }
    }

    public interface DrawableCallback {
        void onDrawableReady(SharpDrawable sharpDrawable);
    }

    public interface PictureCallback {
        void onPictureReady(SharpPicture sharpPicture);
    }
}
