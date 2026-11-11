# LivroStock v0.4 — temas, papéis e auditoria

> Branch experimental `feature/v0.4-rbac-auditoria-temas`, baseada na v0.3. **Não mesclar nem publicar antes da homologação local.**

## Temas da interface

No cabeçalho existe o seletor **Tema**, com três opções:

| Opção | Aparência | Uso sugerido |
| --- | --- | --- |
| **Light** | Branco e verde | Ambiente bem iluminado |
| **Moderado** | Cinza-claro azulado e contraste suave | Longas sessões de trabalho |
| **Dark** | Azul-escuro com textos claros | Ambiente com pouca luz |

A escolha é salva em `localStorage` com a chave `livrostock.tema`. O modo padrão é Light. A preferência não depende de login e não é enviada ao servidor.

## Autenticação e permissões

Com `SPRING_PROFILES_ACTIVE=secure`:
- Login JWT necessário para acessar o **dashboard inteiro**, incluindo catálogo e movimentações.
- `BIBLIOTECARIO`: consulta, criação, edição, desativação, movimentação, upload e acesso à auditoria.
- `LEITOR`: consulta a livros, histórico, PDFs e download. Não pode modificar estoque ou livros, enviar PDFs ou consultar auditoria.
- Token válido por 30 minutos e guardado **somente em memória no React**. Ao atualizar a página, é necessário entrar novamente.
- `/actuator/health`, `/actuator/info`, `/api/v1/auth/mode` e `POST /api/v1/auth/login` são públicos.
- Outras rotas não explicitamente liberadas retornam acesso negado.

A interface omite botões de administração para o leitor, mas a segurança **não depende do frontend**: as permissões são conferidas no Spring Security.

Sem o profile `secure`, o ambiente mantém o comportamento local da v0.3 sem autenticação. **Não exponha esse modo a redes públicas.**

## Auditoria

A migration **V3__create_auditoria_eventos.sql** cria a tabela `auditoria_eventos`, sem modificar as tabelas preexistentes.

No perfil seguro, o backend registra após a conclusão com sucesso de:
- Cadastro, atualização e desativação de livros.
- Movimentações de estoque.
- Upload, leitura e download de PDF.

São armazenados: usuário autenticado, operação, método HTTP, rota, código HTTP e instante UTC. Tokens, senhas e conteúdo dos arquivos **não** são armazenados.

O painel **Auditoria** aparece somente para o perfil Bibliotecário. Também existe a API `GET /api/v1/auditoria?page=0&size=20`, protegida por JWT com perfil de bibliotecário.

**Limitação importante:** a auditoria atual é *best-effort* e executada após a resposta. Se ocorrer falha ao gravar a auditoria, a operação de negócio já pode ter sido concluída. Não equivale a auditoria forense, nem registra tentativas bloqueadas. Não substitui logs estruturados, alertas operacionais ou transações de outbox.

## Testes locais (Windows)

Preserve o `application.yml` e suas configurações PostgreSQL, inclusive o mapeamento de porta do Docker que já está funcionando. Em `backend`:

```powershell
git fetch origin
git switch --track origin/feature/v0.4-rbac-auditoria-temas
mvn clean verify
```

Se a branch já existir: `git switch feature/v0.4-rbac-auditoria-temas`. Não force checkout sobre alterações locais.

Para iniciar, use o mesmo procedimento de variáveis de ambiente da v0.3 em [SEGURANCA.md](SEGURANCA.md), incluindo `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=secure`, senhas e segredo JWT. Não compartilhe valores secretos no chat ou em commits.

```powershell
mvn spring-boot:run
```

Em outro terminal:

```powershell
cd C:\Projetos\livrostock-repo\frontend
npm ci
npm run dev
```

**Checklist de homologação:**
1. Entrar como bibliotecário, alternar Light → Moderado → Dark e atualizar a página; o tema escolhido deve permanecer.
2. Abrir catálogo, cadastrar um livro de teste autorizado ou movimentar estoque e verificar uma entrada em **Auditoria**.
3. Entrar como leitor: catálogo, histórico e leitura/download de PDF disponíveis; sem botões de criação e estoque, sem menu Auditoria.
4. Em REST, verificar que o leitor recebe `403` em `POST/PUT/DELETE /api/v1/livros` e `GET /api/v1/auditoria`.
5. Confirmar que a migration V3 foi aplicada ao **banco existente** sem perda de registros; verificar a persistência da auditoria após reinício.

## Ainda pendente para produção

- Gestão persistente de usuários, papéis e credenciais no PostgreSQL. **A v0.4 ainda utiliza usuários configurados por variáveis de ambiente**.
- Revogação/rotação de tokens e chaves, gestão de sessão, rate limiting e MFA.
- Auditoria transacional, rastreamento de falhas e alertas sobre perda de registros.
- Testes de integração PostgreSQL com Flyway e cenários de migração em ambientes reais.
- Inspeção antimalware e política de direitos autorais/licença dos PDFs.
- HTTPS, proteção de segredos e armazenamento privado antes de exposição externa.
