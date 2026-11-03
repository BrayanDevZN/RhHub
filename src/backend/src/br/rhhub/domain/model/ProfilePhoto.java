package br.rhhub.domain.model;

import br.rhhub.domain.exception.DomainException;

/** Foto de perfil: o tipo vem do conteúdo (assinatura do arquivo), nunca do que o cliente declarou. */
public record ProfilePhoto(byte[] content, String contentType) {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    /** Confere tamanho e assinatura (JPEG, PNG ou WebP). */
    public static ProfilePhoto of(byte[] content) {
        if (content == null || content.length == 0) {
            throw new DomainException.Invalid("Envie uma imagem.");
        }
        if (content.length > MAX_BYTES) {
            throw new DomainException.Invalid("A foto pode ter no máximo 2 MB.");
        }
        return new ProfilePhoto(content, detect(content));
    }

    private static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new DomainException.Invalid("A foto precisa ser JPEG, PNG ou WebP.");
    }
}
