package br.rhhub.infra.cache;

import java.time.Duration;
import java.util.UUID;

/** Chaves e prazos do cache num lugar só. Tudo começa com "cache:". */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** Usuário logado e vagas: mudam pouco e são lidos a cada requisição. */
    public static final Duration LONG = Duration.ofMinutes(5);
    /** Avisos do sino: mudam com mais frequência (e a tela consulta a cada 30 s). */
    public static final Duration SHORT = Duration.ofSeconds(60);

    public static String userIdentity(UUID publicId) {
        return "cache:user:identity:" + publicId;
    }

    public static final String VACANCY_PREFIX = "cache:vacancy:";
    public static final String VACANCY_ALL = VACANCY_PREFIX + "all";

    public static String vacancy(Long id) {
        return VACANCY_PREFIX + "id:" + id;
    }

    public static String notificationPrefix(Long userId) {
        return "cache:notif:" + userId + ":";
    }

    public static String notificationList(Long userId, int limit) {
        return notificationPrefix(userId) + "list:" + limit;
    }

    public static String notificationUnread(Long userId) {
        return notificationPrefix(userId) + "unread";
    }
}
