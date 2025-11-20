# FiadoPay - Sistema de Pagamentos

Refatoração realizada por Rafael Brito e Arthur Lobo para a disciplina de Programação Orientada a Objetos Avançada na UCSAL.

## Sobre este trabalho

Este projeto é uma refatoração de uma API de gateway de pagamentos já existente. A gente não criou o código do zero. O que fizemos foi pegar um sistema que já funcionava e aplicar melhorias focadas em arquitetura, documentação e boas práticas de engenharia de software.

A API original já tinha a estrutura básica: controllers, services, repositories. O que a gente fez foi reorganizar, documentar melhor, e garantir que conceitos avançados como reflexão, concorrência e SOLID estivessem bem aplicados e explicados.

## O que é o FiadoPay

O FiadoPay simula um gateway de pagamentos. Ele aceita pagamentos com cartão, PIX e boleto, calcula juros para parcelamento, valida fraudes e envia notificações via webhook.

Esse tipo de sistema envolve complexidade real. Tem validação de fraude, cálculo de juros, webhooks, tudo acontecendo de forma assíncrona. É um domínio perfeito para aplicar conceitos avançados de programação orientada a objetos.

---

## O que mudou: versão original vs refatorada

### Estrutura de pacotes

**Antes (projeto original):**
- 1 service gigante (PaymentService) com mais de 500 linhas
- Tudo misturado: autenticação, cálculo de juros, webhooks, retry, processamento
- Sem anotações customizadas
- Sem organização clara

**Depois (projeto refatorado):**
- 8 services separados, cada um com uma responsabilidade
- 3 anotações customizadas criadas (@AntiFraud, @PaymentMethod, @WebhookSink)
- Pacotes organizados (antifraud, webhook)
- Interfaces claras (FraudValidator, InterestCalculator, PaymentProcessor)

### O que separamos do service original

O PaymentService original fazia TUDO. A gente dividiu em:

1. **AuthenticationService** - só autenticação
2. **PaymentService** - só orquestração de pagamentos
3. **SimulatedPaymentProcessor** - só processamento assíncrono
4. **InterestCalculationService** - só cálculo de juros
5. **WebhookDeliveryService** - só entrega de webhooks
6. **WebhookSignatureService** - só geração de HMAC
7. **WebhookEventDispatcher** - só disparo de eventos
8. **AntiFraudService** - só validação de fraude
9. **PaymentMapper** - só conversão de entidades para DTOs

Cada um faz UMA COISA. É o princípio da responsabilidade única (SRP) aplicado.

---

## Decisões de design

### 1. Processamento assíncrono

**O que é:** Quando você cria um pagamento, o sistema responde na hora com status PENDING. O processamento real (que demora 2 segundos) acontece em outra thread.

**Por que:** Gateways de pagamento reais podem demorar segundos para processar. Se o cliente HTTP ficasse esperando, a experiência seria ruim. Com thread separada, resposta é imediata.

**Como funciona:**
- Cliente faz POST /payments
- Sistema salva no banco com status PENDING
- Sistema responde 201 CREATED imediatamente
- Outra thread processa em background
- Cliente pode consultar GET /payments/{id} depois para ver o resultado

### 2. Validação de fraude extensível

**O que é:** Sistema descobre automaticamente validadores de fraude usando reflexão.

**Por que:** Se quiser adicionar nova validação (exemplo: bloquear países), só cria uma classe nova. Não precisa modificar código existente.

**Como adicionar novo validador:**
```java
@Service
@AntiFraud(name = "MeuValidador", threshold = 1000.0, riskLevel = HIGH)
public class MeuValidador implements FraudValidator {
    public ValidationResult validate(Payment payment) {
        // Sua lógica aqui
    }
}
```
Pronto. O sistema descobre sozinho na inicialização.

### 3. Cada classe tem uma responsabilidade

**Antes:**
- PaymentService calculava juros, autenticava, processava, enviava webhook, tudo junto

**Depois:**
- PaymentService só orquestra (chama outros services)
- InterestCalculationService só calcula juros
- AuthenticationService só autentica
- WebhookDeliveryService só envia webhooks

É mais fácil de entender, testar e modificar.

### 4. Webhooks isolados em thread pool separado

**O que é:** Webhooks rodam em 3 threads separadas. Processamento de pagamentos roda em 5-10 threads separadas.

