package com.chengde.smartcity.auth;

import com.chengde.smartcity.auth.dto.LoginRequest;
import com.chengde.smartcity.auth.dto.RefreshRequest;
import com.chengde.smartcity.auth.dto.TokenResponse;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (principal != null && authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(principal, authHeader.substring(7));
        }
        return ApiResponse.ok(null);
    }
}
