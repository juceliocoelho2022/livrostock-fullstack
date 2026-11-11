# LivroStock v0.5 — contas persistidas, JWT e auditoria

**Status:** PR #4 em rascunho sobre `feature/v0.4-rbac-auditoria-temas`. Não mesclar na `main` antes de homologar no Windows.

## O que muda

- **Flyway V4** cria apenas a tabela `usuarios` e seu índice. Não altera `livros`, `movimentacoes`, `exemplares_digitais` ou `auditoria_eventos`.
- **Senhas com BCrypt** persistidas em `usuarios.senha_hash`, nunca devolvidas pelos endpoints.
- No **primeiro início seguro** com a tabela vazia, a aplicação cria os usuários da v0.4 a partir de `LIVROSTOCK_LIBRARIAN_USER`, `LIVROSTOCK_LIBRARIAN_PASSWORD`, `LIVROSTOCK_READER_USER` e `LIVROSTOCK_READER_PASSWORD`. Os nomes padrão são `bibliotecario` e `leitor`.
- Nas execuções seguintes, se já houver usuários, o bootstrap **não altera senhas, perfis nem status**, mesmo que o ambiente contenha novos valores. A senha passa a ser gerenciada no painel **Usuários**.
- JWT é assinado com `LIVROSTOCK_JWT_SECRET`, válido por 30 minutos e contém `sub`, `roles` e `ver`. Cada requisição protegida verifica **status, perfil e versão da conta** no PostgreSQL. Alterar senha, perfil ou ativar/desativar uma conta invalida seus JWTs anteriores.
- A interface mantém **Light, Moderado e Dark**, o login global e a auditoria da v0.4.

## Gestão de usuários

Somente o **BIBLIOTECARIO** pode acessar `/api/v1/usuarios` e o menu **Usuários**.

| Método | Endpoint | Ação |
| --- | --- | --- |
| GET | `/api/v1/usuarios` | Listar contas paginadas sem hash de senha |
| POST | `/api/v1/usuarios` | Criar conta (nomeUsuario, senha, perfil) |
| PUT | `/api/v1/usuarios/{id}/perfil` | Alterar entre BIBLIOTECARIO/LEITOR |
| PATCH | `/api/v1/usuarios/{id}/status` | Ativar ou desativar |
| PUT | `/api/v1/usuarios/{id}/senha` | Redefinir senha, resposta 204 |

Regras: nomes entre 3 e 80 caracteres (letras ASCII, números, ponto, traço, sublinhado), comparados em minúsculas; senhas entre 12 e 200 caracteres; o último Bibliotecário ativo não pode ser desativado ou convertido em Leitor. **Limite atual:** a proteção de último administrador não foi testada para alterações concorrentes simultâneas.

## Auditoria de alterações

A criação de livros, edição, desativação, movimentações, contas e upload de PDFs passam a gravar evento **na mesma transação SQL**. Se a inserção do evento falhar, a transação de negócio é revertida. Para arquivos PDF no filesystem, há tentativa de limpeza em caso de rollback.

Leitura e download de PDFs ainda usam registro **posterior à resposta**, sujeito a falhas. Esta trilha não representa uma auditoria forense completa nem registra tentativas negadas.

## Atualização no Windows sem apagar dados

1. **Faça backup do banco existente** antes de executar migrations. Não use `docker compose down -v`, `drop database`, `flyway clean` ou qualquer script que apague volumes.
2. Na pasta do repositório, rode `git status --short`, preserve alterações locais e atualize a branch:

```powershell
cd C:\Projetos\livrostock-repo
git status --short
git fetch origin
git switch --track origin/feature/v0.5-usuarios-persistentes
```

Se a branch já existir localmente, use `git switch feature/v0.5-usuarios-persistentes`. Se o Git bloquear o checkout, preserve as alterações primeiro, sem forçar.

3. Confira a porta do PostgreSQL **existente**:

```powershell
cd C:\Projetos\livrostock-repo\backend
docker compose ps
docker compose port postgres 5432
```

4. Na **mesma janela do PowerShell que iniciará o backend**, configure `DB_URL`, `DB_USER` e `DB_PASSWORD` de acordo com a conexão real, sem divulgar a senha. Defina `SPRING_PROFILES_ACTIVE=secure` e `LIVROSTOCK_JWT_SECRET` com pelo menos 32 bytes.

   **No primeiro início da v0.5**, defina também os dois usuários e suas senhas **já utilizadas na v0.4** (12+ caracteres). Serão gravados com BCrypt e **não precisarão ser recadastrados** nas próximas execuções.

5. Execute:

```powershell
mvn clean verify
mvn spring-boot:run
```

6. Inicie o frontend em outro terminal:

```powershell
cd C:\Projetos\livrostock-repo\frontend
npm ci
npm run dev
```

Abra `http://localhost:5173` e autentique como `bibliotecario`.

## Checklist de homologação

- [ ] Flyway informou `V4__create_usuarios.sql` aplicada e a API iniciou com `secure`.
- [ ] Os livros e PDFs já cadastrados permanecem intactos.
- [ ] O login com a senha anterior da v0.4 funciona após o primeiro bootstrap.
- [ ] A tela **Usuários** cadastra um novo leitor sem exibir hash.
- [ ] O novo leitor autentica, consulta catálogo/PDFs, mas não administra contas.
- [ ] Redefinir a senha, desativar ou alterar perfil invalida tokens anteriores.
- [ ] Tentativa de desativar o último Bibliotecário retorna conflito.
- [ ] Painel Auditoria registra criação/alteração de contas e movimentações.
- [ ] Temas Light, Moderado e Dark continuam persistindo após atualizar a página.

## Evidência de CI

O workflow valida código Java 21, build React/TypeScript e **PostgreSQL 16 isolado** com Flyway V1–V4 e testes de login, persistência, revogação de JWT e auditoria. O CI em banco descartável **não substitui a homologação com seu banco existente**.

## Pendências de segurança para publicação

HTTPS, rate limiting em login, política de rotação de chaves/segredos, MFA, logs de acessos negados, monitoração de falha de auditoria, backup/restore automatizado, testes de concorrência na gestão dos administradores, inspeção antimalware de PDFs e política de direitos de distribuição. O profile sem `secure` continua aberto para desenvolvimento local; **não publicar assim**.
