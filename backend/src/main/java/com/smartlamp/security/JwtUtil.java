package com.smartlamp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Date;

@Component
public class JwtUtil {

    /** 内置开发默认密钥，仅在 dev profile 下允许使用，生产环境必须显式配置 JWT_SECRET。 */
    private static final String DEV_FALLBACK_SECRET =
            "smart-lamp-dev-only-secret-key-please-change-in-production-2024";

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration; // 默认 24 小时

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /** 启动时校验密钥配置：生产环境没有 JWT_SECRET 直接拒绝启动。 */
    @PostConstruct
    void validateSecret() {
        boolean devProfile = "dev".equalsIgnoreCase(activeProfile)
                || activeProfile == null || activeProfile.isBlank();
        if (secret == null || secret.isBlank()) {
            if (devProfile) {
                secret = DEV_FALLBACK_SECRET;
                return;
            }
            throw new IllegalStateException(
                    "生产环境未配置 JWT_SECRET，拒绝启动。请在环境变量或 application.yml 中设置 jwt.secret（至少 32 字节随机字符串）。");
        }
        // HS256 推荐 32 字节以上
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET 长度不足 32 字节，HS256 不安全，拒绝启动。");
        }
        if (DEV_FALLBACK_SECRET.equals(secret) && !devProfile) {
            throw new IllegalStateException(
                    "检测到使用开发默认 JWT 密钥但处于非 dev profile，拒绝启动。");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从 JWT 根密钥派生用途隔离的 256 位子密钥。调用方只拿到派生结果，
     * 不接触根密钥；不同 context 不能互换，避免审计签名与 JWT 签名共用同一把工作密钥。
     */
    public byte[] deriveSubkey(String context) {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("密钥派生 context 不能为空");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(getSigningKey());
            return mac.doFinal(("smartlamp/subkey/" + context).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("安全子密钥派生失败", e);
        }
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
