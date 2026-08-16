package com.chengde.smartcity.masterdata.support;

import java.util.List;
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

    public static final String CONTROL_BAK = "smart_city_bak";
    public static final String ODS_BAK = "smart_city_ods_bak";
    public static final String DWD_BAK = "smart_city_dwd_bak";
    public static final String DWS_BAK = "smart_city_dws_bak";
    public static final String ADS_BAK = "smart_city_ads_bak";

    private static final Set<String> PLATFORM_DBS = Set.of(CONTROL, ODS, DWD, DWS, ADS);
    private static final Set<String> BACKUP_DBS = Set.of(CONTROL_BAK, ODS_BAK, DWD_BAK, DWS_BAK, ADS_BAK);

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

    public static boolean isBackupDatabase(String db) {
        if (db == null || db.isBlank()) {
            return false;
        }
        return BACKUP_DBS.contains(db.trim().toLowerCase(Locale.ROOT));
    }

    /** 源库 → 同机备份库名（smart_city_ods → smart_city_ods_bak） */
    public static String backupDatabaseFor(String sourceDb) {
        String src = sourceDatabaseOf(sourceDb);
        if (!isPlatformLayerDb(src)) {
            throw new IllegalArgumentException("不支持的源库: " + sourceDb);
        }
        return src + "_bak";
    }

    /** 备份库或源库 → 源库名 */
    public static String sourceDatabaseOf(String dbOrBak) {
        if (dbOrBak == null || dbOrBak.isBlank()) {
            return ODS;
        }
        String d = dbOrBak.trim().toLowerCase(Locale.ROOT);
        if (isBackupDatabase(d) && d.endsWith("_bak")) {
            return d.substring(0, d.length() - 4);
        }
        if (isPlatformLayerDb(d)) {
            return d;
        }
        return d;
    }

    public static List<String> platformSourceDatabases() {
        return List.of(CONTROL, ODS, DWD, DWS, ADS);
    }

    /** 控制面库（smart_city）：平台自身表，不作为数据资产目录内容 */
    public static boolean isControlDatabase(String db) {
        return db != null && CONTROL.equalsIgnoreCase(db.trim());
    }

    /** 控制面分层：CONTROL 或库名为 smart_city */
    public static boolean isControlLayer(String layer) {
        return layer != null && "CONTROL".equalsIgnoreCase(layer.trim());
    }

    /** 数据面分层（ODS/DWD/DWS/ADS），不含控制面 */
    public static boolean isDataPlaneLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return false;
        }
        String u = layer.trim().toUpperCase(Locale.ROOT);
        return "ODS".equals(u) || "DWD".equals(u) || "DWS".equals(u) || "ADS".equals(u);
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
