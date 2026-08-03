package com.chengde.smartcity.security;

import java.util.Collection;
import java.util.List;

public class UserPrincipal {

    private final Long userId;
    private final String username;
    private final Long orgId;
    private final String displayName;
    private final List<String> roleCodes;
    private final Collection<String> permissions;

    public UserPrincipal(Long userId, String username, Long orgId, String displayName,
                         List<String> roleCodes, Collection<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.orgId = orgId;
        this.displayName = displayName;
        this.roleCodes = roleCodes;
        this.permissions = permissions;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Long getOrgId() { return orgId; }
    public String getDisplayName() { return displayName; }
    public List<String> getRoleCodes() { return roleCodes; }
    public Collection<String> getPermissions() { return permissions; }

    public boolean isSystemAdmin() {
        return roleCodes.contains("SYSTEM_ADMIN");
    }

    public boolean isDeptAdmin() {
        return roleCodes.contains("DEPT_ADMIN");
    }

    public boolean isPlatformAdmin() {
        return roleCodes.contains("PLATFORM_ADMIN");
    }
}
