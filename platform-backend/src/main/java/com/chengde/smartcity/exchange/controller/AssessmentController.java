package com.chengde.smartcity.exchange.controller;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.exchange.entity.BizEvalDataSource;
import com.chengde.smartcity.exchange.entity.BizEvalExecution;
import com.chengde.smartcity.exchange.entity.BizEvalIndicator;
import com.chengde.smartcity.exchange.entity.BizEvalPeriod;
import com.chengde.smartcity.exchange.service.AssessmentService;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange/assessment")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @GetMapping("/data-sources")
    public ApiResponse<List<BizEvalDataSource>> dataSources() {
        return ApiResponse.ok(service.listDataSources());
    }

    @PostMapping("/data-sources/sync")
    public ApiResponse<List<BizEvalDataSource>> syncDataSources(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(service.syncDataSources(principal));
    }

    @GetMapping("/periods")
    public ApiResponse<List<BizEvalPeriod>> periods() {
        return ApiResponse.ok(service.listPeriods());
    }

    @PostMapping("/periods")
    public ApiResponse<Long> createPeriod(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createPeriod(principal, body));
    }

    @PostMapping("/periods/{id}/activate")
    public ApiResponse<Void> activatePeriod(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.activatePeriod(principal, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/indicators")
    public ApiResponse<List<BizEvalIndicator>> indicators() {
        return ApiResponse.ok(service.listIndicators());
    }

    @PostMapping("/indicators")
    public ApiResponse<Long> createIndicator(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createIndicator(principal, body));
    }

    @GetMapping("/executions")
    public ApiResponse<List<BizEvalExecution>> executions() {
        return ApiResponse.ok(service.listExecutions());
    }

    @GetMapping("/executions/{id}/results")
    public ApiResponse<List<Map<String, Object>>> results(@PathVariable Long id) {
        return ApiResponse.ok(service.listResults(id));
    }

    @PostMapping("/executions/run")
    public ApiResponse<Long> run(@AuthenticationPrincipal UserPrincipal principal,
                                 @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.runEvaluation(principal, body));
    }

    @PostMapping("/executions/{id}/publish")
    public ApiResponse<Void> publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.publishExecution(principal, id);
        return ApiResponse.ok(null);
    }
}