**Por que:** Se o servidor do cliente estiver fora (webhook não responde), as 3 threads de webhook travam esperando timeout. MAS as threads de pagamento continuam funcionando normalmente. Sistema não trava completo.

**Se estivesse tudo junto:** Um webhook travado bloquearia processamento de pagamentos. Sistema todo ficaria lento ou travado.

---

## Anotações customizadas criadas

### 1. @AntiFraud - Para validadores de fraude

Marca uma classe como validador de fraude. Sistema descobre automaticamente.

**Metadados:**
- `name` - Nome do validador
- `threshold` - Limite numérico (ex: 5000.0 para valores acima de R$ 5mil)
- `riskLevel` - HIGH, MEDIUM ou LOW (define ordem de execução)
- `enabled` - true/false para desabilitar sem deletar código

**Exemplo:**
```java
@Service
@AntiFraud(name = "HighAmountValidator", threshold = 5000.0, riskLevel = HIGH)
public class HighAmountFraudValidator implements FraudValidator {
    // Valida se pagamento é acima de 5 mil
}
```

### 2. @PaymentMethod - Para estratégias de pagamento

Marca estratégias de pagamento (ainda não utilizada completamente).

**Metadados:**
- `type` - CARD, PIX, BOLETO, DEBIT
- `requiresInterest` - true/false se precisa calcular juros
- `priority` - número (menor = maior prioridade)

### 3. @WebhookSink - Para listeners de eventos

Marca métodos que devem ser chamados quando eventos acontecem.

**Metadados:**
- `eventType` - "payment.updated", "payment.refunded", etc
- `async` - true/false para executar em thread separada
- `order` - número (ordem de execução)

**Exemplo:**
```java
@Service
public class PaymentWebhookListener {

    @WebhookSink(eventType = "payment.updated", async = true, order = 1)
    public void onPaymentUpdated(Payment payment) {
        // Esse método é chamado automaticamente quando pagamento atualiza
    }
}
```

---

## Reflexão

### Descoberta de validadores de fraude

**No startup da aplicação:**
1. Sistema procura todos os beans que implementam `FraudValidator`
2. Para cada um, verifica se tem anotação `@AntiFraud`
3. Se tiver, lê os metadados (name, threshold, riskLevel)
4. Guarda numa lista ordenada por riskLevel (HIGH primeiro)

**Durante execução (cada pagamento):**
- Percorre a lista de validadores
- Chama `validate(payment)` em cada um
- Não usa reflexão aqui, só polimorfismo (mais rápido)

### Descoberta de webhook listeners

**No startup da aplicação:**
1. Sistema procura todos os beans
2. Para cada bean, examina todos os métodos
3. Se método tem `@WebhookSink`, guarda referência

**Durante execução (quando evento acontece):**
- Filtra listeners por tipo de evento
- Usa `method.invoke()` para chamar método dinamicamente
- Aqui SIM usa reflexão (única forma de chamar método descoberto em runtime)

**Por que reflexão é importante:** Permite adicionar novos validadores ou listeners sem modificar código existente. Sistema descobre tudo sozinho.

---

## Threads

### Pool principal: FiadoPay-Async (5-10 threads)

Usado para:
- Processar pagamentos (2 segundos cada)
- Enviar webhooks HTTP
- Retry de webhooks falhados

**Configuração:**
- 5 threads sempre ativas
- Pode crescer até 10 se necessário
- Fila de 25 tarefas

### Pool de webhooks: Webhook-Dispatcher (3 threads fixas)

Usado para:
- Invocar listeners de webhook via reflexão

**Por que separado:** Se webhooks travarem (servidor cliente fora), não afeta processamento de pagamentos.

### Como coordena

Sistema NÃO usa sincronização manual (synchronized, Lock, etc). Coordenação é feita por:
- JPA gerencia locks do banco
- H2 gerencia locks de linhas
- Cada thread trabalha independente
- Estado compartilhado está no banco de dados

É mais simples e menos propenso a bugs de concorrência.

---

## Padrões de design aplicados

### Strategy Pattern

Usado no cálculo de juros.

- Interface: `InterestCalculator`
- Implementações: `CardInstallmentInterestCalculator` (1% ao mês), `NoInterestCalculator` (sem juros)
- Quem escolhe: `InterestCalculationService` (baseado em método de pagamento e parcelas)

