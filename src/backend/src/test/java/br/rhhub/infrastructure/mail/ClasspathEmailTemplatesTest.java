package br.rhhub.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.application.port.out.EmailTemplate;
import org.junit.jupiter.api.Test;

class ClasspathEmailTemplatesTest {

    private final ClasspathEmailTemplates templates = new ClasspathEmailTemplates();

    @Test
    void fillsPlaceholdersInOrderAndEscapesHtml() {
        String html = templates.render(EmailTemplate.CREATE_ACCOUNT, "Ana <b>", "123456");
        assertThat(html).contains("Ana &lt;b&gt;").contains("123456").doesNotContain("{}");
    }

    @Test
    void everyTemplateLoadsWithTwoPlaceholders() {
        for (EmailTemplate t : EmailTemplate.values()) {
            assertThat(templates.render(t, "a", "b")).doesNotContain("{}");
        }
    }

    @Test
    void wrongNumberOfValuesFails() {
        assertThatThrownBy(() -> templates.render(EmailTemplate.TWO_FACTOR, "só um"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> templates.render(EmailTemplate.TWO_FACTOR, "a", "b", "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
