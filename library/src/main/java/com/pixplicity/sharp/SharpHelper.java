package com.pixplicity.sharp;

import org.xml.sax.Attributes;

/**
 * @author <a href="mailto:angcyo@126.com">angcyo</a>
 * @since 2022/04/14
 */
public class SharpHelper {

    /**
     * 获取[Attributes]日志信息
     */
    public static String logAttributes(Attributes attributes) {
        StringBuilder builder = new StringBuilder();
        int n = attributes.getLength();
        for (int i = 0; i < n; i++) {
            String key = attributes.getLocalName(i);
            String value = attributes.getValue(i);
            builder.append(key);
            builder.append(":");
            builder.append(value);
            if (i != n - 1) {
                builder.append(System.getProperty("line.separator"));
            }
        }
        return builder.toString();
    }
}
