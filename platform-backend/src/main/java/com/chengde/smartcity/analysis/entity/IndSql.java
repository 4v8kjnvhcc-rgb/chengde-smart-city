package com.chengde.smartcity.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@TableName("ind_sql")
public class IndSql {
    @TableId(value = "uuid", type = IdType.INPUT)
    private String uuid;
    private String serial;
    private String groupId;
    private String dbMarkId;
    private String dbVersion;
    private String dbName;
    private String content;
    private Integer timeout;
    private String remark;

    @JsonProperty("id")
    public String getId() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    @JsonIgnore
    public String getUuid() { return uuid; }
    public String getSerial() { return serial; }
    public void setSerial(String serial) { this.serial = serial; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getDbMarkId() { return dbMarkId; }
    public void setDbMarkId(String dbMarkId) { this.dbMarkId = dbMarkId; }
    public String getDbVersion() { return dbVersion; }
    public void setDbVersion(String dbVersion) { this.dbVersion = dbVersion; }
    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
