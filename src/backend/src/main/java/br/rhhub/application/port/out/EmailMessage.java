package br.rhhub.application.port.out;

public record EmailMessage(String to, String subject, String body) {
}
