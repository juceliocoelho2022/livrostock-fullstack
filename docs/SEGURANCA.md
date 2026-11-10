# Segurança da Biblioteca Digital — implementação experimental v0.3

> Branch `feature/v0.3-pdf-security`. A v0.2 continua funcionando sem login no ambiente local. Este perfil representa uma primeira etapa de autenticação, **não uma homologação de segurança para produção**.

## Escopo entregue

- Spring Security + JWT assinado com HMAC-SHA256, validade de **30 minutos**
- Perfis em memória: `BIBLIOTECARIO` e `LEITOR`
- Login: `POST /api/v1/auth/login` somente no profile Spring `secure`
- Detecção de modo: `GET /api/v1/auth/mode` somente no profile `secure`
- Upload PDF: somente `BIBLIOTECARIO`
- Lista, leitura e download de PDF: `BIBLIOTECARIO` ou `LEITOR`
- React usa token Bearer apenas na memória da aba para chamar a API e baixar PDFs como Blob. **O token não é enviado pela URL, nem salvo no localStorage.**
- `401` para ausência/token inválido e `403` para autorização insuficiente.
- `/actuator/health` permanece público.
- Demais endpoints de livros/estoque ainda têm comportamento v0.2: **não estão protegidos por JWT**.

### Ativação local — PowerShell

**Não substitua o `application.yml` nem o PostgreSQL existente.** Abra o terminal em `backend` e use as variáveis de conexão que já funcionam no seu Windows. Depois, configure o perfil seguro:

```powershell
# Na mesma janela em que você iniciará o backend:
$env:SPRING_PROFILES_ACTIVE = "secure"

# Segredo efêmero, forte e aleatório para os tokens JWT
$segredo = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
$env:LIVROSTOCK_JWT_SECRET = [Convert]::ToBase64String($segredo)

# Cadastre duas senhas diferentes, cada uma com pelo menos 12 caracteres
$senhaB = Read-Host "Senha do perfil BIBLIOTECARIO" -AsSecureString
$env:LIVROSTOCK_LIBRARIAN_PASSWORD = [System.Net.NetworkCredential]::new("", $senhaB).Password
$senhaL = Read-Host "Senha do perfil LEITOR" -AsSecureString
$env:LIVROSTOCK_READER_PASSWORD = [System.Net.NetworkCredential]::new("", $senhaL).Password

# No mesmo terminal, após definir DB_URL / DB_USER / DB_PASSWORD
mvn spring-boot:run
```

Usuários de desenvolvimento (alteráveis por `LIVROSTOCK_LIBRARIAN_USER` e `LIVROSTOCK_READER_USER`):
- `bibliotecario`: upload, listagem, leitura e download.
- `leitor`: listagem, leitura e download; **não pode enviar PDF**.

Se o modo seguro não estiver ativado, o `GET /api/v1/auth/mode` responde 404 e o frontend funciona no modo local antigo, **sem autenticação**. Não exponha esse modo à internet.

### Teste de login (ambiente local)

```powershell
# Endpoint deve retornar enabled=true
Invoke-RestMethod http://localhost:8081/api/v1/auth/mode

# Na UI React, abra Biblioteca Digital e informe usuário/senha definidos no terminal.
# O token é armazenado apenas em memória; recarregar a aba exige novo login.
```

### Matriz de autorização

| Recurso | Sem token | LEITOR | BIBLIOTECARIO |
| --- | --- | --- | --- |
| Upload de PDF | 401 | 403 | 201, se válido |
| Lista de PDFs | 401 | 200 | 200 |
| Leitura/download de PDF | 401 | 200 | 200 |
| Health | 200 | 200 | 200 |

## Limites antes de produção

- **Toda a API de livros e estoque ainda precisa de controle de acesso**, inclusive criar, editar e desativar.
- Usuários em memória não são persistidos; falta cadastro de usuários, recuperação de senha, revogação e rotação segura de chaves.
- JWT tem duração limitada, mas não possui refresh token nem lista de revogação.
- Não há rate limiting de login, trilha de auditoria por usuário nem bloqueio progressivo.
- A validação de PDF por extensão/cabeçalho não equivale a antimalware, CDR ou aprovação de direitos autorais.
- HTTPS, gestão de segredos, storage privado, backup e política de licenças são obrigatórios antes de disponibilizar acervo fora de ambiente local.
- O uso de Blob no React carrega o PDF completo na memória do navegador; downloads de arquivos grandes exigem estratégia de streaming.
- Separação de ambiente local e de produção, CORS restrito, testes de penetração e políticas de monitoramento permanecem pendentes.

## Testes

```powershell
cd backend
mvn clean verify

cd ../frontend
npm ci
npm run build
```

A suíte de segurança usa `@WebMvcTest` com perfis simulados. Para homologação, testar o ambiente local com PDF autorizado, verificar 401/403 e os caminhos de Leitura/Download no React.

Veja [regras de negócio](REGRAS_DE_NEGOCIO.md) e [arquitetura/DER](ARQUITETURA.md).
