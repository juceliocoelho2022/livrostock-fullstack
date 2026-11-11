-- LivroStock v0.4: trilha de operações autenticadas
-- Migração aditiva: não altera livros, movimentacoes ou exemplares_digitais.
CREATE TABLE auditoria_eventos (
  id BIGSERIAL PRIMARY KEY,
  usuario VARCHAR(120) NOT NULL,
  acao VARCHAR(60) NOT NULL,
  metodo VARCHAR(10) NOT NULL,
  rota VARCHAR(255) NOT NULL,
  http_status INTEGER NOT NULL,
  criado_em TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auditoria_eventos_criado_em ON auditoria_eventos (criado_em DESC);
CREATE INDEX idx_auditoria_eventos_usuario ON auditoria_eventos (usuario, criado_em DESC);
