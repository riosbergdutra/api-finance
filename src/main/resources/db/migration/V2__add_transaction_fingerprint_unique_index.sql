-- A migration falha sem apagar dados se já houver fingerprints duplicados.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM transactions
        WHERE hash_deduplicacao IS NOT NULL
        GROUP BY hash_deduplicacao
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existem hash_deduplicacao duplicados em transactions; saneie-os antes de aplicar V2.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class idx
        JOIN pg_namespace ns ON ns.oid = idx.relnamespace
        JOIN pg_index i ON i.indexrelid = idx.oid
        WHERE ns.nspname = current_schema()
          AND idx.relname = 'idx_trx_hash'
          AND (
              NOT i.indisunique
              OR i.indnkeyatts <> 1
              OR pg_get_indexdef(idx.oid) NOT ILIKE '%(hash_deduplicacao)'
          )
    ) THEN
        RAISE EXCEPTION 'O índice idx_trx_hash já existe com definição incompatível.';
    END IF;
END $$;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_trx_hash
    ON transactions (hash_deduplicacao);
