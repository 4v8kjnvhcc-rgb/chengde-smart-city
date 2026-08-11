package com.chengde.smartcity.common.util;

import java.util.Locale;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

/**
 * 中文 → 拼音首字母小写；用于手动上传 ODS 表名 / 字段名。
 */
public final class PinyinInitials {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinInitials() {}

    /** 仅保留字母数字下划线的拼音首字母串，空则返回 x */
    public static String of(String text) {
        if (text == null || text.isBlank()) {
            return "x";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(Character.toLowerCase(c));
            } else if (isChinese(c)) {
                sb.append(initialOf(c));
            }
        }
        String s = sb.toString().replaceAll("[^a-z0-9_]", "");
        if (s.isEmpty()) {
            s = "x";
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "c_" + s;
        }
        if (s.length() > 48) {
            s = s.substring(0, 48);
        }
        return s;
    }

    /** ods_up_ + 模板中文名拼音首字母 */
    public static String suggestOdsUpTable(String templateChineseName) {
        String name = templateChineseName == null ? "" : templateChineseName.trim();
        int idx = name.indexOf('_');
        if (idx > 0) {
            String left = name.substring(0, idx);
            String right = name.substring(idx + 1);
            // 「文件名_同名工作表」去重后只保留一遍
            if (!left.isEmpty() && left.equals(right)) {
                name = left;
            }
        }
        return "ods_up_" + of(name);
    }

    /** 表头中文 → 物理字段名（拼音首字母）；已是标识符则小写清洗 */
    public static String toPhysicalColumn(String header) {
        if (header == null || header.isBlank()) {
            return "col";
        }
        String trimmed = header.trim();
        boolean hasChinese = trimmed.chars().anyMatch(ch -> isChinese((char) ch));
        if (hasChinese) {
            return of(trimmed);
        }
        String s = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (s.isEmpty()) {
            s = "col";
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "c_" + s;
        }
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s;
    }

    private static char initialOf(char c) {
        try {
            String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
            if (arr != null && arr.length > 0 && arr[0] != null && !arr[0].isEmpty()) {
                return arr[0].charAt(0);
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        return 'x';
    }

    private static boolean isChinese(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN;
    }
}
