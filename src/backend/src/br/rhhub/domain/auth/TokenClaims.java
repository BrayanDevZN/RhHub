package br.rhhub.domain.auth;

import java.time.Instant;
import java.util.UUID;

/** O que um token válido carrega. subject é o public_id do usuário; id identifica o token (revogação do refresh). */
public record TokenClaims(UUID subject, TokenType type, String id, Instant issuedAt, Instant expiresAt) {
}
