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

    /**
     * Cria o serviço de JWT com o segredo de assinatura.
     *
     * @param secret segredo (JWT_SECRET). Precisa ter pelo menos {@value #MIN_SECRET_LENGTH} caracteres.
     * @param mapper conversor de JSON usado para montar e ler o token.
     * @throws IllegalArgumentException se o segredo for nulo ou curto demais.
     */
    public JwtService(String secret, ObjectMapper mapper) {
        if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("JWT_SECRET precisa ter pelo menos " + MIN_SECRET_LENGTH + " caracteres");
        }
        this.secret = secret.strip().getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    /**
     * Emite um token novo com um identificador (jti) aleatório.
     *
     * @param subject public_id do usuário dono do token.
     * @param type tipo do token (acesso, refresh ou 2FA), que define a validade.
     * @param now instante de emissão; a expiração é calculada a partir dele.
     * @return o JWT assinado, no formato cabeçalho.corpo.assinatura.
     */
    public String issue(UUID subject, TokenType type, Instant now) {
        return issue(subject, type, UUID.randomUUID().toString(), now);
    }

    /**
     * Emite um token com identificador (jti) escolhido por quem chama. É usado no refresh, cujo id é guardado no Redis para poder ser revogado.
     *
     * O corpo leva: sub (usuário), typ (tipo), jti (id), iat (emitido em) e exp (expira em). A assinatura é HMAC-SHA256 do cabeçalho + corpo.
     *
     * @return o JWT assinado.
     */
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

    /**
     * Confere um token e devolve o que ele carrega. As checagens, nesta ordem: formato com 3 partes; algoritmo HS256; assinatura correta (comparação em tempo constante); tipo igual ao esperado; ainda não expirado.
     *
     * @param token o JWT recebido (do cookie).
     * @param expected o tipo que a rota exige (um token de refresh não vale como acesso).
     * @param now instante usado para conferir a expiração.
     * @throws DomainException.Unauthorized (401) "Token inválido" em qualquer defeito, ou "Sessão expirada" se venceu.
     */
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

    /**
     * Cria o erro 401 padrão "Token inválido.". A mensagem é sempre a mesma de propósito: não revela qual checagem falhou.
     */
    private static DomainException.Unauthorized invalid() {
        return new DomainException.Unauthorized("Token inválido.");
    }

    /**
     * Converte um mapa em JSON e codifica em Base64 URL sem preenchimento (o formato de cada parte do JWT).
     */
    private String encode(Map<String, Object> json) {
        try {
            return B64.encodeToString(mapper.writeValueAsBytes(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o JWT", e);
        }
    }

    /**
     * Calcula a assinatura HMAC-SHA256 do texto usando o segredo. É isso que impede alguém de alterar o token sem conhecer o segredo.
     */
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
