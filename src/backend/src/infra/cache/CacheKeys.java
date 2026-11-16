package br.rhhub.infra.cache;

import java.time.Duration;
import java.util.UUID;

/** Chaves e prazos do cache num lugar só. Tudo começa com "cache:". */
public final class CacheKeys {

    /**
     * Classe utilitária: não é criada.
     */
    private CacheKeys() {
    }

    /** Usuário logado e vagas: mudam pouco e são lidos a cada requisição. */
    public static final Duration LONG = Duration.ofMinutes(5);
    /** Avisos do sino: mudam com mais frequência (e a tela consulta a cada 30 s). */
    public static final Duration SHORT = Duration.ofSeconds(60);

    /**
     * Chave do cache da identidade de um usuário logado (nome, id, papel). Ex.: cache:user:identity:<uuid>.
     */
    public static String userIdentity(UUID publicId) {
        return "cache:user:identity:" + publicId;
    }

    public static final String VACANCY_PREFIX = "cache:vacancy:";
    public static final String VACANCY_ALL = VACANCY_PREFIX + "all";

    /**
     * Chave do cache de uma vaga pelo id. Começa com o prefixo das vagas, então apagar o prefixo apaga todas.
     */
    public static String vacancy(Long id) {
        return VACANCY_PREFIX + "id:" + id;
    }

    /**
     * Prefixo de todas as chaves de aviso de um usuário. Apagar esse prefixo limpa a lista e o contador dele de uma vez.
     */
    public static String notificationPrefix(Long userId) {
        return "cache:notif:" + userId + ":";
    }

    /**
     * Chave da lista de avisos de um usuário, para um limite de itens.
     */
    public static String notificationList(Long userId, int limit) {
        return notificationPrefix(userId) + "list:" + limit;
    }

    /**
     * Chave do contador de avisos não lidos de um usuário.
     */
    public static String notificationUnread(Long userId) {
        return notificationPrefix(userId) + "unread";
    }
}
