# Migração e índice único para idempotência de transações

O backend usa PostgreSQL `INSERT ... ON CONFLICT (hash_deduplicacao) DO NOTHING`.
O schema passa a ser versionado por migrations Flyway:

- `V1__create_initial_schema.sql`: cria as nove tabelas persistidas pelas entidades JPA.
- `V2__add_transaction_fingerprint_unique_index.sql`: valida duplicatas e cria
  `idx_trx_hash` como índice único concorrente.
- O arquivo `.sql.conf` da V2 define `executeInTransaction=false`, requisito
  para `CREATE INDEX CONCURRENTLY`.

## Banco novo

Em banco PostgreSQL vazio, Flyway deve executar V1 e V2 em sequência. O teste
`TransactionRepositoryPostgresTest` usa PostgreSQL 16 via Testcontainers,
Flyway habilitado e Hibernate `ddl-auto=validate` para verificar esse caminho.

## Banco existente: não habilitar automaticamente

**Não habilite Flyway em um banco existente sem backup e comparação prévia.**
A V1 cria o schema completo e não deve ser executada sobre tabelas que já existem.

Procedimento controlado para cada banco legado:

1. Faça backup verificável e confirme o banco/schema alvo.
2. Compare todas as tabelas, colunas, tipos, nulabilidade, constraints e índices
   existentes com V1 e com as entidades atuais. Investigue qualquer divergência.
3. Confirme que os dados existentes são compatíveis com o modelo atual.
4. Somente se o schema já corresponder à V1, registre a baseline Flyway na versão
   1 (a V1 será considerada já aplicada, sem recriar tabelas).
5. Aplique V2. Ela interrompe a migration se houver fingerprints duplicados ou
   se encontrar um índice `idx_trx_hash` com definição incompatível.
6. Verifique o histórico Flyway, o índice único e o funcionamento da aplicação.
7. Só então habilite Flyway e Hibernate `ddl-auto=validate` no perfil daquele
   ambiente.

Se o schema legado divergir, não force baseline: prepare uma migration de
reconciliação específica, preservando os dados. Nunca apague ou mescle
transações automaticamente; registros iguais podem representar lançamentos
financeiros legítimos.

## Pré-verificação manual de duplicatas

A V2 executa esta verificação e falha sem modificar os dados se houver colisões.
Ela também pode ser executada antecipadamente:

```sql
SELECT hash_deduplicacao, COUNT(*) AS quantidade
FROM transactions
WHERE hash_deduplicacao IS NOT NULL
GROUP BY hash_deduplicacao
HAVING COUNT(*) > 1;
```

## Verificação pós-implantação

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename = 'transactions'
  AND indexname = 'idx_trx_hash';
```

A aplicação não deve depender do `ON CONFLICT` até confirmar que o índice
único está válido. As propriedades Flyway permanecem desabilitadas nos perfis
de execução até a baseline do banco de cada ambiente ser revisada e aprovada.
