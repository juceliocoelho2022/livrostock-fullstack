# Roadmap — LivroStock

> Entregas baseadas no código versionado e em verificações realizadas no ambiente local. Marcação de implementação não implica deploy em produção.

## ✅ v0.1 — MVP de gestão de livros

- [x] CRUD de livros (incluindo desativação lógica)
- [x] Registro de entradas e saídas
- [x] Bloqueio de saldo negativo
- [x] Histórico de movimentações
- [x] Alerta de estoque baixo
- [x] Dashboard React/TypeScript
- [x] Swagger/OpenAPI e Flyway
- [x] Integração local entre frontend, API e PostgreSQL
- [x] README de apresentação e execução local
- [ ] Verificar execução verde do pipeline CI no GitHub
- [ ] Adicionar capturas do sistema em `docs/images/`

## 🚧 v0.2 — Acervo digital e expansão do domínio

### Sprint 0 — Exemplares PDF (backend inicial na branch)

- [x] Adicionar migration, entidade e repositório de exemplares digitais
- [x] Criar API inicial de upload, listagem e leitura/download
- [x] Registrar regras de negócio e DER do módulo
- [x] Compilar backend com Maven e executar migração V2 em banco local existente
- [x] Validar upload e listagem de PDF de teste pela API
- [x] Integrar tela React de upload, listagem e leitor usando PDF nativo do navegador
- [ ] Validar manualmente a integração React com a API no ambiente local
- [ ] Testar upload, validações, leitura, erros e armazenamento persistente
- [ ] Implementar autenticação e autorização antes de publicar acesso a documentos
- [ ] Reforçar análise de PDF, gestão de licenças e limpeza de arquivos órfãos

**Critério de aceite:** upload autorizado de um PDF, recuperação do documento pelo navegador e teste de negativas; não publicar até ter autenticação, autorização e inspeção adequada de arquivos.


### Sprint 1 — Categorias

- [ ] Modelar `Categoria` com identificador, nome único e status ativo
- [ ] Criar migration Flyway e repositório JPA
- [ ] Implementar API de categorias com validação e tratamento de erros
- [ ] Associar livros à entidade Categoria sem perder dados existentes
- [ ] Adicionar seleção de categoria no formulário React
- [ ] Testar CRUD, unicidade, associação e compatibilidade da migração

**Critério de aceite:** criar uma categoria, associá-la a um livro, consultar os dados no catálogo e impedir nomes duplicados.

### Sprint 2 — Editoras

- [ ] Modelar `Editora` (nome, contato opcional, status)
- [ ] Criar migrations e endpoints REST
- [ ] Associar editora ao livro
- [ ] Criar manutenção no frontend
- [ ] Testar fluxos positivos, validações e referências

**Critério de aceite:** cadastrar e selecionar uma editora no livro sem quebrar o CRUD existente.

### Sprint 3 — Fornecedores

- [ ] Modelar `Fornecedor` com nome, documento opcional, contato e status
- [ ] Criar migrations, serviços, repositórios e endpoints
- [ ] Preparar vínculo entre fornecedor e entradas de estoque
- [ ] Criar tela de consulta/cadastro no frontend
- [ ] Testar regras de desativação e associação

**Critério de aceite:** registrar um fornecedor e identificá-lo numa entrada de estoque, preservando histórico.

### Sprint 4 — Qualidade e documentação

- [ ] Adicionar testes de integração PostgreSQL (Testcontainers)
- [ ] Validar as migrações num banco novo e num banco já populado
- [ ] Atualizar Swagger, API e DER
- [ ] Confirmar build Java, build frontend e CI
- [ ] Preparar release notes e tag `v0.2.0`

**Definição de pronto v0.2:** fluxo de livro → categoria/editora → fornecedor → entrada de estoque documentado, testado e executável no ambiente local.

## 🔒 v0.3 — Segurança e operação

- [ ] Autenticação com Spring Security e JWT
- [ ] Autorização por perfis (ex.: ADMIN, OPERADOR, CONSULTA)
- [ ] Auditoria de mudanças e movimentações por usuário
- [ ] Substituição de credenciais de exemplo por configurações seguras
- [ ] Observabilidade, logs estruturados e métricas
- [ ] Testes de segurança e autorização

## 🚀 v1.0 — Publicação

- [ ] Validar proteção de segredos e configurações por ambiente
- [ ] Pipeline de build/testes/release estabilizado
- [ ] Documentar backup e restauração PostgreSQL
- [ ] Implantação controlada com HTTPS e monitoramento
- [ ] Manual de operação e demonstração de portfólio

## Convenções propostas

- Branches: `feature/v0.2-categorias`, `feature/v0.2-editoras`, `feature/v0.2-fornecedores`
- Commits: `feat: ...`, `fix: ...`, `test: ...`, `docs: ...`
- Antes de cada alteração de schema, preservar backup e revisar o script Flyway
