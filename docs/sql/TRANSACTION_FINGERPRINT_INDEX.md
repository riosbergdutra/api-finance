# Índice único para idempotência de transações

O backend usa PostgreSQL `INSERT ... ON CONFLICT (hash_deduplicacao) DO NOTHING`.
A entidade JPA declara o índice, mas `ddl-auto=none` e Flyway desabilitado
significam que essa anotação **não cria nem atualiza o índice no banco existente**.

## Pré-verificação

Execute no banco correto, com um usuário que tenha permissão de leitura:

```sql
SELECT hash_deduplicacao, COUNT(*) AS quantidade
FROM transactions
WHERE hash_deduplicacao IS NOT NULL
GROUP BY hash_deduplicacao
HAVING COUNT(*) > 1;
```

Se retornar linhas, interrompa a implantação e investigue os registros antes de
criar o índice. Não apague nem mescle transações automaticamente: elas podem
representar lançamentos financeiros legítimos.

## Criar o índice

Faça backup e execute em uma janela operacional adequada, conectado ao banco
de produção/staging correto:

```sql
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_trx_hash
    ON transactions (hash_deduplicacao);
```

O comando `CONCURRENTLY` não pode ser executado dentro de uma transação explícita.
Se falhar, verifique e remova somente um índice inválido após confirmar seu estado,
e então repita a operação.

## Verificação pós-implantação

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename = 'transactions'
  AND indexname = 'idx_trx_hash';
```

A implantação da aplicação que depende de `ON CONFLICT` só deve ocorrer depois
de confirmar que o índice único está válido. Esta instrução é deliberadamente
manual: o repositório ainda não possui uma baseline de migrations para criar
com segurança todo o schema em bancos novos ou existentes. Não habilite Flyway
globalmente até definir e testar essa estratégia de baseline.
