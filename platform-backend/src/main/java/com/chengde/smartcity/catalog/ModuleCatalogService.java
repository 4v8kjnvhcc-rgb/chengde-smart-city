package com.chengde.smartcity.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ModuleCatalogService {

    private final ObjectMapper objectMapper;
    private JsonNode root;
    private List<Map<String, Object>> modules = List.of();
    private Map<String, Map<String, Object>> byCode = Map.of();

    public ModuleCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("catalog/d05-modules.json").getInputStream()) {
            root = objectMapper.readTree(in);
            List<Map<String, Object>> list = new ArrayList<>();
            Map<String, Map<String, Object>> index = new LinkedHashMap<>();
            for (JsonNode n : root.get("modules")) {
                Map<String, Object> m = objectMapper.convertValue(n, Map.class);
                list.add(m);
                index.put((String) m.get("mCode"), m);
            }
            modules = list;
            byCode = index;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load catalog/d05-modules.json", e);
        }
    }

    public Map<String, Object> summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Map<String, Object> m : modules) {
            String s = (String) m.get("implStatus");
            byStatus.merge(s, 1L, Long::sum);
        }
        return Map.of(
                "version", root.get("version").asText(),
                "moduleCount", modules.size(),
                "platforms", objectMapper.convertValue(root.get("platforms"), Map.class),
                "sections", objectMapper.convertValue(root.get("sections"), List.class),
                "statusSummary", byStatus
        );
    }

    public List<Map<String, Object>> list(String platform, String sectionKey, String keyword, String status) {
        return modules.stream()
                .filter(m -> platform == null || platform.isBlank() || platform.equals(m.get("platform")))
                .filter(m -> sectionKey == null || sectionKey.isBlank() || sectionKey.equals(m.get("sectionKey")))
                .filter(m -> status == null || status.isBlank() || status.equals(m.get("implStatus")))
                .filter(m -> matchesKeyword(m, keyword))
                .sorted(Comparator.comparing(m -> (String) m.get("mCode")))
                .toList();
    }

    public Optional<Map<String, Object>> get(String mCode) {
        if (mCode == null) {
            return Optional.empty();
        }
        String key = mCode.toUpperCase();
        if (!key.startsWith("M")) {
            key = "M" + key;
        }
        return Optional.ofNullable(byCode.get(key));
    }

    private boolean matchesKeyword(Map<String, Object> m, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String k = keyword.toLowerCase();
        return contains(m, "mCode", k) || contains(m, "moduleName", k)
                || contains(m, "sectionName", k) || contains(m, "description", k);
    }

    private boolean contains(Map<String, Object> m, String field, String keyword) {
        Object v = m.get(field);
        return v != null && v.toString().toLowerCase().contains(keyword);
    }
}
