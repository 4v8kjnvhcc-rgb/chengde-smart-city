package com.chengde.smartcity.integration.dataease;

import com.chengde.smartcity.integration.config.IntegrationConfig;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DataEaseClient {

    private final IntegrationProperties props;
    private final RestTemplate rest;

    public DataEaseClient(IntegrationProperties props, RestTemplate integrationRestTemplate) {
        this.props = props;
        this.rest = integrationRestTemplate;
    }

    public boolean isHealthy() {
        if (!props.isEnabled()) {
            return false;
        }
        try {
            rest.getForEntity(props.getDe().getUrl(), String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 组装 DataEase iframe 地址。
     * <p>社区版不支持用 iframe 嵌「公共分享」({@code /#/de-link/...})，应使用仪表板资源 ID：
     * {@code /#/preview?dvId=...&ignoreParams=true}（在 DataEase 预览页地址栏可复制）。
     * 也支持直接填完整 URL 或 {@code #/} 路径。
     */
    public String buildEmbedUrl(String targetId, String token, Long userId) {
        String base = trimTrailingSlash(props.getDe().getEmbedBase());
        String id = targetId == null ? "" : targetId.trim();
        if (id.isEmpty()) {
            return base;
        }
        if (id.startsWith("http://") || id.startsWith("https://")) {
            String extracted = extractDvIdFromUrl(id);
            if (extracted != null) {
                return previewUrl(base, extracted);
            }
            return id;
        }
        if (id.startsWith("#/")) {
            return base + "/" + id;
        }
        if (id.startsWith("/#/")) {
            return base + id.substring(1);
        }
        String fromQuery = extractDvIdFromUrl(id);
        if (fromQuery != null) {
            return previewUrl(base, fromQuery);
        }
        // 仪表板数字资源 ID（预览地址栏 dvId）— 社区版可 iframe
        if (id.matches("^\\d{10,}$")) {
            return previewUrl(base, id);
        }
        // 公共分享码：社区版 iframe 会提示仅嵌入式/企业版支持，仍拼 de-link 供新窗口打开
        if (looksLikePublicShareCode(id)) {
            return base + "/#/de-link/" + id;
        }
        return previewUrl(base, id);
    }

    private static String previewUrl(String base, String dvId) {
        return base + "/#/preview?dvId=" + dvId + "&ignoreParams=true";
    }

    private static String extractDvIdFromUrl(String raw) {
        int idx = raw.indexOf("dvId=");
        if (idx < 0) {
            return null;
        }
        String rest = raw.substring(idx + 5);
        int amp = rest.indexOf('&');
        int hash = rest.indexOf('#');
        int end = rest.length();
        if (amp >= 0) {
            end = Math.min(end, amp);
        }
        if (hash >= 0) {
            end = Math.min(end, hash);
        }
        String v = rest.substring(0, end).trim();
        return v.isEmpty() ? null : v;
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "http://localhost:8100";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** DataEase 公共链接后缀多为短字母数字串，如 SEMnG63X（含字母，非纯数字） */
    private static boolean looksLikePublicShareCode(String id) {
        return id.matches("^(?=.*[A-Za-z])[A-Za-z0-9_-]{6,32}$");
    }

    public Map<String, Object> buildEmbed(String targetType, String targetId, Long userId) {
        IntegrationConfig.requireIntegration(props, "DataEase");
        String token = "DE_" + UUID.randomUUID().toString().replace("-", "");
        return Map.of(
                "token", token,
                "embedUrl", "/analytics/embed-preview?targetType=" + targetType + "&targetId=" + targetId + "&token=" + token,
                "dataeaseUrl", buildEmbedUrl(targetId, token, userId),
                "targetType", targetType,
                "targetId", targetId,
                "source", "dataease-live"
        );
    }
}
