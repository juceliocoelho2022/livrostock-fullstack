# 📚 LivroStock — Gestão de Acervo e Estoque

![Java 21](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-green) ![React](https://img.shields.io/badge/React-18-blue) ![TypeScript](https://img.shields.io/badge/TypeScript-5-blue) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-blue) ![License](https://img.shields.io/badge/license-MIT-lightgrey)

**LivroStock** é uma aplicação full stack para cadastro de livros, controle de estoque por movimentações e consulta de indicadores de acervo. Projeto de portfólio construído com **Java 21 / Spring Boot**, **React / TypeScript**, **PostgreSQL**, **Flyway** e **Swagger**.

> Status: versão inicial funcional em desenvolvimento local. Não há autenticação implementada neste código. Antes de expor em produção, adicionar controle de acesso, gestão de segredos e hardening.

## Funcionalidades

- CRUD de livros com título, autor, ISBN, categoria, preço e estoque mínimo
- Entrada e saída de unidades com histórico de movimentações
- Bloqueio de saída maior que o saldo disponível
- Consulta de livros com paginação, busca e alerta de estoque baixo
- Painel React para cadastro, consulta e movimentações
- Documentação interativa via Swagger/OpenAPI
- Migrations Flyway e testes unitários de domínio

## Estrutura

```text
livrostock/
├── backend/              # API Spring Boot + JPA + Flyway + testes
│   ├── src/main/java/
│   ├── src/main/resources/db/migration/
│   ├── src/test/
│   └── pom.xml
├── frontend/             # React + TypeScript + Vite
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
├── docs/                 # arquitetura, contratos e roteiro
├── .github/workflows/    # CI Java e frontend
├── .gitignore
└── README.md
```

## Pré-requisitos

- JDK 21 e Maven 3.9+
- Node.js 20+ e npm
- PostgreSQL 16+ (local ou Docker)
- Git; Docker Desktop **opcional**

## Início rápido (Windows PowerShell)

### 1. Banco de dados

Se já existir o banco **livrostock** acessível em `localhost:5432`, **não inicie outro PostgreSQL**. Caso contrário:

```powershell
cd backend
docker compose up -d
```

A configuração de desenvolvimento usa `livrostock` como nome do banco, usuário e senha. **Altere as credenciais antes de publicar**. Para uma instância externa, configure `DB_URL`, `DB_USER` e `DB_PASSWORD`.

### 2. Backend

```powershell
cd backend
mvn clean test
mvn spring-boot:run
```

API por padrão: `http://localhost:8081` (8080 já estava ocupada por outro serviço Docker no ambiente de desenvolvimento). Override: variável de ambiente `PORT`.

- Swagger: http://localhost:8081/swagger-ui.html
- Health: http://localhost:8081/actuator/health
- Livros: http://localhost:8081/api/v1/livros

**Verifique sempre a rota de livros**: `health` com HTTP 200 não garante que o serviço respondendo seja o LivroStock. Se `GET /api/v1/livros` retornar 401, confira o processo/container responsável pela porta. O backend deste pacote não inclui Spring Security.

### 3. Frontend

Em outro terminal:

```powershell
cd frontend
npm install
Copy-Item .env.example .env
npm run dev
```

UI: http://localhost:5173. No desenvolvimento, `/api` e `/actuator` são encaminhados pelo Vite para `http://localhost:8081`. `VITE_API_URL` vazia = usa o proxy. Ajuste `API_PROXY_TARGET` em `frontend/.env` se necessário e reinicie o Vite.

### 4. Build

```powershell
cd backend
mvn clean test package
cd ../frontend
npm install  # gerar e versionar package-lock.json em seguida para builds reproduzíveis
npm run build
```

> Este pacote foi preparado a partir das versões iniciais do backend/frontend. O build do frontend foi validado pelo usuário na versão anterior; **esta versão integrada ainda precisa ser compilada/testada no seu computador ou no CI**.

## API REST

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/livros` | Cadastrar livro |
| `GET` | `/api/v1/livros` | Listar/buscar com paginação |
| `GET` | `/api/v1/livros/{id}` | Buscar por ID |
| `PUT` | `/api/v1/livros/{id}` | Atualizar dados |
| `DELETE` | `/api/v1/livros/{id}` | Desativar livro |
| `POST` | `/api/v1/livros/{id}/movimentacoes` | Entrada/saída |
| `GET` | `/api/v1/livros/{id}/movimentacoes` | Histórico |
| `GET` | `/api/v1/livros/estoque-baixo` | Alerta de reposição |

### Exemplo de cadastro

```json
{
  "titulo": "Clean Code",
  "autor": "Robert C. Martin",
  "isbn": "9780132350884",
  "categoria": "Tecnologia",
  "preco": 99.90,
  "estoqueMinimo": 5
}
```

### Exemplo de entrada

`POST /api/v1/livros/1/movimentacoes`

```json
{"tipo":"ENTRADA", "quantidade":20, "observacao":"Compra de livros"}
```

### Regras essenciais

1. Livro é cadastrado inicialmente com saldo zero.
2. Movimentações registram tipo, quantidade, saldo após e data.
3. Não é permitido saldo negativo.
4. Exclusão lógica preserva o histórico.

## Qualidade, segurança e roadmap

- **CI**: workflows para testes Java 21 e build React/TypeScript.
- **Testes**: JUnit 5 no domínio; expandir com integração PostgreSQL/Testcontainers.
- **Limitações atuais**: sem login/JWT, sem gestão de usuários, sem deploy e sem cadastro separado de fornecedores/editoras.
- **Próximas versões**: autenticação/autorização, relatórios, fornecedores, editoras, categorias, testes de integração e pipeline de deploy.

Consulte [docs/ARQUITETURA.md](docs/ARQUITETURA.md), [docs/API.md](docs/API.md) e [docs/ROADMAP.md](docs/ROADMAP.md).

## Licença

MIT — veja [LICENSE](LICENSE).