### Observer Pattern

Usado nos webhooks.

- Subject: `WebhookEventDispatcher` (mantém lista de listeners)
- Observers: Métodos anotados com `@WebhookSink`
- Quando evento acontece, notifica todos os interessados

### Factory Pattern

`InterestCalculationService` funciona como factory: você pede cálculo de juros e ele decide qual estratégia usar.

### Repository Pattern

Spring Data JPA cria repositories automaticamente:
- `PaymentRepository`, `MerchantRepository`, `WebhookDeliveryRepository`

### SOLID

**Single Responsibility:** Cada classe faz uma coisa.

**Open/Closed:** Adicionar validador ou listener não modifica código existente.

**Liskov Substitution:** Pode trocar `SimulatedPaymentProcessor` por outro sem quebrar.

**Interface Segregation:** Interfaces pequenas (`FraudValidator`, `InterestCalculator`).

**Dependency Inversion:** Services dependem de interfaces, não de classes concretas.

---

## Limites conhecidos

Sistema é para fins didáticos. Tem limitações propositais:

**Autenticação fake:** Tokens são falsos. Qualquer string funciona. Só demonstra fluxo.

**Processamento simulado:** Só espera 2 segundos e aprova/rejeita aleatoriamente. Não valida cartão de verdade.

**Banco em memória:** H2 perde dados quando desliga. Para produção seria Postgres ou MySQL.

**Sem testes:** Não tem testes unitários nem integração. Focamos em arquitetura e documentação.

**Validação mínima:** Não valida se valor é positivo, range de parcelas, formato de moeda.

**Thread pools fixos:** Números hardcoded. Em produção seria configurável via properties.

**Sem observabilidade:** Só logs básicos. Produção teria métricas, tracing, alertas.

Essas limitações são conhecidas e aceitas. O objetivo é demonstrar conceitos, não ser gateway real.

---

## Como rodar

Precisa ter Java 17+ e Maven instalado.

```bash
mvn clean install
mvn spring-boot:run
```

Sistema sobe em `http://localhost:8080`

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Console H2: `http://localhost:8080/h2` (JDBC: `jdbc:h2:mem:fiadopay`, usuário: `sa`, sem senha)

---

## Resumo da refatoração

### O que fizemos

1. **Dividimos** PaymentService monolítico em 8 services especializados
2. **Criamos** 3 anotações customizadas para extensibilidade
3. **Implementamos** descoberta automática via reflexão
4. **Separamos** thread pools para isolamento de falhas
5. **Aplicamos** padrões de design (Strategy, Observer, Factory)
6. **Documentamos** tudo de forma clara

---

## Contexto: conceitos aplicados

### Anotações e seus metadados

**O que são:** "Etiquetas" que você coloca em classes ou métodos. Sistema lê essas etiquetas em runtime.

**Metadados:** Informações dentro da anotação (name, threshold, riskLevel, etc).

**Por que usar:** Sistema descobre automaticamente componentes. É extensível sem modificar código.

### Reflexão

**O que é:** Java olhando para código em runtime. Descobre classes, métodos, anotações.

**Como usamos:** Descobrir validadores e listeners automaticamente no startup.

**Desvantagem:** Um pouco mais lento que chamada direta. Mas fazemos descoberta uma vez só, no startup.

### Threads e concorrência

**O que são:** Múltiplas coisas acontecendo ao mesmo tempo.

**Como usamos:**
- Thread HTTP responde cliente imediatamente
- Thread background processa pagamento
- Thread separada envia webhook

**Sincronização:** JPA e banco gerenciam automaticamente. Não tem código de lock manual.

### Padrões de design

**O que são:** Soluções comprovadas para problemas comuns.

**Quais usamos:**
- Strategy: Múltiplas formas de calcular juros
- Observer: Múltiplos listeners para eventos
- Factory: Escolher qual estratégia usar
- Repository: Acesso ao banco

### SOLID

**O que é:** 5 princípios para código limpo.

**Como aplicamos:**
- Cada classe tem uma responsabilidade
- Adicionar funcionalidade não modifica código existente
- Dependências são interfaces, não classes concretas

---

Rafael Brito e Arthur Lobo
UCSAL 2025 - Programação Orientada a Objetos Avançada - Refatoração FiadoPay
