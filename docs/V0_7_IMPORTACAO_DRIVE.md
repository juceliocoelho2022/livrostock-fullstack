# LivroStock v0.7 — importação privada de livros PDF do Google Drive

**Objetivo:** importar documentos que o titular já possui em sua conta Google Drive para o catálogo particular do LivroStock, usando o backend Java e sem publicar cópias no GitHub.

## O que mudou

- Flyway **V5__livros_digitais_drive.sql**: ISBN pode ser nulo, novos campos `somente_digital` e `origem_drive_id` com índice único. Livros existentes e movimentações não são modificados.
- Endpoint `POST /api/v1/importacoes-digitais/drive`, exclusivamente com **Spring profile secure**, permitido apenas ao perfil **BIBLIOTECARIO**.
- Multipart form: `driveFileId`, `titulo`, `autor`, `categoria`, `arquivo` (.pdf).
- A importação cria um livro digital (`preco=0`, `quantidade=0`, `estoqueMinimo=0`, sem ISBN) e associa exatamente um PDF via Biblioteca Digital.
- Validadores existentes: cabeçalho PDF, limite de 20 MiB, SHA-256 no armazenamento. Importação repetida com o mesmo `driveFileId` retorna **409**, sem duplicar livro.
- Livro, PDF e auditoria participam da mesma transação SQL; o arquivo PDF é removido em caso de rollback conhecido.
- Livros digitais são identificados no catálogo, não geram alertas de estoque e não aceitam movimentação física.

## Como importar os PDFs do seu Drive

O arquivo ZIP privado com PDFs reais, manifesto e importador PowerShell é entregue exclusivamente ao usuário na conversa. **Não adicione nem faça commit desse ZIP ou dos PDFs ao repositório.**

1. **Faça backup do PostgreSQL + PDFs atuais** usando a v0.6: `ops/criar-backup.ps1 -ContainerPostgres livrostock-api-postgres-1 ...` e depois valide uma restauração isolada.
2. No diretório `C:\Projetos\livrostock-repo`, verifique alterações locais e atualize a branch:

```powershell
cd C:\Projetos\livrostock-repo
git status --short
git fetch origin
git switch --track origin/feature/v0.7-importacao-drive-pdfs
```

Se a branch já existir localmente, use `git switch feature/v0.7-importacao-drive-pdfs`. Se o checkout estiver bloqueado por arquivos modificados, preserve as edições antes de continuar; **não use** `git reset --hard`.

3. Inicie o backend no perfil `secure`, com `DB_URL=jdbc:postgresql://127.0.0.1:5434/livrostock`, `DB_USER` e `DB_PASSWORD` corretos, e `LIVROSTOCK_JWT_SECRET` (32+ bytes). A migração Flyway V5 será aplicada na inicialização. As contas existentes permanecem intactas.
4. Em uma pasta **fora do Git** (p.ex. `C:\ImportacaoLivroStock`), extraia o ZIP. Os 9 PDFs e `manifesto.json` devem ficar ao lado de `importar-livros.ps1`.
5. Abra PowerShell nessa pasta e faça uma simulação, que confere todos os hashes e não altera dados:

```powershell
cd C:\ImportacaoLivroStock
.\importar-livros.ps1 -Simular
```

6. Com a API segura funcionando na porta 8081 e o backup validado, execute:

```powershell
.\importar-livros.ps1 -ConfirmarImportacao
```

Digite a senha do Bibliotecário somente no prompt protegido, sem compartilhá-la no chat. O importador usa apenas `http://127.0.0.1:8081`, verifica `/actuator/health` e `/api/v1/auth/mode` e envia cada PDF via API autenticada.

7. Abra `http://localhost:5173` e confira os títulos no **Catálogo de livros**, marcados como “Livro digital”. Em **Biblioteca Digital**, selecione um título e confira o PDF associado e o botão Ler.

## Importante: segurança, uso e direitos

- Esta versão não oferece integração OAuth Google Drive no backend: a transferência é um **pacote local de PDFs baixados da conta conectada**, não sincronização automática de cloud.
- O pacote privado contém cópias integrais dos arquivos de usuário. Não colocar em Git, publicar online, enviar a terceiros ou habilitar download público de materiais protegidos por direitos autorais sem autorização.
- Não forneça o JWT do LivroStock ou credenciais Google ao script ou ao chatbot. Somente a senha do **Bibliotecário LivroStock** é solicitada localmente para autenticar a API.
- Todas as alterações ocorrem no PostgreSQL local selecionado por `DB_URL`; os arquivos continuam no diretório `PDF_STORAGE_DIR` da aplicação.
- Se houver erro, revise mensagens e execute de novo. O backend reconhece IDs Drive já importados e os ignora com 409, sem criar duplicatas.
- PDFs acima de 20 MiB não são suportados pelo endpoint atual; escolha documentos menores ou desenvolva tratamento de uploads grandes antes de importá-los.
- Antes de uma implantação pública, são necessários HTTPS, controle de direitos digitais e política de acesso compatível com a legislação de propriedade intelectual.

## Homologação

No GitHub Actions, o teste com PostgreSQL 16 valida a migração V5, o login, a permissão Bibliotecário/Leitor, a criação de livro digital com ISBN nulo, o vínculo ao PDF e a prevenção de duplicatas. A validação com seu PostgreSQL de 5434 e seu diretório real de PDFs é **separada** e requer a execução dos comandos locais.
