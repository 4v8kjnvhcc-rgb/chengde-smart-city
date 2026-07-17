package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.service.TaskVariableService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/tasks/{taskId}/variables")
public class TaskVariableController {

    private final TaskVariableService variableService;

    public TaskVariableController(TaskVariableService variableService) {
        this.variableService = variableService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<TaskVariableService.TaskVariable>> getVariables(@PathVariable Long taskId) {
        return ApiResponse.ok(variableService.getTaskVariables(taskId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> saveVariables(@PathVariable Long taskId,
                                                          @RequestBody List<TaskVariableService.TaskVariable> variables) {
        variableService.saveTaskVariables(taskId, variables);
        return ApiResponse.ok(Map.of("saved", variables == null ? 0 : variables.size()));
    }
}
