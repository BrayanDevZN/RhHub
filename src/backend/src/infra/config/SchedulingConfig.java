package br.rhhub.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas agendadas (@Scheduled), como o reenvio de candidaturas pendentes. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
