# Como contribuir

## Rodando localmente

```bash
cp .env.compose.example .env && docker compose up --build   # tudo junto, em http://localhost:9000
```

Ou separado: `cd src/backend && ./mvnw spring-boot:run` e `cd src/frontend && npm ci && npm run dev`.

## Testes

Todo teste do back end pertence a **uma** categoria, marcada por anotação (`br.rhhub.testing`). Escolha pela regra:

| Se o teste... | Categoria |
|---|---|
| roda só na memória (regra, serviço com `Fakes`, validação) | `@UnitTest` |
| usa **uma** peça real de infraestrutura (banco, Redis, Kafka embarcado, servidor HTTP local) | `@IntegrationTest` |
| chama a API HTTP inteira ou percorre o fluxo completo | `@FunctionalTest` |
| chama Groq ou n8n de verdade | `@LiveTest` (nunca roda no CI) |

```bash
cd src/backend
mvn test                          # unit + integration + functional
mvn test -Pcategory-unit          # ou -integration, -functional, -live
```

Os testes usam `ENVIRONMENT=test`: não precisam de chaves nem enviam e-mail. Teste novo sem anotação só roda na execução completa, fora dos jobs de categoria do CI.

No front: `cd src/frontend && npm run lint && npm run build`.

## Commits e pull requests

- Mensagens no padrão `tipo: o que mudou` (`feat`, `fix`, `docs`, `refactor`, `test`, `chore`), com `(escopo)` quando ajudar: `fix(auth): ...`.
- Se mexer em `Dockerfile`, altere também `Dockerfile.front` (o CI exige que sejam idênticos).
- Se a API ou uma regra mudar, atualize a `DOCUMENTACAO.md`.
- Nunca commite segredos: use o `.env` (fora do git) e o `.env.compose.example` como modelo.
