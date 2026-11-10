# Contribuindo

Abra uma issue descrevendo o problema ou melhoria. Para mudanças de código:

1. Crie branch `feature/descricao-curta` ou `fix/descricao-curta`.
2. Adicione testes quando houver alteração de regra de negócio.
3. Execute `mvn clean test` em `backend` e `npm run build` em `frontend`.
4. Abra pull request com motivação, passos de teste e capturas de tela para mudanças de UI.

Padrão de commits sugerido: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`.

Não comite `.env`, segredos, credenciais ou dados reais de clientes.
