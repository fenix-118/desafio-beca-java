# Desafio Beca Java - API de Gestão Financeira

Projeto do Desafio Beca Java (Beca Bradesco · NTT DATA).

## Arquitetura

- **MS1 Usuários:** CRUD de usuários e importação via CSV (PostgreSQL)
- **MS2 Transações:** depósito, retirada, transferência e compra, em arquitetura hexagonal
- **Integrações:** API Mock (saldo e limites) e BrasilAPI (câmbio)

## Como rodar o projeto

### Pré-requisitos
- Java 25
- Maven
- Docker com Docker Compose

### 1. Configurar as variáveis de ambiente
As credenciais do banco ficam no arquivo `.env`, na raiz do projeto. Esse arquivo **não vai para o Git**.
Crie o seu a partir do modelo:

```bash
cp .env.example .env
```

Depois, preencha os valores no `.env`:

| Variável | Descrição |
|---|---|
| `POSTGRES_USER` | Usuário do PostgreSQL |
| `POSTGRES_PASSWORD` | Senha do PostgreSQL |
| `MOCKAPI_BASE_URL` | URL base da API Mock |

### 2. Subir o banco

```bash
docker compose up -d postgres
```

O PostgreSQL fica disponível em `localhost:5433`, com os bancos `usuarios_db` e `transacoes_db`.

### 3. Rodar pela IDE (IntelliJ)
Basta executar `Ms1UsuariosApplication` (ou `Ms2TransacoesApplication`).
Não é preciso configurar variáveis na IDE: o `application.yml` importa o `.env` automaticamente com
`spring.config.import`. Valores definidos em `SPRING_DATASOURCE_*` têm prioridade sobre o `.env`.

### 4. Rodar tudo pelo Docker Compose

```bash
docker compose up -d --build
```

> O MS2 ainda não está no Compose. Ele será adicionado quando o serviço de transações estiver implementado.

### Serviços

| Serviço | Porta | Health check |
|---|---|---|
| MS1 - Usuários | 8081 | http://localhost:8081/actuator/health |
| MS2 - Transações | 8082 | http://localhost:8082/actuator/health |
| PostgreSQL | 5433 | — |_

### Documentação da API (Swagger)

Com os serviços rodando (pela IDE ou pelo Compose):

| Recurso | MS1 - Usuários | MS2 - Transações |
|---|---|---|
| Swagger UI (documentação interativa) | http://localhost:8081/swagger-ui.html | http://localhost:8082/swagger-ui.html |
| Especificação OpenAPI (JSON) | http://localhost:8081/v3/api-docs | http://localhost:8082/v3/api-docs |

Pelo Swagger UI é possível testar todos os endpoints, inclusive a importação de CSV (botão **Try it out**). Há arquivos de exemplo em `samples/`.

### Transações (MS2)

| Método e rota | O que faz |
|---|---|
| `POST /transacoes` | Registra depósito, retirada, transferência ou compra e responde 201 com o status (`APROVADA` ou `REJEITADA`, com o motivo) |
| `GET /transacoes/{id}` | Consulta uma transação |
| `GET /transacoes?usuarioId=1&inicio=2026-11-01&fim=2026-11-30&tipo=COMPRA` | Lista as transações do usuário; período e tipo são opcionais |

Regras principais: valor em BRL com até 2 casas decimais; saídas (retirada, transferência e compra) exigem categoria; transferência exige `usuarioDestinoId`; saídas são conferidas contra saldo, limite por transação e limite diário (dia no fuso de São Paulo); depósito é sempre aprovado.

> O saldo e os limites vêm do recurso `contas` da API Mock (mockapi.io). Defina `MOCKAPI_BASE_URL` no `.env` (veja o `.env.example`); sem ela o MS2 não sobe. Usuário sem conta na Mock tem a transação `REJEITADA` com `CONTA_INEXISTENTE`.
>
> Quem atualiza o saldo é o MS2: depois de passar nas regras, ele grava o novo saldo na Mock (`PUT /contas/{id}`) e só então aprova. Depósito soma, saída subtrai. Se o `PUT` falhar, a transação fica `REJEITADA` com `SERVICO_SALDO_INDISPONIVEL`.

> O MS2 consulta o MS1 (`GET /usuarios/{id}`) antes de registrar. Pela IDE, suba o MS1 antes (endereço em `MS1_BASE_URL`, padrão `http://localhost:8081`); no Compose, o MS2 só sobe depois que o healthcheck do MS1 responde `UP`.

| Situação no MS1 | Resposta do MS2 | Grava? |
|---|---|---|
| Dono não existe | 404 | Não |
| MS1 fora do ar ao conferir o **dono** | 503 | Não |
| Destino não existe | 201, `REJEITADA` com `DESTINO_INEXISTENTE` | Sim |
| MS1 fora do ar ao conferir o **destino** | 201, `REJEITADA` com `SERVICO_USUARIOS_INDISPONIVEL` | Sim |
| MS1 responde outro 4xx (ex.: 403) | 502, com `log.error` do status | Não |

**Por que o dono e o destino são tratados diferente:** uma transação só existe se tiver um dono confirmado. Sem conseguir falar com o MS1 na checagem do dono, não há de quem registrar a tentativa, então a resposta é 503 e o cliente tenta de novo. Na checagem do destino o dono já foi confirmado, então vale a mesma regra do saldo: a transação não é aprovada às cegas e fica gravada como `REJEITADA` com o motivo.

## Tecnologias

Java, Spring Boot, JPA, PostgreSQL, Docker Compose, JUnit, Mockito, Swagger/OpenAPI.
