package com.chengde.smartcity.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysAppearanceConfig;
import com.chengde.smartcity.system.mapper.SysAppearanceConfigMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AppearanceService {

    private static final Logger log = LoggerFactory.getLogger(AppearanceService.class);
    private static final long ROW_ID = 1L;

    private final SysAppearanceConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final SecurityConfigService securityConfigService;
    private final Path storageRoot;

    public AppearanceService(SysAppearanceConfigMapper mapper, ObjectMapper objectMapper,
                             SecurityConfigService securityConfigService,
                             @Value("${app.data-dir:./data}") String dataDir) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.securityConfigService = securityConfigService;
        this.storageRoot = Paths.get(dataDir, "appearance").toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            log.warn("创建外观资源目录失败: {}", e.getMessage());
        }
    }

    public SysAppearanceConfig requireRow() {
        SysAppearanceConfig row = mapper.selectById(ROW_ID);
        if (row == null) {
            row = new SysAppearanceConfig();
            row.setId(ROW_ID);
            row.setThemeId("builtin-blue");
            row.setLogoMode("CUSTOM");
            row.setLoginCaptchaEnabled(0);
            row.setLoginTitle("承德高新区智慧城市基础平台");
            row.setLoginTitleFontSize(28);
            row.setLoginTitleColor("#ffffff");
            row.setLoginBgMode("DEFAULT");
            row.setWatermarkEnabled(0);
            row.setWatermarkShowUsername(0);
            row.setCreatedAt(LocalDateTime.now());
            mapper.insert(row);
        }
        return row;
    }

    public Map<String, Object> toPublicMap(SysAppearanceConfig c) {
        Map<String, Object> m = new HashMap<>();
        m.put("themeId", c.getThemeId());
        m.put("logoMode", c.getLogoMode());
        m.put("logoUrl", publicUrl(c.getLogoPath()));
        m.put("loginCaptchaEnabled", c.getLoginCaptchaEnabled() != null && c.getLoginCaptchaEnabled() == 1);
        m.put("loginTitle", c.getLoginTitle());
        m.put("loginTitleFontSize", c.getLoginTitleFontSize());
        m.put("loginTitleColor", c.getLoginTitleColor());
        m.put("loginBgMode", c.getLoginBgMode());
        m.put("loginMediaUrl", publicUrl(c.getLoginMediaPath()));
        m.put("loginMediaType", c.getLoginMediaType());
        m.put("browserTitle", c.getBrowserTitle());
        m.put("faviconUrl", publicUrl(c.getFaviconPath()));
        m.put("watermarkEnabled", c.getWatermarkEnabled() != null && c.getWatermarkEnabled() == 1);
        m.put("watermarkText", c.getWatermarkText());
        m.put("watermarkShowUsername", c.getWatermarkShowUsername() != null && c.getWatermarkShowUsername() == 1);
        m.put("themes", listThemes(c));
        Map<String, Object> authMethods = new HashMap<>();
        authMethods.put("password", securityConfigService.isAuthMethodEnabled("password", true));
        authMethods.put("sms", securityConfigService.isAuthMethodEnabled("sms", false));
        authMethods.put("totp", securityConfigService.isAuthMethodEnabled("totp", false));
        authMethods.put("fingerprint", securityConfigService.isAuthMethodEnabled("fingerprint", false));
        authMethods.put("twoFactorRequired", securityConfigService.isSecondFactorRequired()
                || "true".equalsIgnoreCase(securityConfigService.get("two_factor_enabled", "false")));
        m.put("authMethods", authMethods);
        return m;
    }

    public Map<String, Object> toAdminMap(SysAppearanceConfig c) {
        Map<String, Object> m = toPublicMap(c);
        m.put("logoPath", c.getLogoPath());
        m.put("loginMediaPath", c.getLoginMediaPath());
        m.put("faviconPath", c.getFaviconPath());
        m.put("customThemesJson", c.getCustomThemesJson());
        return m;
    }

    public List<Map<String, Object>> listThemes(SysAppearanceConfig c) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(builtin("builtin-blue", "内置主题·蓝", "#1677ff", "#001529"));
        list.add(builtin("builtin-teal", "内置主题·青", "#13c2c2", "#002329"));
        try {
            if (c.getCustomThemesJson() != null && !c.getCustomThemesJson().isBlank()) {
                List<Map<String, Object>> customs = objectMapper.readValue(
                        c.getCustomThemesJson(), new TypeReference<List<Map<String, Object>>>() {});
                if (customs != null) {
                    for (Map<String, Object> t : customs) {
                        t.put("builtin", false);
                        list.add(t);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析自定义主题失败: {}", e.getMessage());
        }
        return list;
    }

    private Map<String, Object> builtin(String id, String name, String primary, String sidebar) {
        Map<String, Object> t = new HashMap<>();
        t.put("id", id);
        t.put("name", name);
        t.put("primaryColor", primary);
        t.put("sidebarBg", sidebar);
        t.put("builtin", true);
        return t;
    }

    public void save(UserPrincipal operator, Map<String, Object> body) {
        SysAppearanceConfig c = requireRow();
        if (body.containsKey("themeId")) {
            c.setThemeId(str(body.get("themeId"), c.getThemeId()));
        }
        if (body.containsKey("logoMode")) {
            c.setLogoMode(str(body.get("logoMode"), c.getLogoMode()));
        }
        if (body.containsKey("loginCaptchaEnabled")) {
            c.setLoginCaptchaEnabled(bool01(body.get("loginCaptchaEnabled")));
        }
        if (body.containsKey("loginTitle")) {
            c.setLoginTitle(str(body.get("loginTitle"), c.getLoginTitle()));
        }
        if (body.containsKey("loginTitleFontSize")) {
            c.setLoginTitleFontSize(intVal(body.get("loginTitleFontSize"), c.getLoginTitleFontSize()));
        }
        if (body.containsKey("loginTitleColor")) {
            c.setLoginTitleColor(str(body.get("loginTitleColor"), c.getLoginTitleColor()));
        }
        if (body.containsKey("loginBgMode")) {
            c.setLoginBgMode(str(body.get("loginBgMode"), c.getLoginBgMode()));
        }
        if (body.containsKey("loginMediaType")) {
            c.setLoginMediaType(str(body.get("loginMediaType"), c.getLoginMediaType()));
        }
        if (body.containsKey("browserTitle")) {
            Object v = body.get("browserTitle");
            c.setBrowserTitle(v == null ? null : String.valueOf(v));
        }
        if (body.containsKey("watermarkEnabled")) {
            c.setWatermarkEnabled(bool01(body.get("watermarkEnabled")));
        }
        if (body.containsKey("watermarkText")) {
            Object v = body.get("watermarkText");
            c.setWatermarkText(v == null ? null : String.valueOf(v));
        }
        if (body.containsKey("watermarkShowUsername")) {
            c.setWatermarkShowUsername(bool01(body.get("watermarkShowUsername")));
        }
        c.setUpdatedBy(operator != null ? operator.getUserId() : null);
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
    }

    public Map<String, Object> uploadAsset(UserPrincipal operator, String kind, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase();
        }
        String relative = kind + "/" + UUID.randomUUID() + ext;
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(400, "非法路径");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());

        SysAppearanceConfig c = requireRow();
        switch (kind) {
            case "logo" -> c.setLogoPath(relative);
            case "login-media" -> {
                c.setLoginMediaPath(relative);
                if (ext.matches("\\.(mp4|webm|ogg)")) {
                    c.setLoginMediaType("VIDEO");
                } else {
                    c.setLoginMediaType("IMAGE");
                }
            }
            case "favicon" -> c.setFaviconPath(relative);
            default -> throw new BusinessException(400, "不支持的上传类型: " + kind);
        }
        c.setUpdatedBy(operator.getUserId());
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
        Map<String, Object> out = new HashMap<>();
        out.put("path", relative);
        out.put("url", publicUrl(relative));
        return out;
    }

    public Map<String, Object> uploadCustomTheme(UserPrincipal operator, String name, String primaryColor,
                                                 String sidebarBg, MultipartFile file) throws IOException {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "请填写主题名称");
        }
        String id = "custom-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String filePath = null;
        if (file != null && !file.isEmpty()) {
            String relative = "theme/" + id + "-" + safeName(file.getOriginalFilename());
            Path target = storageRoot.resolve(relative).normalize();
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
            filePath = relative;
        }
        SysAppearanceConfig c = requireRow();
        List<Map<String, Object>> customs = new ArrayList<>();
        if (c.getCustomThemesJson() != null && !c.getCustomThemesJson().isBlank()) {
            customs = objectMapper.readValue(c.getCustomThemesJson(), new TypeReference<List<Map<String, Object>>>() {});
        }
        Map<String, Object> theme = new HashMap<>();
        theme.put("id", id);
        theme.put("name", name.trim());
        theme.put("primaryColor", primaryColor == null || primaryColor.isBlank() ? "#1677ff" : primaryColor);
        theme.put("sidebarBg", sidebarBg == null || sidebarBg.isBlank() ? "#001529" : sidebarBg);
        theme.put("filePath", filePath);
        theme.put("builtin", false);
        customs.add(theme);
        c.setCustomThemesJson(objectMapper.writeValueAsString(customs));
        c.setUpdatedBy(operator.getUserId());
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
        return theme;
    }

    public void deleteCustomTheme(UserPrincipal operator, String themeId) {
        if (themeId == null || themeId.startsWith("builtin-")) {
            throw new BusinessException(400, "内置主题不可删除");
        }
        SysAppearanceConfig c = requireRow();
        try {
            List<Map<String, Object>> customs = new ArrayList<>();
            if (c.getCustomThemesJson() != null && !c.getCustomThemesJson().isBlank()) {
                customs = objectMapper.readValue(c.getCustomThemesJson(), new TypeReference<List<Map<String, Object>>>() {});
            }
            boolean removed = customs.removeIf(t -> themeId.equals(String.valueOf(t.get("id"))));
            if (!removed) {
                throw new BusinessException(404, "主题不存在");
            }
            c.setCustomThemesJson(objectMapper.writeValueAsString(customs));
            if (themeId.equals(c.getThemeId())) {
                c.setThemeId("builtin-blue");
            }
            c.setUpdatedBy(operator.getUserId());
            c.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(c);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "删除主题失败: " + e.getMessage());
        }
    }

    public byte[] downloadBuiltinTheme(String id) {
        Map<String, Object> theme = listThemes(requireRow()).stream()
                .filter(t -> id.equals(t.get("id")) && Boolean.TRUE.equals(t.get("builtin")))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "内置主题不存在"));
        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(theme);
        } catch (Exception e) {
            throw new BusinessException(500, "导出失败");
        }
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public Resource resolveFile(String relative) {
        if (relative == null || relative.isBlank() || relative.contains("..")) {
            throw new BusinessException(404, "资源不存在");
        }
        Path p = storageRoot.resolve(relative).normalize();
        if (!p.startsWith(storageRoot) || !Files.isRegularFile(p)) {
            throw new BusinessException(404, "资源不存在");
        }
        return new FileSystemResource(p);
    }

    private String publicUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return "/api/v1/system/appearance/files/" + path.replace("\\", "/");
    }

    private static String str(Object o, String def) {
        if (o == null) {
            return def;
        }
        String s = String.valueOf(o);
        return s.isBlank() ? def : s;
    }

    private static int intVal(Object o, Integer def) {
        if (o == null) {
            return def == null ? 0 : def;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def == null ? 0 : def;
        }
    }

    private static int bool01(Object o) {
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0 ? 1 : 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o)) ? 1 : 0;
    }

    private static String safeName(String name) {
        if (name == null) {
            return "theme.json";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
