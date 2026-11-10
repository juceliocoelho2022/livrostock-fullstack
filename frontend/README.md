# LivroStock Frontend

Dashboard administrativo para a API de estoque LivroStock, construído em React 18, TypeScript e Vite.

## Pré-requisitos
- Node.js 20.19+ ou 22+
- Backend LivroStock API v0.1 em `http://localhost:8080`
- PostgreSQL funcionando e Spring Boot iniciado

## Executar no Windows PowerShell

```powershell
cd C:\Projetos\livrostock-frontend
npm install
npm run dev
```

Abra http://localhost:5173

O Vite encaminha as chamadas `/api` e `/actuator` para `http://localhost:8080` por proxy. **Não é necessário alterar CORS no backend para desenvolvimento usando esse proxy.** Se quiser chamar o backend diretamente de outra origem, configure CORS no Spring Boot.

## Funcionalidades
- Dashboard com títulos ativos, unidades, valor bruto do estoque e alertas
- Listagem, busca local e paginação da lista carregada (até 1000 itens)
- Cadastro, edição e desativação lógica de livros
- Entrada e saída de estoque com previsão do saldo e validação no backend
- Histórico de movimentações por livro (até 100 registros)
- Visualização dos livros em estoque baixo
- Avisos de erros REST e status de conectividade
- Layout responsivo e navegação lateral

## API e contrato
- `GET /api/v1/livros?page=0&size=1000`
- `POST /api/v1/livros`
- `PUT /api/v1/livros/{id}`
- `DELETE /api/v1/livros/{id}`
- `POST /api/v1/livros/{id}/movimentacoes`
- `GET /api/v1/livros/{id}/movimentacoes`

O campo ISBN não é alterável no backend após o cadastro. A propriedade `preco` é o preço registrado, **não** o custo de aquisição. O valor do dashboard é a soma de quantidade × preço cadastrado, não um indicador contábil de custo de estoque.

## Produção

```powershell
npm run build
npm run preview
```

O build gera `dist/`. Em implantação real, configure o proxy `/api` do seu servidor HTTP ou a variável `VITE_API_URL` no momento do build e CORS apropriado no backend.

## Limites da versão inicial
- Catálogo carregado em até 1000 livros. Para grandes acervos, migrar para paginação e busca no servidor.
- O histórico mostra até 100 registros por livro.
- Não possui login nem controle de acesso. **Não exponha o sistema publicamente sem autenticação e autorização.**
- Não contempla editoras, fornecedores e vendas, pois ainda não existem endpoints no backend v0.1.
