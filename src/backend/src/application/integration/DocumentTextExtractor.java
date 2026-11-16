package br.rhhub.application.port.out;

import br.rhhub.domain.model.DocumentType;
import java.util.Optional;

/** Porta de saída: descobre se o arquivo é PDF ou DOCX e extrai o texto do currículo. */
public interface DocumentTextExtractor {

    /** Identifica o tipo pelo conteúdo (assinatura), não pelo cabeçalho enviado. */
    Optional<DocumentType> detect(byte[] content);

    /** Lança DomainException.Unprocessable se o arquivo estiver corrompido. */
    String extract(byte[] content, DocumentType type);
}
