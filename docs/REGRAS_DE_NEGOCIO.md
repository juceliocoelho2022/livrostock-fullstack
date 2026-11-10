# Regras de negócio — LivroStock

> **Convenção:** **Implementado v0.1** = regra no código atual. **Implementado backend na branch v0.2** = precisa de validação de build e execução. **Planejado** = especificação, não funcionalidade entregue.

## RN-001 — Cadastro de livro (implementado v0.1)

- Título, autor, ISBN, categoria, preço e estoque mínimo são informados no cadastro.
- ISBN deve ser único; quantidade inicial é zero.
- Preço e estoque mínimo não podem ser negativos.
- Livro pode ser desativado logicamente, preservando seu histórico.

## RN-002 — Movimentação de estoque (implementado v0.1)

- Apenas movimentações do tipo `ENTRADA` e `SAIDA`.
- Quantidade movimentada obrigatoriamente positiva.
- É proibida uma saída que deixe saldo negativo.
- Cada operação preserva o saldo resultante, data e observação opcional.
- O saldo não deve ser alterado diretamente na edição de cadastro.

## RN-003 — Reposição (implementado v0.1)

- Livro com quantidade abaixo do estoque mínimo integra o relatório de estoque baixo.
- O alerta é informativo; não cria pedido de compra automaticamente.

## RN-004 — Exemplares digitais (backend inicial v0.2, validação pendente)

- O PDF está vinculado a um livro existente e ativo.
- São aceitos somente arquivos com extensão `.pdf` e assinatura inicial `%PDF-`, com até 20 MiB.
- Nome enviado pelo usuário não determina o caminho salvo; armazenamento usa UUID.
- Cada exemplar possui identificador próprio, nome original, tamanho, SHA-256, data e tipo MIME.
- PDF não altera a quantidade de exemplares físicos.
- Listagem de arquivos digitais e leitura/download dependem de endpoint específico.
- O arquivo deve ser de domínio público, licenciado ou ter autorização de disponibilização.
- **Limitação:** verificar cabeçalho não substitui inspeção antimalware ou parsing completo de PDF; o sistema ainda não possui controle de acesso.

## RN-005 — Segurança dos documentos (planejado)

- Implementar JWT e papéis `ADMIN`, `BIBLIOTECARIO`/`OPERADOR`, `LEITOR`.
- `ADMIN` ou `BIBLIOTECARIO`: upload e gestão de arquivos, mediante direitos.
- `LEITOR`: somente arquivos autorizados pela política de acesso.
- Auditar leitura, download, criação, substituição e exclusão.
- Exigir tamanho máximo, inspeção de conteúdo e antivírus em upload.
- Considerar licença, titular de direitos, escopo e data de expiração do direito de acesso.
- Nunca expor diretamente o diretório de armazenamento ou usar nomes originais como caminhos.
- Bloquear API pública até implantar autenticação e autorização.

## RN-006 — Integridade e operação (planejado)

- Remover arquivos órfãos após falha de persistência; manter backups consistentes de banco e storage.
- Implementar política de retenção, versões do exemplar e exclusão controlada.
- Verificar checksum na recuperação e monitorar falhas de armazenamento.
- Evitar gravações concorrentes de estoque que violem saldo e histórico.
- Expor logs estruturados, métricas, correlação de requisição e health probes.

## RN-007 — Catálogo profissional (planejado)

- Categoria e editora como entidades vinculadas ao livro (migração sem perda da categoria textual atual).
- Fornecedor relacionado à entrada de estoque.
- Pesquisa por ISBN/título/autor, filtros, ordenação e paginação.
- Relatórios de estoque, custo, divergências, títulos mais movimentados e auditoria.
- Exportação CSV/Excel com controle de acesso e trilha de auditoria.

## Critérios de aceite do módulo digital

1. Enviar PDF válido para um livro cadastrado: receber `201` e metadados.
2. Consultar a listagem: encontrar o exemplar ligado ao livro.
3. Abrir o documento: resposta com `application/pdf` e `Content-Disposition: inline`.
4. Baixar o documento: `Content-Disposition: attachment`.
5. Enviar arquivo com outro formato, vazio ou acima do limite: falhar sem cadastro.
6. Enviar para livro inexistente ou inativo: falhar sem cadastro.
7. Com segurança futura: usuário sem permissão recebe `403` e nenhuma leitura de arquivo.
