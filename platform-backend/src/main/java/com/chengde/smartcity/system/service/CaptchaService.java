package com.chengde.smartcity.system.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.SessionRedisService;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * 登录验证码：优先 Redis；不可用时退化为进程内 Map（单机开发可用）。
 */
@Service
public class CaptchaService {

    private final SessionRedisService sessionRedisService;
    private final ConcurrentHashMap<String, String> localStore = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public CaptchaService(SessionRedisService sessionRedisService) {
        this.sessionRedisService = sessionRedisService;
    }

    public Map<String, String> create() {
        String id = UUID.randomUUID().toString().replace("-", "");
        String code = String.format("%04d", random.nextInt(10000));
        try {
            sessionRedisService.putTemp("captcha:" + id, code, 300);
        } catch (Exception e) {
            localStore.put(id, code);
        }
        Map<String, String> out = new HashMap<>();
        out.put("captchaId", id);
        out.put("imageBase64", "data:image/png;base64," + renderBase64(code));
        return out;
    }

    public void verifyOrThrow(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(400, "请填写验证码");
        }
        String expect = null;
        try {
            expect = sessionRedisService.getTemp("captcha:" + captchaId);
            sessionRedisService.deleteTemp("captcha:" + captchaId);
        } catch (Exception ignored) {
            // fall through
        }
        if (expect == null) {
            expect = localStore.remove(captchaId);
        }
        if (expect == null || !expect.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
    }

    private String renderBase64(String code) {
        try {
            BufferedImage img = new BufferedImage(100, 36, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(240, 244, 250));
            g.fillRect(0, 0, 100, 36);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.setColor(new Color(22, 119, 255));
            g.drawString(code, 22, 25);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }
}
