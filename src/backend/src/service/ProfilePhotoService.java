package br.rhhub.service;

import br.rhhub.application.port.out.ProfilePhotoRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.ProfilePhoto;
import br.rhhub.domain.model.User;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Foto de perfil: o dono troca e remove; o dono e os admins veem. */
public class ProfilePhotoService {

    private final ProfilePhotoRepository photos;
    private final UserRepository users;

    public ProfilePhotoService(ProfilePhotoRepository photos, UserRepository users) {
        this.photos = photos;
        this.users = users;
    }

    /** Confere tamanho e tipo (JPEG, PNG, WebP; até 2 MB) e troca a foto anterior. */
    public Mono<Void> upload(User actor, byte[] content) {
        return Mono.defer(() -> photos.save(actor.id(), ProfilePhoto.of(content)));
    }

    public Mono<Void> remove(User actor) {
        return photos.delete(actor.id());
    }

    public Mono<Boolean> has(User user) {
        return photos.exists(user.id());
    }

    /** targetPublicId nulo significa a própria foto. Ver a de outro usuário é só para admin. */
    public Mono<ProfilePhoto> get(User actor, UUID targetPublicId) {
        if (targetPublicId == null || targetPublicId.equals(actor.publicId())) {
            return photos.find(actor.id()).switchIfEmpty(Mono.error(noPhoto()));
        }
        if (!actor.isAdmin()) {
            return Mono.error(new DomainException.Forbidden("Você não pode ver a foto de outro usuário."));
        }
        return users.findByPublicId(targetPublicId)
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Usuário não encontrado.")))
                .flatMap(target -> photos.find(target.id()))
                .switchIfEmpty(Mono.error(noPhoto()));
    }

    private static DomainException.NotFound noPhoto() {
        return new DomainException.NotFound("Sem foto de perfil.");
    }
}
