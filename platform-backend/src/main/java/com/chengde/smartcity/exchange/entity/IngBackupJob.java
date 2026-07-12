package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ing_backup_job")
public class IngBackupJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobCode;
    private Long policyId;
    private String scheduleCron;
    private String backupPath;
    private String lastRestorePoint;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public String getBackupPath() { return backupPath; }
    public void setBackupPath(String backupPath) { this.backupPath = backupPath; }
    public String getLastRestorePoint() { return lastRestorePoint; }
    public void setLastRestorePoint(String lastRestorePoint) { this.lastRestorePoint = lastRestorePoint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
