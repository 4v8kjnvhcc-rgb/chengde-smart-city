package com.chengde.smartcity.exchange.service;

import java.util.ArrayList;
import java.util.List;

/** Kettle 一次抽数请求（支持映射别名、WHERE、自定义 SQL、全量/追加）。 */
public class CollectCopyRequest {
    private Long sourceId;
    private Long tableId;
    private IngIngestTaskLedger ledger;
    private String physicalSourceTable;
    private String selectSql;
    private String odsTable;
    private boolean truncate = true;
    private String watermarkAfterSuccess;
    private final List<FieldPair> fields = new ArrayList<>();

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public IngIngestTaskLedger getLedger() { return ledger; }
    public void setLedger(IngIngestTaskLedger ledger) { this.ledger = ledger; }
    public String getPhysicalSourceTable() { return physicalSourceTable; }
    public void setPhysicalSourceTable(String physicalSourceTable) { this.physicalSourceTable = physicalSourceTable; }
    public String getSelectSql() { return selectSql; }
    public void setSelectSql(String selectSql) { this.selectSql = selectSql; }
    public String getOdsTable() { return odsTable; }
    public void setOdsTable(String odsTable) { this.odsTable = odsTable; }
    public boolean isTruncate() { return truncate; }
    public void setTruncate(boolean truncate) { this.truncate = truncate; }
    public String getWatermarkAfterSuccess() { return watermarkAfterSuccess; }
    public void setWatermarkAfterSuccess(String watermarkAfterSuccess) { this.watermarkAfterSuccess = watermarkAfterSuccess; }
    public List<FieldPair> getFields() { return fields; }

    public static class FieldPair {
        private String source;
        private String target;
        private String dataType;
        private Integer length;

        public FieldPair() {}

        public FieldPair(String source, String target, String dataType, Integer length) {
            this.source = source;
            this.target = target;
            this.dataType = dataType;
            this.length = length;
        }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public Integer getLength() { return length; }
        public void setLength(Integer length) { this.length = length; }
    }

    /** 台账任务引用（更新已有 job，而非按 tableId 新建）。 */
    public static class IngIngestTaskLedger {
        private final Long taskId;

        public IngIngestTaskLedger(Long taskId) {
            this.taskId = taskId;
        }

        public Long getTaskId() { return taskId; }
    }
}
