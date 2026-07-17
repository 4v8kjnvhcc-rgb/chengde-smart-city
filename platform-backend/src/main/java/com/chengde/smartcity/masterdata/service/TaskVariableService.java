package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务变量管理服务
 * 支持Kettle变量的定义、保存和运行时注入
 */
@Service
public class TaskVariableService {

    private static final ObjectMapper OM = new ObjectMapper();

    private final GovGovernanceTaskMapper taskMapper;

    public TaskVariableService(GovGovernanceTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 获取任务的变量列表
     */
    public List<TaskVariable> getTaskVariables(Long taskId) {
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return new ArrayList<>();
        }
        return parseVariables(task.getVariablesJson());
    }

    /**
     * 保存任务变量
     */
    @Transactional
    public void saveTaskVariables(Long taskId, List<TaskVariable> variables) {
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        String json = serializeVariables(variables);
        task.setVariablesJson(json);
        taskMapper.updateById(task);
    }

    /**
     * 将变量列表转换为Kettle参数Map
     */
    public Map<String, String> getVariableParams(Long taskId, Map<String, String> runtimeValues) {
        List<TaskVariable> variables = getTaskVariables(taskId);
        Map<String, String> params = new java.util.HashMap<>();

        for (TaskVariable var : variables) {
            String value = runtimeValues != null && runtimeValues.containsKey(var.name)
                ? runtimeValues.get(var.name)
                : var.defaultValue;
            if (value != null) {
                params.put(var.name, value);
            }
        }

        return params;
    }

    /**
     * 解析变量JSON
     */
    private List<TaskVariable> parseVariables(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OM.readValue(json, new TypeReference<List<TaskVariable>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 序列化变量为JSON
     */
    private String serializeVariables(List<TaskVariable> variables) {
        try {
            return OM.writeValueAsString(variables);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 任务变量数据结构
     */
    public static class TaskVariable {
        public String name;
        public String label;
        public String type; // STRING, NUMBER, DATE, BOOLEAN
        public String defaultValue;
        public String description;
        public boolean required;

        public TaskVariable() {}

        public TaskVariable(String name, String label, String type, String defaultValue) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.defaultValue = defaultValue;
        }
    }
}
