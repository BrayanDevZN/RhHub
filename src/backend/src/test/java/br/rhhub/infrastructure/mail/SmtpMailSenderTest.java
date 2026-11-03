package br.rhhub.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.infrastructure.TestSupport;
import br.rhhub.infrastructure.config.AppProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpMailSenderTest {

    private final EmailMessage message = new EmailMessage("ana@gmail.com", "Assunto", "<p>oi</p>");

    @Test
    void sendsHtmlWithConfiguredSender() throws Exception {
        var javaMail = mock(JavaMailSender.class);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMail.createMimeMessage()).thenReturn(mime);

        new SmtpMailSender(javaMail, TestSupport.props()).send(message);

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMail).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Assunto");
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("ana@gmail.com");
        assertThat(captor.getValue().getFrom()[0].toString()).isEqualTo("no-reply@rhhub.test");
    }

    @Test
    void doesNothingWhenDisabled() {
        var javaMail = mock(JavaMailSender.class);
        var base = TestSupport.props();
        var off = new AppProperties(base.environment(), base.jwt(), base.topics(), base.ai(),
                new AppProperties.Mail(false, "x@x"));

        new SmtpMailSender(javaMail, off).send(message);

        verify(javaMail, never()).send(any(MimeMessage.class));
    }
}
