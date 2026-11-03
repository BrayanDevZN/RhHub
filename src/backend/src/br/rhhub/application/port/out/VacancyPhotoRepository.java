package br.rhhub.application.port.out;

import br.rhhub.domain.model.ProfilePhoto;
import reactor.core.publisher.Mono;

/** Uma foto por vaga (a mesma regra de tipo e tamanho da foto de perfil). Apagar a vaga apaga a foto. */
public interface VacancyPhotoRepository {

    /** Grava ou troca a foto. Erro NotFound se a vaga não existir. */
    Mono<Void> save(Long vacancyId, ProfilePhoto photo);

    Mono<ProfilePhoto> find(Long vacancyId);

    Mono<Void> delete(Long vacancyId);
}
