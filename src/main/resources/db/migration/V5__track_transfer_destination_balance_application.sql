-- Distingue transferências históricas (que debitavam apenas a origem)
-- das novas transferências que também creditam a conta de destino.
-- DEFAULT FALSE preserva a reversão correta de lançamentos já existentes.
ALTER TABLE transactions
    ADD COLUMN transfer_destination_applied BOOLEAN NOT NULL DEFAULT FALSE;
