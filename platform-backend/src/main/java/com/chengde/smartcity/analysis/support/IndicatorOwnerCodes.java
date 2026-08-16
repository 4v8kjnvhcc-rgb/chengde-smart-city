package com.chengde.smartcity.analysis.support;

import java.util.Locale;

/** 旧 ind_area 无所属系统列：按名称/库名推导 population|legal|macro|key。 */
public final class IndicatorOwnerCodes {

    private IndicatorOwnerCodes() {}

    public static String derive(String name, String dbSchema) {
        String n = name == null ? "" : name;
        String s = dbSchema == null ? "" : dbSchema;
        String t = (n + " " + s).toLowerCase(Locale.ROOT);
        if (contains(t, "legal", "法人")) {
            return "legal";
        }
        if (contains(t, "population", "popu", "人口")
                || "ind_basic_database".equalsIgnoreCase(s.trim())
                || n.contains("基础库")) {
            return "population";
        }
        if (contains(t, "key_field", "key_domain", "重点", "公共安全", "社会治理", "城市管理")
                || contains(t, "security", "social", "urban")) {
            return "key";
        }
        if (contains(t, "macro", "宏观", "产业", "经济")
                || contains(t, "eco", "industry")) {
            return "macro";
        }
        return "population";
    }

    public static boolean matchesOwner(String owner, String name, String dbSchema) {
        if (owner == null || owner.isBlank() || "all".equalsIgnoreCase(owner) || "gov".equalsIgnoreCase(owner)) {
            return true;
        }
        return owner.trim().equalsIgnoreCase(derive(name, dbSchema));
    }

    private static boolean contains(String hay, String... needles) {
        for (String n : needles) {
            if (n != null && !n.isBlank() && hay.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
