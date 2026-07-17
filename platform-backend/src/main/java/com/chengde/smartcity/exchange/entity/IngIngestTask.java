package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_ingest_task")
public class IngIngestTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private String taskName;
    private Long channelId;
    private Long sourceId;
    private Long tableId;
    private String targetTable;
    private Long collectedRows;
    private String kettleTransName;
    private Long dsProjectCode;
    private Long dsDefinitionCode;
    private Long dsInstanceId;
    private Long linesInput;
    private Long linesOutput;
    private Long linesRejected;
    private String errorDetail;
    private String scheduleCron;
    private String status;
    private LocalDateTime lastRunAt;
    private String lastRunMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public Long getCollectedRows() { return collectedRows; }
    public void setCollectedRows(Long collectedRows) { this.collectedRows = collectedRows; }
    public String getKettleTransName() { return kettleTransName; }
    public void setKettleTransName(String kettleTransName) { this.kettleTransName = kettleTransName; }
    public Long getDsProjectCode() { return dsProjectCode; }
    public void setDsProjectCode(Long dsProjectCode) { this.dsProjectCode = dsProjectCode; }
    public Long getDsDefinitionCode() { return dsDefinitionCode; }
    public void setDsDefinitionCode(Long dsDefinitionCode) { this.dsDefinitionCode = dsDefinitionCode; }
    public Long getDsInstanceId() { return dsInstanceId; }
    public void setDsInstanceId(Long dsInstanceId) { this.dsInstanceId = dsInstanceId; }
    public Long getLinesInput() { return linesInput; }
    public void setLinesInput(Long linesInput) { this.linesInput = linesInput; }
    public Long getLinesOutput() { return linesOutput; }
    public void setLinesOutput(Long linesOutput) { this.linesOutput = linesOutput; }
    public Long getLinesRejected() { return linesRejected; }
    public void setLinesRejected(Long linesRejected) { this.linesRejected = linesRejected; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastRunMessage() { return lastRunMessage; }
    public void setLastRunMessage(String lastRunMessage) { this.lastRunMessage = lastRunMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
