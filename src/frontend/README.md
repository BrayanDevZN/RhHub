# RhHub: front end

Aplicação de página única (SPA) do RhHub: o candidato escolhe vagas, envia o currículo e acompanha o resultado da IA e a etapa do processo; o administrador gerencia vagas, etapas, candidaturas e usuários.

Em produção o front é servido por **nginx**, que também repassa `/api` para o back end. O código fala só com o próprio domínio, e é por isso que os cookies de sessão (`HttpOnly`, `SameSite=Strict`) funcionam.

## Tecnologias

| Item | Uso |
| --- | --- |
| React 19 + TypeScript | Interface e tipagem |
| Vite 8 | Servidor de desenvolvimento e build |
| React Router 7 | Rotas e proteção por sessão e por papel |
| Tailwind CSS 4 | Estilo (plugin `@tailwindcss/vite`); só tokens de tema e animações ficam em `src/index.css` |
| Base UI + componentes no estilo shadcn/ui | Componentes em `src/components/ui` (estilo `base-nova`, ícones `lucide-react`) |
| Three.js (`@react-three/fiber` e `drei`) | A cena 3D das telas de entrada (login, cadastro), carregada sob demanda |
| Oxlint | Lint |

Não há biblioteca de estado nem de requisições: o estado de sessão fica em um contexto (`AuthProvider`) e os dados de cada tela vêm de um hook pequeno (`useQuery`).

## Scripts

Todos rodam dentro de `src/frontend` (Node 22).

| Comando | O que faz |
| --- | --- |
| `npm ci` | Instala as dependências do `package-lock.json` |
| `npm run dev` | Servidor de desenvolvimento do Vite, com HMR |
| `npm run build` | Confere os tipos (`tsc -b`) e gera `dist/` |
| `npm run lint` | Roda o Oxlint (configuração em `.oxlintrc.json`) |
| `npm run preview` | Serve o `dist/` gerado, para conferir o build |

O CI roda `npm run lint` e `npm run build` a cada push e pull request.

## Como rodar

### Tudo junto, pelo Docker Compose (recomendado)

Na raiz do repositório:

```bash
cp .env.compose.example .env
docker compose up --build
```

O front fica em <http://localhost:9000> e já repassa `/api` para a API, então login, vagas e candidaturas funcionam. É o jeito mais simples de ver o sistema inteiro.

### Só o servidor do Vite

```bash
cd src/frontend
npm ci
npm run dev
```

O cliente chama sempre caminhos relativos (`/api/...`) e o `vite.config.ts` **não tem proxy**. Por isso, só com `npm run dev` a tela abre, mas as chamadas à API não chegam ao back end. Para desenvolver contra uma API local, há duas saídas:

- subir o sistema pelo Compose e usar o front dele (porta 9000), reconstruindo a imagem a cada mudança; ou
- acrescentar um proxy no `vite.config.ts` (não vem no repositório), que mantém tudo no mesmo domínio:

```ts
export default defineConfig({
  // ...plugins e alias que já existem
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
    },
  },
})
```

Com o proxy, a API local precisa de `COOKIE_SECURE=false` (http local) e `FRONTEND_URL` apontando para o endereço do Vite.

## Estrutura de pastas

```
src/frontend/
├── index.html
├── vite.config.ts            plugins (React, Tailwind) e o alias "@" → src/
├── components.json           configuração do shadcn/ui (estilo base-nova, aliases)
├── nginx.conf                servidor estático com fallback de SPA
├── 40-rhhub-listen.sh        gera as portas que o nginx escuta (9000 e a PORT da plataforma)
├── 41-rhhub-api.sh           gera o repasse de /api conforme BACKEND_URL
├── public/                   favicon
└── src/
    ├── main.tsx              entrada: monta o <App />
    ├── App.tsx               providers e tabela de rotas (com os guardas de acesso)
    ├── index.css             tokens de tema (claro e escuro), visual do login e animações do painel
    ├── pages/                uma tela por rota
    ├── components/           peças compartilhadas (layout, sino, etapas, tabela de candidaturas...)
    │   └── ui/               componentes base no estilo shadcn/ui
    ├── hooks/                use-mobile (detecta tela pequena)
    └── lib/
        ├── api.ts            cliente HTTP da API (único lugar que usa fetch)
        ├── auth.tsx          AuthProvider e hooks useAuth / useCurrentUser
        ├── use-query.ts      busca de dados com recarga e atualização periódica
        ├── types.ts          tipos do domínio (User, Vacancy, Resume...) e constantes
        ├── format.ts         datas e iniciais
        └── utils.ts          cn() para combinar classes
```

