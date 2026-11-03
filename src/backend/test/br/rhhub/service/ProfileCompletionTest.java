package br.rhhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileCompletionTest {

    private Fakes.Users users;
    private UserService service;
    private User google;

    @BeforeEach
    void setUp() {
        users = new Fakes.Users();
        service = new UserService(users, null, new Fakes.Refresh(), new AccessPolicy("root@gmail.com"));
        google = users.save(new User(null, UUID.randomUUID(), "Ana", "ana@gmail.com", null, null, null, null, false,
                Role.USER, AuthProvider.GOOGLE, "g-1", null)).block();
    }

    @Test
    void aGoogleAccountCompletesCpfAgeAndGenderTogether() {
        var done = service.completeProfile(google.publicId(), "52998224725", 30, "female").block();
        assertThat(done.cpf()).isEqualTo("52998224725");
        assertThat(done.age()).isEqualTo(30);
        assertThat(done.gender()).isNotNull();
    }

    @Test
    void allThreeFieldsAreRequired() {
        assertThatThrownBy(() -> service.completeProfile(google.publicId(), null, 30, "female").block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> service.completeProfile(google.publicId(), "52998224725", null, "female").block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> service.completeProfile(google.publicId(), "52998224725", 30, " ").block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> service.completeProfile(google.publicId(), "123", 30, "female").block())
                .isInstanceOf(DomainException.Invalid.class);
    }

    @Test
    void anAlreadySetCpfCannotBeChangedButTheSameOneIsFine() {
        service.completeProfile(google.publicId(), "52998224725", 30, "female").block();
        assertThat(service.completeProfile(google.publicId(), "52998224725", 31, "male").block().age()).isEqualTo(31);
        assertThatThrownBy(() -> service.completeProfile(google.publicId(), "11144477735", 31, "male").block())
                .isInstanceOf(DomainException.Unprocessable.class);
    }
}
