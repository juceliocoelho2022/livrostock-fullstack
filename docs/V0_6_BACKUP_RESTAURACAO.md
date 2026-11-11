# LivroStock v0.6 — backup verificável e restauração segura

**Branch:** feature/v0.6-backup-restore-seguro (PR em rascunho, base v0.5).

## Entrega

O objetivo é proteger os dados atuais do LivroStock: livros, estoque, movimentações, usuários, auditoria e arquivos PDF.

| Script | Finalidade | Efeito no banco original |
| --- | --- | --- |
| ops/criar-backup.ps1 | PostgreSQL custom dump + PDFs + manifesto SHA-256 + ZIP | Somente leitura |
| ops/verificar-backup.ps1 | Compara arquivos, tamanhos e hashes internos do ZIP sem extrair | Nenhum |
| ops/restaurar-em-teste.ps1 | Restaura dump em um banco NOVO e PDFs em uma pasta NOVA | Nenhum |
| ops/testar-verificador.ps1 | Exercita validação de ZIPs em ambientes descartáveis | Nenhum |

As rotinas foram desenhadas para Windows PowerShell 5.1 e PowerShell 7.

## 1. Verificações antes do backup

Pare o processo **Spring Boot**, mas mantenha o PostgreSQL do Docker em execução. A janela sem escritas é obrigatória: o dump SQL e a cópia dos PDFs não formam uma transação única.

No PowerShell:

~~~powershell
cd C:\Projetos\livrostock-repo\backend
docker compose ps
docker compose port postgres 5432
~~~

Confirme o caminho REAL usado pela aplicação para os PDFs. Se PDF_STORAGE_DIR foi configurado, utilize esse valor. Quando a aplicação é iniciada em backend e PDF_STORAGE_DIR não está configurado, o padrão é backend/data/pdfs.

## 2. Criar backup

Em outro terminal, na raiz do repositório:

~~~powershell
cd C:\Projetos\livrostock-repo
.\ops\criar-backup.ps1 -Destino "C:\Backups\LivroStock" -DiretorioPdf "C:\Projetos\livrostock-repo\backend\data\pdfs" -ConfirmoAplicacaoParada
~~~

Se o acervo estiver realmente vazio, somente após conferir a situação use a opção -PermitirAcervoVazio. Se houver PDFs em outra pasta, corrija -DiretorioPdf; não use acervo vazio para mascarar caminho incorreto.

O script cria um ZIP de nome exclusivo, contendo:
- database/livrostock.dump, no formato custom do PostgreSQL.
- pdfs/, com todos os arquivos PDF do diretório informado.
- manifest.json com versão, data UTC, nomes relativos, tamanho e SHA-256 dos arquivos.

Ele também verifica o dump com pg_restore -l e verifica o ZIP antes de apresentá-lo como concluído. Não contém chaves JWT nem arquivo .env.

**Segurança:** o ZIP contém seus dados, os hashes das senhas dos usuários e os PDFs. Ele NÃO é criptografado internamente. Salve em unidade com criptografia e controle de acesso, preferencialmente com uma segunda cópia offline protegida. Nunca envie ZIPs reais ao GitHub ou a chats públicos.

## 3. Verificar integridade, sem alterar dados

~~~powershell
.\ops\verificar-backup.ps1 -Arquivo "C:\Backups\LivroStock\NOME_DO_ARQUIVO.zip"
~~~

O verificador recusa caminhos perigosos, entradas extras ou duplicadas, PDF alterado, tamanhos incompatíveis e SHA-256 diferente. Isso detecta corrupção, mas não comprova autenticidade contra alguém que possa adulterar o ZIP E o manifesto. Para saber se o banco é recuperável, realize a restauração isolada.

## 4. Restaurar somente em TESTE

~~~powershell
.\ops\restaurar-em-teste.ps1 -Arquivo "C:\Backups\LivroStock\NOME_DO_ARQUIVO.zip" -Destino "C:\Restauracoes\LivroStock" -ConfirmarCriacaoBancoTeste
~~~

Esse script:
1. Verifica a integridade do ZIP antes de extrair.
2. Cria pasta nova para PDFs e gera um banco chamado livrostock_restore_DATA_ID.
3. Confirma que o banco novo ainda não existe.
4. Executa createdb e pg_restore sem --clean, --create ou DROP DATABASE.
5. Confere as cinco tabelas esperadas da versão 0.5 e o total de PDFs extraídos.
6. Imprime o nome do banco e o caminho da pasta de recuperação.

O banco atual **livrostock** e a pasta ativa de PDFs continuam intocados. Se o teste falhar, o banco novo pode permanecer parcialmente restaurado para análise. Não altere nem apague o banco original para corrigir falhas de teste.

Depois, em terminal Java de TESTE, use DB_URL apontando para o banco livrostock_restore_... recém-criado, e PDF_STORAGE_DIR apontando para a pasta nova. Confira autenticação, acervo, estoque, PDFs e auditoria. A aplicação não é iniciada nem redirecionada automaticamente pelos scripts.

## Precauções e limites

- **Nenhum backup automático:** a rotina exige janela de manutenção sem escritas. Agendamento, retenção e limpeza ficam para uma evolução posterior.
- **Sem restauração sobre produção:** para recuperar o banco de uso real, é necessário procedimento revisado e aprovação específica.
- **Limite:** o Compress-Archive tem limite de aproximadamente 2 GB por arquivo, verificado preventivamente. Acervos maiores exigem outra estratégia de arquivamento.
- **Hashes não são assinaturas:** SHA-256 armazenado dentro do ZIP não autentica o autor.
- **Não publicar o Compose de desenvolvimento:** o Docker Compose atual ainda usa credenciais de desenvolvimento; restringir acesso externo antes de qualquer deploy.
- **Sem exclusão de volumes:** jamais execute docker compose down -v, flyway clean ou DROP DATABASE para realizar esses testes.

## CI e homologação local

A CI inclui:
- Java 21 e testes de integração com PostgreSQL/Flyway.
- Build React/TypeScript.
- Testes de ZIP em Windows PowerShell 5.1 e PowerShell 7.
- Backup e restauração de um PostgreSQL + PDF descartáveis em Docker.

O sucesso no GitHub Actions confirma os scripts no ambiente de CI, **não** demonstra que foram executados no seu Windows. A homologação local só estará concluída depois que seu próprio ZIP for verificado e restaurado em banco de TESTE, com conferência visual dos registros e dos arquivos recuperados.
