package com.leasingdocument.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtService {

    // Development secret key
    private static final String SECRET_KEY =
            "leasing-document-backend-secret-key-2026";

    // Token valid for 8 hours
    private static final long EXPIRATION_TIME =
            1000L * 60 * 60 * 8;

    private final SecretKey key;


    // =========================================================
    // REVOKED TOKEN STORE
    //
    // token -> expiry timestamp
    // =========================================================

    private final Map<String, Long> revokedTokens =
            new ConcurrentHashMap<>();


    public JwtService() {

        this.key =
                Keys.hmacShaKeyFor(
                        SECRET_KEY.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
    }


    // =========================================================
    // GENERATE JWT TOKEN
    // =========================================================

    public String generateToken(
            Long userId,
            String role
    ) {

        return Jwts.builder()
                .subject(
                        String.valueOf(userId)
                )
                .claim(
                        "role",
                        role
                )
                .issuedAt(
                        new Date()
                )
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + EXPIRATION_TIME
                        )
                )
                .signWith(key)
                .compact();
    }


    // =========================================================
    // GET CLAIMS
    // =========================================================

    private Claims extractClaims(
            String token
    ) {

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    // =========================================================
    // GET USER ID
    // =========================================================

    public String extractUserId(
            String token
    ) {

        return extractClaims(token)
                .getSubject();
    }


    // =========================================================
    // GET ROLE
    // =========================================================

    public String extractRole(
            String token
    ) {

        return extractClaims(token)
                .get(
                        "role",
                        String.class
                );
    }


    // =========================================================
    // REVOKE TOKEN
    // =========================================================

    public void revokeToken(
            String token
    ) {

        try {

            Claims claims =
                    extractClaims(token);

            Date expiration =
                    claims.getExpiration();

            if (expiration != null) {

                revokedTokens.put(
                        token,
                        expiration.getTime()
                );
            }

        } catch (
                JwtException |
                IllegalArgumentException e
        ) {

            // Invalid tokens do not need to be stored.
        }
    }


    // =========================================================
    // CHECK WHETHER TOKEN IS REVOKED
    // =========================================================

    public boolean isTokenRevoked(
            String token
    ) {

        Long expiryTime =
                revokedTokens.get(token);


        // Token is not in blacklist
        if (expiryTime == null) {

            return false;
        }


        // Remove expired blacklist entries
        if (
                expiryTime <
                        System.currentTimeMillis()
        ) {

            revokedTokens.remove(token);

            return false;
        }


        return true;
    }


    // =========================================================
    // CHECK WHETHER TOKEN IS VALID
    // =========================================================

    public boolean isTokenValid(
            String token
    ) {

        try {

            // Logout token must not be accepted
            if (
                    isTokenRevoked(token)
            ) {

                return false;
            }


            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);


            return true;


        } catch (
                JwtException |
                IllegalArgumentException e
        ) {

            return false;
        }
    }
}