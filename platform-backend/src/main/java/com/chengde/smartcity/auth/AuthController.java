package com.chengde.smartcity.auth;

import com.chengde.smartcity.auth.dto.ChangePasswordRequest;
import com.chengde.smartcity.auth.dto.EncryptedTransportRequest;
import com.chengde.smartcity.auth.dto.EncryptedTransportResponse;
import com.chengde.smartcity.auth.dto.LoginRequest;
import com.chengde.smartcity.auth.dto.RefreshRequest;
import com.chengde.smartcity.auth.dto.SsoTicketRequest;
import com.chengde.smartcity.auth.dto.SsoTicketResponse;
import com.chengde.smartcity.auth.dto.TokenResponse;
import com.chengde.smartcity.common.api.ApiResponse;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.CaptchaService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final SsoTicketService ssoTicketService;
    private final TransportCryptoService transportCryptoService;

    public AuthController(AuthService authService, CaptchaService captchaService, SsoTicketService ssoTicketService,
                          TransportCryptoService transportCryptoService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.ssoTicketService = ssoTicketService;
        this.transportCryptoService = transportCryptoService;
    }

    @GetMapping("/crypto/public-key")
    public ApiResponse<Map<String, Object>> publicKey() {
        return ApiResponse.ok(transportCryptoService.publicKeyInfo());
    }

    @PostMapping("/login")
    public ApiResponse<EncryptedTransportResponse> login(@Valid @RequestBody EncryptedTransportRequest request,
                                                         HttpServletRequest httpRequest) {
        TransportCryptoService.OpenedEnvelope opened = transportCryptoService.openEnvelope(request);
        LoginRequest login = new LoginRequest(
                transportCryptoService.requireText(opened.payload(), "username"),
                transportCryptoService.requireText(opened.payload(), "password"),
                request.totpCode(),
                request.captchaId(),
                request.captchaCode()
        );
        TokenResponse tokens = authService.login(login, httpRequest);
        return ApiResponse.ok(transportCryptoService.encryptForClient(opened.aesKey(), tokens));
    }

    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        return ApiResponse.ok(captchaService.create());
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

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody EncryptedTransportRequest request) {
        JsonNode plain = transportCryptoService.decryptAndVerify(request);
        ChangePasswordRequest change = new ChangePasswordRequest(
                transportCryptoService.requireText(plain, "oldPassword"),
                transportCryptoService.requireText(plain, "newPassword")
        );
        authService.changePassword(principal, change);
        return ApiResponse.ok(null);
    }

    /** 签发短期一次性门户票据，供考核等外系统验票换本系统登录态 */
    @PostMapping("/sso-ticket")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SsoTicketResponse> issueSsoTicket(@AuthenticationPrincipal UserPrincipal principal,
                                                         @Valid @RequestBody SsoTicketRequest request) {
        return ApiResponse.ok(ssoTicketService.issue(principal, request));
    }
}
