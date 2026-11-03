package br.rhhub.domain.auth;

import br.rhhub.domain.exception.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Emite e confere os JWT de sessão (HS256). Só aceita o algoritmo HS256, confere a assinatura em tempo constante,
 * a expiração e o tipo esperado. Qualquer defeito vira {@link DomainException.Unauthorized} (401).
 */
public final class JwtService {

    public static final int MIN_SECRET_LENGTH = 32;

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final ObjectMapper mapper;

    public JwtService(String secret, ObjectMapper mapper) {
        if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("JWT_SECRET precisa ter pelo menos " + MIN_SECRET_LENGTH + " caracteres");
        }
        this.secret = secret.strip().getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    public String issue(UUID subject, TokenType type, Instant now) {
        return issue(subject, type, UUID.randomUUID().toString(), now);
    }

    public String issue(UUID subject, TokenType type, String id, Instant now) {
        var header = new LinkedHashMap<String, Object>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        var claims = new LinkedHashMap<String, Object>();
        claims.put("sub", subject.toString());
        claims.put("typ", type.claim());
        claims.put("jti", id);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(type.lifetime()).getEpochSecond());
        String input = encode(header) + "." + encode(claims);
        return input + "." + B64.encodeToString(hmac(input));
    }

    public TokenClaims verify(String token, TokenType expected, Instant now) {
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        String[] parts = token.strip().split("\\.", -1);
        if (parts.length != 3) {
            throw invalid();
        }
        try {
            JsonNode header = mapper.readTree(B64_DECODER.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) {
                throw invalid();
            }
            byte[] signature = B64_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(signature, hmac(parts[0] + "." + parts[1]))) {
                throw invalid();
            }
            JsonNode body = mapper.readTree(B64_DECODER.decode(parts[1]));
            TokenType type = TokenType.fromClaim(body.path("typ").asText());
            if (type != expected) {
                throw invalid();
            }
            Instant expiresAt = Instant.ofEpochSecond(body.path("exp").asLong(0));
            if (!expiresAt.isAfter(now)) {
                throw new DomainException.Unauthorized("Sessão expirada.");
            }
            return new TokenClaims(
                    UUID.fromString(body.path("sub").asText()),
                    type,
                    body.path("jti").asText(),
                    Instant.ofEpochSecond(body.path("iat").asLong(0)),
                    expiresAt);
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static DomainException.Unauthorized invalid() {
        return new DomainException.Unauthorized("Token inválido.");
    }

    private String encode(Map<String, Object> json) {
        try {
            return B64.encodeToString(mapper.writeValueAsBytes(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o JWT", e);
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }
}
