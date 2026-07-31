package com.chengde.smartcity.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_appearance_config")
public class SysAppearanceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String themeId;
    private String customThemesJson;
    private String logoMode;
    private String logoPath;
    private Integer loginCaptchaEnabled;
    private String loginTitle;
    private Integer loginTitleFontSize;
    private String loginTitleColor;
    private String loginBgMode;
    private String loginMediaPath;
    private String loginMediaType;
    private String browserTitle;
    private String faviconPath;
    private Integer watermarkEnabled;
    private String watermarkText;
    private Integer watermarkShowUsername;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    public String getCustomThemesJson() { return customThemesJson; }
    public void setCustomThemesJson(String customThemesJson) { this.customThemesJson = customThemesJson; }
    public String getLogoMode() { return logoMode; }
    public void setLogoMode(String logoMode) { this.logoMode = logoMode; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    public Integer getLoginCaptchaEnabled() { return loginCaptchaEnabled; }
    public void setLoginCaptchaEnabled(Integer loginCaptchaEnabled) { this.loginCaptchaEnabled = loginCaptchaEnabled; }
    public String getLoginTitle() { return loginTitle; }
    public void setLoginTitle(String loginTitle) { this.loginTitle = loginTitle; }
    public Integer getLoginTitleFontSize() { return loginTitleFontSize; }
    public void setLoginTitleFontSize(Integer loginTitleFontSize) { this.loginTitleFontSize = loginTitleFontSize; }
    public String getLoginTitleColor() { return loginTitleColor; }
    public void setLoginTitleColor(String loginTitleColor) { this.loginTitleColor = loginTitleColor; }
    public String getLoginBgMode() { return loginBgMode; }
    public void setLoginBgMode(String loginBgMode) { this.loginBgMode = loginBgMode; }
    public String getLoginMediaPath() { return loginMediaPath; }
    public void setLoginMediaPath(String loginMediaPath) { this.loginMediaPath = loginMediaPath; }
    public String getLoginMediaType() { return loginMediaType; }
    public void setLoginMediaType(String loginMediaType) { this.loginMediaType = loginMediaType; }
    public String getBrowserTitle() { return browserTitle; }
    public void setBrowserTitle(String browserTitle) { this.browserTitle = browserTitle; }
    public String getFaviconPath() { return faviconPath; }
    public void setFaviconPath(String faviconPath) { this.faviconPath = faviconPath; }
    public Integer getWatermarkEnabled() { return watermarkEnabled; }
    public void setWatermarkEnabled(Integer watermarkEnabled) { this.watermarkEnabled = watermarkEnabled; }
    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText; }
    public Integer getWatermarkShowUsername() { return watermarkShowUsername; }
    public void setWatermarkShowUsername(Integer watermarkShowUsername) { this.watermarkShowUsername = watermarkShowUsername; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
