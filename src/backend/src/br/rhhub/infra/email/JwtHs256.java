package br.rhhub.infra.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assina o JWT HS256 que o webhook do n8n exige (credencial "JWT Auth", Key Type Passphrase).
 * O segredo é usado como texto, do jeito que o n8n usa.
 */
final class JwtHs256 {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;
    private final ObjectMapper mapper;

    JwtHs256(String secret, ObjectMapper mapper) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    /** Token curto: vale só o tempo de uma chamada. */
    String sign(String issuer, Instant now, Duration validFor) {
        var header = new LinkedHashMap<String, Object>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(validFor).getEpochSecond());
        String signingInput = encode(header) + "." + encode(claims);
        return signingInput + "." + B64.encodeToString(hmac(signingInput));
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
