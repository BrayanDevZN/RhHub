## O que muda

<!-- Explique em poucas linhas o que este PR faz e por quê. -->

## Tipo de mudança

- [ ] Correção de bug
- [ ] Nova funcionalidade
- [ ] Refatoração (sem mudar comportamento)
- [ ] Documentação, testes ou CI

## Como testar

<!-- Passos para ver funcionando. Se mexeu na API, cite as rotas. -->

## Checklist

- [ ] Os testes passam: `cd src/backend && mvn test` (ou a categoria: `-Pcategory-unit`, `-Pcategory-integration`, `-Pcategory-functional`)
- [ ] Teste novo na categoria certa (`@UnitTest`, `@IntegrationTest` ou `@FunctionalTest`)
- [ ] Front: `cd src/frontend && npm run lint && npm run build`
- [ ] `Dockerfile` e `Dockerfile.front` continuam idênticos (se mexi em um)
- [ ] Documentação atualizada (`DOCUMENTACAO.md` e/ou `README.md`), se a API ou o comportamento mudou
- [ ] Nenhum segredo, chave ou dado pessoal foi commitado
