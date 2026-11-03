package br.rhhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AnalysisResult;
import br.rhhub.domain.model.ResumeStatus;
import org.junit.jupiter.api.Test;

class AnalysisResultTest {

    private static final String HTML = "<!DOCTYPE html><html><head><title>T</title><style>p{color:red}</style></head>"
            + "<body><p>Olá, Ana.</p><p>Seu perfil &amp; sua experiência seguem para a <b>próxima etapa</b>.</p></body></html>";

    @Test
    void parsesApprovedAndRefusedInTheContractFormat() {
        var approved = AnalysisResult.parse("aproved | Seu processo segue em frente | " + HTML);
        assertThat(approved.decision()).isEqualTo(ResumeStatus.APROVED);
        assertThat(approved.subject()).isEqualTo("Seu processo segue em frente");
        assertThat(approved.html()).isEqualTo(HTML);

        assertThat(AnalysisResult.parse("recuse | Atualização da candidatura | " + HTML).decision())
                .isEqualTo(ResumeStatus.RECUSE);
    }

    @Test
    void htmlMayContainTheSeparator() {
        var result = AnalysisResult.parse("aproved | Assunto | <html><body>a | b | c</body></html>");
        assertThat(result.html()).isEqualTo("<html><body>a | b | c</body></html>");
        assertThat(result.subject()).isEqualTo("Assunto");
    }

    @Test
    void toleratesWhitespaceCaseAndCodeFences() {
        assertThat(AnalysisResult.parse("  \n APROVED | Assunto | " + HTML + "\n  ").decision()).isEqualTo(ResumeStatus.APROVED);
        var fenced = AnalysisResult.parse("```html\nrecuse | Assunto | " + HTML + "\n```");
        assertThat(fenced.decision()).isEqualTo(ResumeStatus.RECUSE);
        assertThat(fenced.html()).isEqualTo(HTML);
        assertThat(AnalysisResult.parse("aproved |   Assunto   \n  com quebra  | " + HTML).subject()).isEqualTo("Assunto com quebra");
    }

    @Test
    void rejectsAnythingOutsideTheContract() {
        for (String bad : new String[] {
                null, "", "   ",
                "sem separador nenhum",
                "aproved | só um separador",
                "approved | Assunto | " + HTML,   // grafia corrigida pela IA: não pode
                "aprovado | Assunto | " + HTML,
                "pending | Assunto | " + HTML,
                "aproved |  | " + HTML,            // assunto vazio
                "aproved | Assunto | só texto sem html",
                "Claro! Aqui está: aproved | Assunto | " + HTML}) {
            assertThatThrownBy(() -> AnalysisResult.parse(bad)).as("[%s]", bad)
                    .isInstanceOf(DomainException.Unprocessable.class);
        }
    }

    @Test
    void reasonIsReadableTextWithoutHtmlOrStyles() {
        String reason = AnalysisResult.parse("aproved | Seu processo segue em frente | " + HTML).reason();
        assertThat(reason).startsWith("Seu processo segue em frente\n\n");
        assertThat(reason).contains("Olá, Ana.").contains("Seu perfil & sua experiência seguem para a próxima etapa.");
        assertThat(reason).doesNotContain("<").doesNotContain("color:red").doesNotContain("&amp;");
    }

    @Test
    void reasonIsCapped() {
        String reason = AnalysisResult.parse("recuse | S | <html><body>" + "x".repeat(10_000) + "</body></html>").reason();
        assertThat(reason).hasSize(4000).endsWith("…");
    }
}
