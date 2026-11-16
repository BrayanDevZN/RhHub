package br.rhhub.application.port.out;

import br.rhhub.domain.model.ProfilePhoto;
import reactor.core.publisher.Mono;

/** Uma foto por usuário. Apagar o usuário apaga a foto. */
public interface ProfilePhotoRepository {

    /** Grava ou troca a foto. */
    Mono<Void> save(Long userId, ProfilePhoto photo);

    Mono<ProfilePhoto> find(Long userId);

    Mono<Boolean> exists(Long userId);

    Mono<Void> delete(Long userId);
}
