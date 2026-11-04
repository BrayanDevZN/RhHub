package br.rhhub.infra.email;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.domain.auth.CodePurpose;
import org.junit.jupiter.api.Test;

@UnitTest
class HtmlEmailComposerTest {

    private final HtmlEmailComposer composer = new HtmlEmailComposer();

    @Test
    void everyPurposeRendersNameAndCodeWithoutLeftovers() {
        for (CodePurpose purpose : CodePurpose.values()) {
            var message = composer.verificationCode(purpose, "ana@gmail.com", "Ana", "042517");
            assertThat(message.type()).isEqualTo(EmailMessage.Type.HTML);
            assertThat(message.to()).isEqualTo("ana@gmail.com");
            assertThat(message.subject()).isNotBlank();
            assertThat(message.content()).contains(">Ana<").contains("042517").doesNotContain("{{").doesNotContain("{}");
        }
    }

    @Test
    void nameIsEscapedSoItCannotInjectHtml() {
        var message = composer.verificationCode(CodePurpose.SIGNUP, "x@gmail.com", "<script>alert(1)</script>", "123456");
        assertThat(message.content()).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