O alias `@` aponta para `src/`: importe com `@/lib/api`, `@/components/...`.

## Telas e rotas

| Rota | Tela | Quem acessa |
| --- | --- | --- |
| `/login` | Entrar (e-mail e senha, Google, 2FA e recuperação de senha) | Só deslogado |
| `/cadastro` | Criar conta, com código por e-mail | Só deslogado |
| `/completar-cadastro` | Informar CPF, idade e gênero (primeiro acesso com Google) | Logado |
| `/` | Painel: números e candidaturas recentes | Logado |
| `/vagas` | Lista de vagas, detalhes, candidatura e (admin) criar, editar e excluir | Logado |
| `/candidaturas` | Minhas candidaturas, resultado da IA e etapa atual | Logado |
| `/perfil` | Aba Dados (dados pessoais, foto, 2FA e troca de senha) e aba Conta (resetar senha por código no e-mail e exclusão da conta) | Logado |
| `/admin/usuarios` | Gestão de usuários e papéis | Admin |
| `/admin/candidaturas` | Todas as candidaturas, com filtro por status e mudança de etapa | Admin |

Os guardas ficam em `App.tsx`:

- **`PublicRoute`**: quem já está logado vai para `/`.
- **`SessionRoute`**: exige login, mas não o cadastro completo (é a rota que o completa).
- **`ProtectedRoute`**: exige login; conta criada com o Google sem CPF, idade ou gênero é enviada para `/completar-cadastro` (`isProfileIncomplete`).
- **`AdminRoute`**: exige `role === "admin"`; um candidato volta para `/`.
- Qualquer rota desconhecida volta para `/`.

## Comunicação com a API (`src/lib/api.ts`)

- Todas as chamadas vão para `/api` no mesmo domínio, com `credentials: "include"`. Os tokens vivem em cookies `HttpOnly` e **nunca passam pelo JavaScript**.
- O objeto `api` agrupa as chamadas por assunto: `auth`, `users`, `notifications`, `admin`, `vacancies` e `resumes`. Telas e componentes não chamam `fetch` direto.
- **Renovação da sessão.** O token de acesso vale 15 minutos. Um `401` dispara `POST /api/auth/refresh` (uma vez, mesmo que várias chamadas falhem juntas) e repete a requisição. Se o refresh responde `401` ou `403`, o cliente emite o evento `rhhub:unauthorized` e o `AuthProvider` volta para o login. Falha de rede, `5xx` ou limite de requisições **não deslogam**: viram uma mensagem de erro.
- **Aba parada.** Ao voltar para a aba depois de mais de 10 minutos sem resposta boa, `keepSessionAlive` renova a sessão antes de a primeira chamada falhar.
- **Chamadas em segundo plano** (como o sino de notificações) usam `silent: true` e nunca derrubam a sessão sozinhas.
- **Erros.** Toda falha vira `ApiError`, com a `message` devolvida pela API ou um texto padrão por status (400, 401, 403, 404, 409, 422, 429, 502 e 503).
- **Uploads** (currículo, foto de perfil e de vaga) usam `FormData`; a API valida tipo e tamanho.

O formato do JSON (snake_case, como em `vacancy_id` e `expires_at`) e as rotas estão em [`DOCUMENTACAO.md`](../../DOCUMENTACAO.md), seções 4.5 e 6.

## Estado e dados

