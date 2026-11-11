# LivroStock v1.0 — Observabilidade e Painel de Integridade

## Escopo entregue
- Spring Boot Actuator com endpoints `health`, `info`, `metrics` e `prometheus`.
- Registro Micrometer Prometheus para exportação de métricas HTTP/JVM e métricas próprias.
- `livrostock_pdf_integridade_verificacoes_total` com rótulo `estado` para auditorias acionadas sob demanda.
- Autorização JWT: rotas de métricas e painel de integridade apenas para `BIBLIOTECARIO`.
- Painel React/TypeScript dentro da Biblioteca Digital: botão `Verificar agora`, resultados paginados, contagem por página de OK e problemas, leitura sem alteração de PDFs.
- Teste de integração no PostgreSQL 16: Prometheus `401` sem token, `403` para LEITOR, `200` para bibliotecário e presença do contador de auditorias.

## Segurança
Não há servidor Prometheus público ou Grafana em execução nesta versão. O endpoint `/actuator/prometheus` contém métricas operacionais e **não é público**; exige JWT de bibliotecário em `secure`. Não exponha o perfil `default` à Internet.

## Preparar o ambiente local
Siga o README do repositório. No Windows homologado:
- PostgreSQL: `127.0.0.1:5434/livrostock` em contêiner `livrostock-api-postgres-1`.
- Backend: `http://localhost:8081`, perfil `secure`, variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`, `LIVROSTOCK_JWT_SECRET` (mínimo 32 bytes), `PDF_STORAGE_DIR`.
- Frontend Vite: `http://localhost:5173`.
- Backup original já realizado: banco + 10 PDFs, com 9 PDFs digitais ativos íntegros na v0.9.

## Verificação automatizada em PowerShell

Na raiz do repositório, com backend `secure` iniciado e PostgreSQL existente preservado:

```powershell
.\ops\homologar-v1.0.ps1
```

O script solicita a senha de `bibliotecario` apenas no console e não publica tokens JWT. Confere health, segurança, paginação completa, estados SHA-256, contador Prometheus e acesso a `/actuator/metrics`. Não altera registros nem arquivos. Uma falha deve ser investigada, não corrigida modificando PDFs reais.

## Homologação manual
1. Confirme `GET /actuator/health` = `UP` e `GET /api/v1/auth/mode` = `jwt`.
2. Faça login como `bibliotecario` no frontend.
3. Abra **Biblioteca Digital** e encontre a seção **Integridade dos PDFs**.
4. Clique **Verificar agora**. Para o acervo real homologado na v0.9 são esperados **9 OK / 0 problemas**, mas valide novamente após atualizar.
5. Confira se o perfil **LEITOR** consegue ler PDFs, mas não vê o painel nem consegue consultar o endpoint administrativo.
6. Opcionalmente, com um token JWT válido em memória local, verifique `GET /actuator/prometheus` e `GET /actuator/metrics`. Não imprima tokens em logs ou tickets.

## Diagnóstico do HTTP 403 no Prometheus (Windows)

A autenticação administrativa na rota `/api/v1/acervo-digital/integridade` pode funcionar em uma instância **v0.9**, mesmo que o endpoint Prometheus da **v1.0** responda `403`. Isso **não comprova** que a v1.0 está em execução. Verifique versão local, processo e exposição de endpoints antes de alterar regras de segurança.

Na raiz do repositório:

```powershell
git branch --show-current
git log -1 --oneline
git status --short
Select-String -Path backend/src/main/resources/application.yml -Pattern 'include:'
Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

A versão v1.0 contém `management.endpoints.web.exposure.include: health,info,metrics,prometheus`. Se a configuração local for diferente, preserve seu `application.yml` e configure a exposição necessária via `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` no terminal da API. Verifique a dependência `micrometer-registry-prometheus` no `backend/pom.xml`.

Pare **somente** a aplicação Java com `Ctrl+C` no terminal original; não pare nem recrie o contêiner PostgreSQL. Reinicie o backend a partir da branch v1.0 atualizada, no **mesmo terminal que contém DB_URL, DB_USER, DB_PASSWORD, PDF_STORAGE_DIR, SPRING_PROFILES_ACTIVE=secure e LIVROSTOCK_JWT_SECRET**. Em outro PowerShell execute `.\ops\homologar-v1.0.ps1`.

Se o `403` persistir após confirmar branch, processo e configurações, investigue a cadeia de filtros Spring Security no processo em execução; **não** substitua `hasRole("BIBLIOTECARIO")` por `permitAll()` e **não** exponha métricas administrativas sem autenticação.

## Limites
- O painel mostra apenas a página conferida, não afirma ter auditado todo o acervo se houver múltiplas páginas.
- Hash SHA-256 não é antivírus e não protege a janela entre verificação e entrega.
- Contador Micrometer é em memória e zera quando o processo reinicia.
- Não há scraper Prometheus, retenção, alertmanager, Grafana, tracing distribuído nem deploy público.
- Não contém migrations, mudanças de dados ou alterações nos PDFs reais.

## Próximos passos
Hardening de deploy, armazenamento versionado, política de métricas, testes de carga, configuração HTTPS e plano de alertas. Não habilitar acessos públicos a endpoints administrativos.
