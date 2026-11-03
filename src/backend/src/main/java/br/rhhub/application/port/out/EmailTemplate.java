package br.rhhub.application.port.out;

public enum EmailTemplate {
    CREATE_ACCOUNT("create_account"),
    TWO_FACTOR("two_factor_authentication"),
    UPDATE_PASSWORD("update_password"),
    RESUME_ANALYSIS_TEST("resume_analysis_test");

    private final String file;

    EmailTemplate(String file) {
        this.file = file;
    }

    public String file() {
        return file;
    }
}
