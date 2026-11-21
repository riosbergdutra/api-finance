# Arquitetura DDD — FinanceFlow

## Objetivo

Evoluir o backend para Domain-Driven Design de forma incremental, preservando os contratos HTTP atuais e evitando uma reestruturação em massa que introduza regressões. Os pacotes de domínio existentes já representam módulos funcionais; esta convenção formaliza seus limites e orienta novas alterações.

## Bounded contexts

| Contexto | Responsabilidade | Agregados / conceitos centrais |
|---|---|---|
| Identity & Access | Identidade externa, autenticação BFF, sessão e autorização | User, AuthSession, OAuthLoginAttempt |
| Personal Finance | Lançamentos e visão financeira | Transaction |
| Accounts | Contas financeiras e saldos | Account |
| Planning | Orçamentos e acompanhamento de limites | Budget |
| Goals | Metas e progresso | Goal |
| Categorization | Categorias e regras de classificação | Category, MerchantRule |
| Engagement | Notificações e preferências | Notification |
| Subscription & Billing | Plano, assinatura e integração Stripe em modo de teste | Subscription, StripeEvent |

Os contextos podem compartilhar identificadores e contratos, mas não devem acessar diretamente repositórios ou entidades internas uns dos outros. Integrações entre contextos devem ocorrer por serviços de aplicação, interfaces explícitas ou eventos de domínio/aplicação.

## Organização por contexto

Cada módulo deve evoluir para a seguinte estrutura, sem mover tudo de uma vez:

```text
<context>/
  domain/
    model/           # entidades, value objects e invariantes
    event/           # eventos de domínio
    repository/      # interfaces de repositório
    service/         # serviços de domínio puros, quando necessários
  application/
    usecase/         # casos de uso e orquestração
    port/            # portas de entrada/saída
    dto/             # comandos e resultados internos
  infrastructure/
    persistence/     # implementações JPA/Redis
    integration/     # Keycloak, Stripe e outros serviços externos
  presentation/
    http/            # controllers, requests e responses
```

A estrutura atual `controller/service/repository/model` pode permanecer durante a migração. Novos casos de uso devem respeitar as responsabilidades acima; a migração de classes existentes deve ser feita por contexto, acompanhada de testes e sem alterar os endpoints públicos sem necessidade.

## Regras de dependência

1. O domínio não depende de Spring, JPA, Redis, Stripe, Keycloak ou HTTP.
2. A camada de aplicação coordena casos de uso e depende de abstrações.
3. A infraestrutura implementa portas e contém detalhes de tecnologia.
4. A apresentação valida entrada HTTP e converte comandos/resultados; não contém regras de negócio.
5. Entidades de domínio protegem invariantes. Não expor entidades JPA diretamente na API.
6. Value objects devem representar conceitos com validação própria (por exemplo, Money, Email e identificadores), quando isso reduzir duplicação e ambiguidade.
7. Eventos devem representar fatos já ocorridos; não usar eventos como chamadas síncronas disfarçadas.
8. Não criar abstrações genéricas sem necessidade real do domínio.

## Identidade e autenticação BFF

O contexto Identity & Access é responsável pelo fluxo OAuth2. O navegador não recebe refresh token nem client secret. O backend mantém o verifier PKCE e os dados temporários do fluxo, valida state de uso único e realiza a troca do authorization code. A sessão permanece gerenciada pelo BFF.

## Estratégia de migração

1. Estabilizar CI e testes existentes.
2. Corrigir o fluxo OAuth2 no contexto Identity & Access.
3. Extrair casos de uso de um contexto por vez, começando pelos módulos com maior mudança funcional.
4. Introduzir value objects e invariantes somente onde houver regra de negócio clara.
5. Adicionar testes de domínio sem infraestrutura e testes de integração nas fronteiras.
6. Remover dependências cruzadas e classes antigas apenas depois de confirmar ausência de consumidores.

## Critérios de aceite

- Regras de negócio testáveis sem subir Spring ou banco.
- Nenhum contexto acessa repositório de outro contexto diretamente.
- Controllers sem lógica de negócio.
- Integrações externas isoladas em infraestrutura.
- Contratos e permissões entre contextos documentados.
- CI verde antes de considerar cada etapa concluída.
