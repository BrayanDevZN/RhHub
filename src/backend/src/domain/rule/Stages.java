package br.rhhub.domain.rule;

import br.rhhub.domain.exception.DomainException;
import java.util.ArrayList;
import java.util.List;

/**
 * Etapas do processo seletivo de uma vaga. A Triagem (a análise do currículo) é obrigatória e sempre a primeira; por
 * padrão a vaga também tem Entrevista e Contratado, e o admin pode tirar, trocar ou acrescentar etapas.
 */
public final class Stages {

    public static final String SCREENING = "Triagem";
    public static final List<String> DEFAULT = List.of(SCREENING, "Entrevista", "Contratado");
    public static final int MAX_STAGES = 10;
    public static final int MAX_NAME = 40;

    private Stages() {
    }

    /**
     * Lista nula ou vazia vira o padrão. Espaços sobrando e repetições (sem diferenciar maiúsculas) saem, e a Triagem
     * entra sempre em primeiro, mesmo que o admin não a envie.
     */
    public static List<String> normalize(List<String> raw) {
        if (raw == null || raw.stream().allMatch(s -> s == null || s.isBlank())) {
            return DEFAULT;
        }
        var result = new ArrayList<String>();
        result.add(SCREENING);
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String name = item.strip().replaceAll("\\s+", " ");
            if (name.length() > MAX_NAME) {
                throw new DomainException.Invalid("O nome da etapa pode ter no máximo " + MAX_NAME + " caracteres.");
            }
            if (name.contains("\n") || name.contains("\r") || name.chars().anyMatch(Character::isISOControl)) {
                throw new DomainException.Invalid("O nome da etapa tem caracteres inválidos.");
            }
            if (result.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                result.add(name);
            }
        }
        if (result.size() > MAX_STAGES) {
            throw new DomainException.Invalid("Uma vaga pode ter no máximo " + MAX_STAGES + " etapas.");
        }
        return List.copyOf(result);
    }

    /** Formato do banco: um nome por linha (o nome nunca tem quebra de linha). */
    public static String join(List<String> stages) {
        return String.join("\n", stages);
    }

    /** Lê o que o banco guarda; vaga antiga (coluna vazia) fica com o padrão. */
    public static List<String> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return DEFAULT;
        }
        return List.of(stored.split("\n"));
    }
}
