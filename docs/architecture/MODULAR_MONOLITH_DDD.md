# Arquitetura do FinanceFlow — Monólito Modular

## Decisão arquitetural

O FinanceFlow permanece como **uma única aplicação Spring Boot implantável**, com PostgreSQL e Redis. A organização será por módulos de negócio (bounded contexts), aplicando DDD pragmático, Clean Architecture, MVC na borda HTTP, SOLID e Repository Pattern.

Não haverá extração para microserviços nesta migração.

## Regras permanentes

1. Cada módulo é dono das suas regras, modelos e persistência.
2. Controllers pertencem à camada de apresentação e delegam casos de uso; não acessam repositories.
3. Repositories são detalhes de persistência. Um módulo não injeta o repository privado de outro módulo.
4. A comunicação entre módulos ocorre por contratos públicos de aplicação (ports/facades) ou eventos explícitos.
5. Um módulo não importa controllers, DTOs HTTP ou entidades JPA internas de outro módulo.
6. Dependências de domínio não apontam para Spring, JPA, Redis, Stripe, Keycloak ou WebSocket.
7. Application Services coordenam casos de uso e limites transacionais.
8. Entidades JPA e modelos de domínio só serão separados quando isso reduzir acoplamento ou proteger regras relevantes; não haverá duplicação mecânica.
9. Eventos usados para efeitos após commit devem declarar claramente a semântica transacional.
10. Migrações de estrutura não alteram endpoints, tabelas, dados, nomes de colunas ou comportamento sem uma mudança funcional deliberada e testada.

## Estrutura-alvo

Cada módulo terá somente as camadas necessárias:

```text
com.api.finance
├── shared/                 # tipos realmente transversais, sem regras de negócio dos módulos
├── user/
│   ├── presentation/       # controllers e DTOs HTTP
│   ├── application/        # casos de uso e contratos públicos
│   ├── domain/             # regras e modelos de negócio
│   └── infrastructure/     # JPA, Redis ou integrações próprias
├── account/
├── transaction/
├── category/
├── budget/
├── goal/
├── dashboard/
├── notification/
├── subscription/
├── bff/
└── ...
```

A estrutura é uma direção, não uma exigência de criar pastas vazias. MVC continua na apresentação: Controller -> Application Service -> Domain/Ports. A implementação de persistência fica em infrastructure.

## Inventário técnico inicial (branch hardening/production-readiness)

### Plataforma e integrações confirmadas

- Java 21 e Spring Boot 3.5.12.
- Spring MVC, Spring Security, OAuth2 Client e Resource Server.
- PostgreSQL com Spring Data JPA e Flyway.
- Redis com Spring Data Redis.
- WebSocket para notificações em tempo real.
- Stripe Java; a implementação inspecionada falha fechada fora do Test Mode.
- Spring Mail, Thymeleaf, geração de documentos com iText/Apache POI.
- Actuator, Micrometer/Prometheus e Resilience4j.
- Testes com Spring Boot Test, Security Test, H2 e Testcontainers PostgreSQL.

### Módulos e responsabilidades observados

- **user:** perfil e identidade interna associada ao Keycloak.
- **account:** contas, saldos, moeda, soft-delete e isolamento por userId.
- **transaction:** receitas/despesas/transferências, deduplicação SHA-256, paginação e limites do plano.
- **category:** categorias do usuário/sistema e regras de categorização por estabelecimento.
- **budget:** orçamentos e cálculo de despesas a partir de transações.
- **goal:** metas financeiras, depósitos/saques e eventos de conclusão.
- **dashboard:** composição de dados de contas, transações, notificações e metas.
- **notification:** persistência e push WebSocket; já contém alguns ports de saída.
- **subscription:** planos FREE/PRO, limites, expiração e integração Stripe.
- **bff:** sessão opaca HttpOnly, tokens no Redis, OAuth/Keycloak, refresh, logout e exclusão de conta.

### Acoplamentos encontrados nos arquivos inspecionados

- `TransactionService` injeta repositories de transaction, account, category e user; também depende de CategoryService, SubscriptionService e BudgetService. Importa entidades Account, Category e User.
- `BudgetService` injeta CategoryRepository, TransactionRepository e UserRepository.
- `DashboardService` consulta diretamente AccountRepository, TransactionRepository, NotificationRepository, GoalRepository e UserRepository.
- `AccountService` depende de UserRepository e SubscriptionService.
- `CategoryService` depende de UserRepository e de seus repositories locais.
- `GoalService` depende de UserRepository e usa User para publicar eventos.
- `SubscriptionService` depende de UserRepository, NotificationService e StripeService.
- `bff.AuthService` depende diretamente de UserRepository e User para provisionamento e exclusão de conta.
- `Transaction` mantém associação JPA direta com `category.model.Category`, cruzando a fronteira de persistência entre módulos.
- `notification` já possui `UserIdentityPort` e `NotificationPushPort`, com adaptador para UserRepository; é uma referência útil, mas não representa ainda uma migração completa de todos os módulos.

Esses pontos demonstram que a principal dívida arquitetural não é a ausência de nomes de pastas, mas a comunicação direta entre serviços e repositories/entidades de outros contextos.

## Sequência segura de migração

1. Mapear controllers, services, repositories, entidades, eventos, schedulers, listeners, integrações e testes de todos os módulos.
2. Definir contratos públicos por contexto e identificar quais operações precisam ser síncronas e quais podem ser eventos.
3. Migrar primeiro consultas de identidade de usuário para um contrato de aplicação, sem expor User/JPA a outros módulos.
4. Migrar transação/account/category/budget/subscription com contratos explícitos, preservando transações ACID onde a operação exige consistência imediata.
5. Desacoplar Dashboard por consultas de aplicação específicas, evitando que ele se torne um supermódulo com acesso irrestrito a repositories.
6. Preservar eventos de notificação e garantir quando devem ocorrer após commit.
7. Mover pacotes gradualmente, mantendo controllers/DTOs e endpoints compatíveis.
8. Ampliar testes ArchUnit para impedir dependências cruzadas de repositories e entidades JPA.
9. Executar `./mvnw --batch-mode --no-transfer-progress verify` e validar PostgreSQL/Flyway, Redis/BFF, Keycloak, WebSocket e Stripe Test Mode.

## Testes arquiteturais adicionados

`ModularArchitectureTest` começa com regras compatíveis com a estrutura atual:

- controllers/presentation não podem acessar repositories diretamente;
- pacotes `domain` não podem depender de frameworks, persistência ou apresentação;
- módulos não podem depender da camada de apresentação de outros módulos.

As regras de fronteira entre repositories de módulos serão ativadas conforme os acessos cruzados forem removidos; ativá-las antes disso quebraria a branch sem entregar uma migração funcional.

## Limite desta primeira análise

Este documento registra o inventário dos arquivos e fluxos que foram efetivamente inspecionados. Não afirma que todos os arquivos, controllers, schedulers, listeners e testes do repositório foram auditados. Antes de mover pacotes em larga escala, a listagem completa da árvore de código e a matriz de dependências devem ser concluídas. Nenhum schema, migration ou endpoint deve ser alterado por suposição.
