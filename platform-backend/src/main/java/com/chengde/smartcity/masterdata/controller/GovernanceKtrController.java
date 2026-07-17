package com.chengde.smartcity.masterdata.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.service.KettleTransConverterService;
import com.chengde.smartcity.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/governance/gov-tasks")
public class GovernanceKtrController {

    private final GovGovernanceTaskMapper taskMapper;
    private final KettleTransConverterService converter;

    public GovernanceKtrController(GovGovernanceTaskMapper taskMapper,
                                   KettleTransConverterService converter) {
        this.taskMapper = taskMapper;
        this.converter = converter;
    }

    /** 导出任务画布为 .ktr */
    @GetMapping("/{id}/export-ktr")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportKtr(@PathVariable Long id) {
        GovGovernanceTask task = taskMapper.selectById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        String name = task.getTaskCode() != null ? task.getTaskCode() : ("task_" + id);
        String ktr = converter.graphToKtr(
                task.getGraphJson() != null ? task.getGraphJson() : "{\"nodes\":[],\"edges\":[]}",
                name);
        byte[] bytes = ktr.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".ktr\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(bytes);
    }

    /** 上传 .ktr 并覆盖画布 */
    @PostMapping("/{id}/import-ktr")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> importKtr(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id,
                                                      @RequestParam("file") MultipartFile file) throws Exception {
        GovGovernanceTask task = taskMapper.selectById(id);
        if (task == null) {
            return ApiResponse.fail(404, "任务不存在");
        }
        String xml = new String(file.getBytes(), StandardCharsets.UTF_8);
        String graphJson = converter.ktrToGraph(xml);
        task.setGraphJson(graphJson);
        if ("DRAFT".equals(task.getStatus())) {
            task.setStatus("READY");
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", id);
        m.put("graphJson", graphJson);
        m.put("message", "导入成功");
        return ApiResponse.ok(m);
    }

    /** 切换执行引擎：IN_MEMORY / KETTLE */
    @PutMapping("/{id}/engine")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> updateEngine(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        GovGovernanceTask task = taskMapper.selectById(id);
        if (task == null) {
            return ApiResponse.fail(404, "任务不存在");
        }
        String engine = body.get("engineType") == null ? "KETTLE" : String.valueOf(body.get("engineType")).trim().toUpperCase();
        if (!"IN_MEMORY".equals(engine) && !"KETTLE".equals(engine)) {
            return ApiResponse.fail(400, "engineType 仅支持 IN_MEMORY 或 KETTLE");
        }
        task.setEngineType(engine);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("engineType", engine);
        return ApiResponse.ok(m);
    }
}
