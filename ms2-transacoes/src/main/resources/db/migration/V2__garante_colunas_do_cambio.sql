-- Banco criado antes do câmbio (pedido 3) pelo antigo ddl-auto: update não tem as colunas da cotação.
-- Ele recebe o baseline da V1 (que não roda), então esta V2 garante as colunas e o índice.
-- IF NOT EXISTS: num banco criado pela V1, nada muda.
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS cotacao_usd NUMERIC(12, 6);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS data_cotacao_usd DATE;
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS cotacao_eur NUMERIC(12, 6);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS data_cotacao_eur DATE;

CREATE INDEX IF NOT EXISTS idx_transacoes_usuario_data ON transacoes (usuario_id, criada_em);
