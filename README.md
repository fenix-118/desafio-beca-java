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

## Tecnologias

Java, Spring Boot, JPA, PostgreSQL, Docker Compose, JUnit, Mockito, Swagger/OpenAPI.
