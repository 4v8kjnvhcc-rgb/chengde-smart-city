package com.chengde.smartcity.exchange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ing_category_resource")
public class IngCategoryResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private Long registryId;
    private String boundBy;
    private LocalDateTime boundAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getRegistryId() { return registryId; }
    public void setRegistryId(Long registryId) { this.registryId = registryId; }
    public String getBoundBy() { return boundBy; }
    public void setBoundBy(String boundBy) { this.boundBy = boundBy; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public void setBoundAt(LocalDateTime boundAt) { this.boundAt = boundAt; }
}