- **`AuthProvider`** (`lib/auth.tsx`): carrega o usuário (`GET /api/users/me`) ao abrir o app e expõe `user`, `loading`, `refresh` e `logout`. Também guarda `photoVersion`, que muda a cada troca de foto para o navegador buscar a imagem de novo.
- **`useQuery(fetcher, { pollMs, shouldPoll })`** (`lib/use-query.ts`): busca os dados, expõe `data`, `loading` e `reload`, e mostra um aviso (toast) se a busca falhar. Com `pollMs`, recarrega em intervalo enquanto `shouldPoll` for verdadeiro.
- **Atualização automática.** Painel, Minhas candidaturas e Candidaturas do admin consultam a API a cada 2 segundos **enquanto houver candidatura `pending`**, para o resultado da IA aparecer sozinho. Quando nenhuma está pendente, a consulta para.
- **Notificações.** O sino (`NotificationBell`) lista os avisos e o contador de não lidos, e marca como lido.

## Estilo e componentes

- **Tema.** Os tokens semânticos (cores, raios, modo escuro) ficam em `index.css`, nas camadas do Tailwind e do shadcn. O layout do app (barra lateral, topo, painel, cartões) é montado só com componentes shadcn (`Sidebar`, `Card`, `Badge`, `Breadcrumb`, `Popover`, `DropdownMenu`...) e utilitários do Tailwind; as únicas classes próprias são as animações `rh-rise`, `rh-bob`, `rh-drift`, `rh-pulse`, `rh-dot` e `rh-grow`, que respeitam "reduzir movimento".
- **Componentes base** em `src/components/ui` seguem o shadcn/ui sobre o Base UI. Para trazer outro componente, use a CLI (`npx shadcn add <nome>`): ela lê o `components.json`. Prefira compor com o que já existe antes de criar um novo.
- **Classes condicionais:** use `cn(...)` de `@/lib/utils`.
- **Avisos** de sucesso e erro: `toast.add({ title, description, type })` (`components/ui/toast`).
- **Cena 3D** (`Scene3D`): usa Three.js e só aparece nas telas de entrada (`AuthLayout`). É carregada por import dinâmico, então não pesa nas telas internas.
- **Responsivo e barra lateral.** A barra lateral é o `Sidebar` do shadcn (`components/ui/sidebar.tsx`), montado em `components/AppLayout.tsx`. No desktop ela pode ser minimizada para só os ícones pelo botão do topo (ou com `Ctrl/Cmd + B`; a escolha fica salva em cookie) e mostra uma dica com o nome de cada item. Abaixo de 768 px ela vira uma gaveta (`Sheet`) que abre pelo mesmo botão e fecha ao navegar. A tabela de candidaturas vira cartões em telas estreitas. Ao mexer no layout, confira em tela de celular (320 a 430 px) e em desktop, com a barra aberta e minimizada.

## Build e produção

O `Dockerfile.front` (na raiz do repositório) tem dois estágios: o Node compila (`npm ci` e `npm run build`) e o nginx serve o `dist/`.

| Item | Comportamento |
| --- | --- |
| Porta | 9000, e também a `PORT` injetada pela plataforma, se for diferente (`40-rhhub-listen.sh`) |
| Fallback de SPA | Qualquer caminho que não seja arquivo devolve `index.html` |
| Cache | `/assets/` com `Cache-Control: public, immutable` por 1 ano (os arquivos têm hash no nome) |
| Repasse da API | `BACKEND_URL` (ou o nome antigo `API_UPSTREAM`) define o destino de `/api`, `/oauth2` e `/login/oauth2` (`41-rhhub-api.sh`). Aceita `http://`, `https://` ou só `host:porta`. Sem a variável, o front sobe sozinho e `/api` não é repassado |
| IP do cliente | O nginx envia à API só o último valor de `X-Forwarded-For` (o que a plataforma acrescentou), usado no limite de requisições por IP |

Front e API precisam estar no **mesmo domínio**, porque os cookies são `SameSite=Strict`.

## Qualidade

- `npm run lint` precisa passar sem erros (`react/rules-of-hooks` é erro; `react/only-export-components` é aviso).
- `npm run build` confere os tipos (`tsc -b`) antes do Vite: erro de tipo, variável ou parâmetro não usado quebra o build.
