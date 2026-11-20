# Migração e índice único para idempotência de transações

O backend usa PostgreSQL `INSERT ... ON CONFLICT (hash_deduplicacao) DO NOTHING`.
O schema é versionado por migrations Flyway:

- `V1__create_initial_schema.sql`: cria as nove tabelas persistidas pelas entidades JPA.
- `V2__add_transaction_fingerprint_unique_index.sql`: valida duplicatas e cria
  `idx_trx_hash` como índice único. A migration é transacional e funciona também
  em banco novo; em banco legado grande, o índice pode ser criado previamente
  com `CONCURRENTLY` conforme o procedimento abaixo, antes de aplicar V2.

## Banco novo

Em banco PostgreSQL vazio, Flyway executa V1 e V2 em sequência. O teste
`TransactionRepositoryPostgresTest` usa PostgreSQL 16 via Testcontainers,
Flyway habilitado e Hibernate `ddl-auto=validate` para verificar esse caminho.

Os perfis `dev` e `prod` habilitam Flyway e usam Hibernate `ddl-auto=validate`.
O perfil padrão mantém Flyway desabilitado; os ambientes devem selecionar
explicitamente um perfil apropriado.

## Banco existente: baseline obrigatória e controlada

**Não inicie a aplicação com o perfil `dev` ou `prod` em um banco existente
sem backup e comparação prévia.** A V1 cria o schema completo e não deve ser
executada sobre tabelas que já existem.

Procedimento controlado para cada banco legado:

1. Faça backup verificável e confirme o banco/schema alvo.
2. Compare todas as tabelas, colunas, tipos, nulabilidade, constraints e índices
   existentes com V1 e com as entidades atuais. Investigue qualquer divergência.
3. Confirme que os dados existentes são compatíveis com o modelo atual.
4. Somente se o schema já corresponder à V1, registre explicitamente a baseline
   Flyway na versão 1 (a V1 será considerada já aplicada, sem recriar tabelas).
   Não configure `baseline-on-migrate=true` permanentemente.
5. Verifique duplicatas. Para tabela grande, crie o índice com `CONCURRENTLY`
   antes de aplicar V2, fora de uma transação explícita. A V2 valida a definição
   e não recria um índice correto já existente. Para tabela pequena, deixe V2
   criá-lo normalmente.
6. Aplique V2. Ela interrompe a migration se houver fingerprints duplicados ou
   se encontrar um índice `idx_trx_hash` com definição incompatível.
7. Verifique o histórico Flyway, o índice único e o funcionamento da aplicação.
8. Só então inicie a aplicação com o perfil daquele ambiente.

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
único está válido. Em banco legado, faça baseline explicitamente antes de
habilitar o perfil `dev` ou `prod`; não use baseline automática global.
