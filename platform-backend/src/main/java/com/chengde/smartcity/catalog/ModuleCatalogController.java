package com.chengde.smartcity.catalog;

import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class ModuleCatalogController {

    private final ModuleCatalogService catalogService;

    public ModuleCatalogController(ModuleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(catalogService.summary());
    }

    @GetMapping("/modules")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String sectionKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(catalogService.list(platform, sectionKey, keyword, status));
    }

    @GetMapping("/modules/{mCode}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String mCode) {
        return catalogService.get(mCode)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new BusinessException(404, "模块不存在: " + mCode));
    }
}
