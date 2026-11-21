# Execução local com Docker Compose

Este Compose é um ambiente **local de desenvolvimento**, não um manifesto de produção pública.

## Serviços

- PostgreSQL 16 (postgres:5432)
- Redis 7 (redis:6379)
- Keycloak 24 (localhost:8180)
- Backend Spring Boot (localhost:8080)
- Nginx (localhost:80)
- Prometheus (localhost:9090)
- Grafana (localhost:3000)

O Nginx e o Prometheus só iniciam depois que o healthcheck de readiness do backend passar. O backend aguarda PostgreSQL e Redis saudáveis.

## Inicialização segura

Antes de iniciar o backend pela primeira vez em uma instalação que já tenha dados, faça backup do PostgreSQL e confira public.flyway_schema_history. O perfil dev habilita Flyway; a inicialização pode aplicar migrations pendentes V1–V9. Não use um banco com dados importantes sem backup verificado e ensaio de restauração.

```bash
docker compose config --quiet
docker compose build finance-app
docker compose up -d
docker compose ps
```

Para acompanhar a aplicação:

```bash
docker compose logs -f finance-app
```

A imagem final executa como usuário não-root. O healthcheck usa /actuator/health/readiness; Prometheus coleta /actuator/prometheus diretamente pela rede interna. O Nginx não expõe publicamente os demais endpoints Actuator. /nginx_status é limitado à rede Docker privada.

## Keycloak e BFF

O navegador utiliza o issuer público http://localhost:8180/realms/bff-codeflow. O backend usa o hostname Docker keycloak:8080 para token, user-info e JWKs, mantendo a validação do claim iss contra o issuer público. O Keycloak está configurado com hostname público e backchannel dinâmico para esse cenário local.

O realm bff-codeflow e o cliente confidencial bff-codeflow precisam existir no Keycloak persistente. Configure o redirect URI do cliente para http://localhost:8080/auth/callback e o Web Origin para http://localhost:4200. O segredo deve corresponder ao segredo configurado no cliente; o fallback do Compose é somente um placeholder de desenvolvimento.

## Limites de segurança

O Compose usa start-dev, credenciais locais de conveniência, HTTP e Stripe Test Mode. Não publique esta configuração na internet. Antes de produção, use Keycloak em modo produção, TLS, secrets manager, Redis com TLS, credenciais distintas, PostgreSQL isolado para Keycloak, backups testados, rede privada e configuração de audience/admin client descrita no runbook de produção.

O Redis atual não recebe volume novo nesta alteração para não provocar descarte das sessões que já estejam na instância local ao recriar o container. Portanto, sessões Redis continuam sujeitas à perda se o container Redis for removido/recriado; planeje persistência com migração/backup separado.
