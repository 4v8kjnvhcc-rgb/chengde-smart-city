package com.chengde.smartcity.masterdata.support;

import java.util.Locale;
import java.util.Set;

/**
 * 平台分层库命名与表名前缀解析。
 */
public final class DataLayerSupport {

    public static final String CONTROL = "smart_city";
    public static final String ODS = "smart_city_ods";
    public static final String DWD = "smart_city_dwd";
    public static final String DWS = "smart_city_dws";
    public static final String ADS = "smart_city_ads";

    private static final Set<String> PLATFORM_DBS = Set.of(CONTROL, ODS, DWD, DWS, ADS);

    private DataLayerSupport() {
    }

    public static String databaseForLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return ODS;
        }
        return switch (layer.trim().toUpperCase(Locale.ROOT)) {
            case "CONTROL" -> CONTROL;
            case "ODS" -> ODS;
            case "DWD" -> DWD;
            case "DWS" -> DWS;
            case "ADS" -> ADS;
            default -> ODS;
        };
    }

    public static String layerForDatabase(String db) {
        if (db == null || db.isBlank()) {
            return "ODS";
        }
        return switch (db.trim().toLowerCase(Locale.ROOT)) {
            case "smart_city" -> "CONTROL";
            case "smart_city_ods" -> "ODS";
            case "smart_city_dwd" -> "DWD";
            case "smart_city_dws" -> "DWS";
            case "smart_city_ads" -> "ADS";
            default -> "ODS";
        };
    }

    public static String layerForTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "ODS";
        }
        String lower = tableName.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("ods_")) {
            return "ODS";
        }
        if (lower.startsWith("dwd_")) {
            return "DWD";
        }
        if (lower.startsWith("dws_")) {
            return "DWS";
        }
        if (lower.startsWith("ads_")) {
            return "ADS";
        }
        return "ODS";
    }

    public static String qualify(String db, String table) {
        return "`" + sanitizeIdent(db) + "`.`" + sanitizeIdent(table) + "`";
    }

    public static boolean isPlatformLayerDb(String db) {
        if (db == null || db.isBlank()) {
            return false;
        }
        return PLATFORM_DBS.contains(db.trim().toLowerCase(Locale.ROOT));
    }

    /** 过程层：默认可治理/融合，不可进目录门户 */
    public static boolean isProcessLayer(String layer) {
        return layer != null && "DWD".equalsIgnoreCase(layer.trim());
    }

    /**
     * 可编目进资源目录：源层 ODS（直通）或资源层 DWS/ADS（加工）；DWD/CONTROL 不可。
     * 空层按表名推断；仍无法判定时默认允许并由调用方补齐 sourcePathType。
     */
    public static boolean isCatalogableLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return true;
        }
        String u = layer.trim().toUpperCase(Locale.ROOT);
        return "ODS".equals(u) || "DWS".equals(u) || "ADS".equals(u) || "SOURCE".equals(u);
    }

    public static String sourcePathTypeForLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return "DIRECT";
        }
        String u = layer.trim().toUpperCase(Locale.ROOT);
        if ("DWS".equals(u) || "ADS".equals(u)) {
            return "PROCESSED";
        }
        return "DIRECT";
    }

    private static String sanitizeIdent(String name) {
        return name == null ? "" : name.replace("`", "");
    }
}
