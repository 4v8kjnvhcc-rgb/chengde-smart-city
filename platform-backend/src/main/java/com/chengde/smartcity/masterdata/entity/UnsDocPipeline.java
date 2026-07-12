package com.chengde.smartcity.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("uns_doc_pipeline")
public class UnsDocPipeline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String pipelineType;
    private String status;
    private String resultMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getPipelineType() { return pipelineType; }
    public void setPipelineType(String pipelineType) { this.pipelineType = pipelineType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
