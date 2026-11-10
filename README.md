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

| Variável | Obrigatória | Descrição |
|---|---|---|
| `POSTGRES_USER` | Sim | Usuário do PostgreSQL |
| `POSTGRES_PASSWORD` | Sim | Senha do PostgreSQL |
| `MOCKAPI_BASE_URL` | Sim, para o MS2 | URL base da API Mock (recurso `contas`); sem ela o MS2 não sobe |
| `MS1_BASE_URL` | Não | Endereço do MS1 usado pelo MS2 (padrão `http://localhost:8081`) |

### 2. Subir o banco

```bash
docker compose up -d postgres
```

O PostgreSQL fica disponível em `localhost:5433`, com os bancos `usuarios_db` e `transacoes_db`.

### 3. Rodar pela IDE (IntelliJ)
Basta executar `Ms1UsuariosApplication` e `Ms2TransacoesApplication`, nessa ordem: o MS2 consulta o MS1 antes de registrar cada transação.
Não é preciso configurar variáveis na IDE: o `application.yml` importa o `.env` automaticamente com
`spring.config.import`. Valores definidos em `SPRING_DATASOURCE_*` têm prioridade sobre o `.env`.

#### Rede corporativa (proxy)

Se o MS2 não conseguir acessar a API Mock na rede da empresa, adicione nas **VM options** da Run Configuration do `Ms2TransacoesApplication` (**Run → Edit Configurations → Modify options → Add VM options**):

```
-Djava.net.useSystemProxies=true -Djavax.net.ssl.trustStoreType=Windows-ROOT
```

A primeira opção faz o Java usar o proxy do Windows; a segunda faz ele confiar nos certificados instalados no Windows, incluindo os da empresa.

### 4. Rodar pelo Docker Compose

```bash
docker compose up -d --build
```

> O MS2 ainda não está no Compose: por enquanto, ele roda pela IDE. O Compose sobe o PostgreSQL e o MS1.

### Serviços

| Serviço | Porta | Health check |
|---|---|---|
| MS1 - Usuários | 8081 | http://localhost:8081/actuator/health |
| MS2 - Transações | 8082 | http://localhost:8082/actuator/health |
| PostgreSQL | 5433 | — |

## Documentação da API (Swagger)

Com os serviços rodando (o MS1 pela IDE ou pelo Compose, o MS2 pela IDE):

| Recurso | MS1 - Usuários | MS2 - Transações |
|---|---|---|
| Swagger UI (documentação interativa) | http://localhost:8081/swagger-ui.html | http://localhost:8082/swagger-ui.html |
| Especificação OpenAPI (JSON) | http://localhost:8081/v3/api-docs | http://localhost:8082/v3/api-docs |

Pelo Swagger UI é possível testar todos os endpoints, inclusive a importação de CSV (botão **Try it out**). Há arquivos de exemplo em `samples/`.

## Transações (MS2)

| Método e rota | O que faz |
|---|---|
| `POST /transacoes` | Registra depósito, retirada, transferência ou compra e responde 201 com o status (`APROVADA` ou `REJEITADA`, com o motivo) |
| `GET /transacoes/{id}` | Consulta uma transação |
| `GET /transacoes?usuarioId=1&inicio=2026-11-01&fim=2026-11-30&tipo=COMPRA` | Lista as transações do usuário; período e tipo são opcionais |
| `GET /contas/{usuarioId}/saldo` | Saldo e limites lidos da API Mock no momento, o gasto de hoje e quanto ainda pode sair |

Regras principais: valor em BRL com até 2 casas decimais; saídas (retirada, transferência e compra) exigem categoria; transferência exige `usuarioDestinoId`; saídas são conferidas contra saldo, limite por transação e limite diário (dia no fuso de São Paulo); depósito não tem limite nem exige saldo, mas precisa da conta na Mock para ser creditado: sem conta, ou com a Mock fora do ar, ele é rejeitado.

> O saldo e os limites vêm do recurso `contas` da API Mock (mockapi.io). Defina `MOCKAPI_BASE_URL` no `.env` (veja o `.env.example`); sem ela o MS2 não sobe. Usuário sem conta na Mock tem a transação `REJEITADA` com `CONTA_INEXISTENTE`.
>
> Quem atualiza o saldo é o MS2: depois de passar nas regras, ele grava o novo saldo na Mock (`PUT /contas/{id}`) e só então aprova. Depósito soma, saída subtrai. Se o `PUT` falhar, a transação fica `REJEITADA` com `SERVICO_SALDO_INDISPONIVEL`.
>
> **Transferência debita só quem envia (proposital).** O destino pode não ter conta na Mock, e creditar o destino exigiria um segundo `PUT` sem como desfazer os dois juntos. A transferência fica registrada com `usuarioDestinoId`; creditar o destino está como proposta para o PO.

### Limitações conhecidas (e como resolveríamos)

| Limitação | O que pode acontecer | Como mitigamos hoje | Solução em produção |
|---|---|---|---|
| **Concorrência** | Duas saídas ao mesmo tempo do mesmo usuário leem o mesmo saldo; a segunda sobrescreve a primeira no `PUT` (atualização perdida) e as duas podem passar no limite diário | Nada no código: documentado | Trava por conta (ex.: `SELECT ... FOR UPDATE` num registro do usuário), versão otimista na conta (`If-Match`/versão) ou o saldo como soma das transações no próprio banco |
| **Mock e banco sem transação única** | O `PUT` na Mock acontece antes de gravar; se a gravação falhar, o saldo já mudou sem registro | Log de erro com a conta e o saldo (anterior → novo) e tentativa de voltar o saldo anterior; se não der, log `AJUSTE MANUAL` | Padrão *outbox*/saga: gravar a transação como pendente, atualizar o saldo e confirmar, com reprocessamento |

### Câmbio (BrasilAPI)

Cada transação mostra o valor em real e em **USD** e **EUR** (`conversoes`), com a taxa e a data da cotação. A cotação é buscada **no registro** e guardada na transação, então a consulta mostra sempre o câmbio do dia em que ela foi feita.

| Regra | Por quê |
|---|---|
| Endpoint `GET https://brasilapi.com.br/api/cambio/v1/cotacao/{moeda}/{data}` | Cotação oficial do Banco Central por moeda e data |
| Taxa = boletim `FECHAMENTO PTAX`, valor de **venda** | Referência oficial do dia |
| Transação de hoje (ou data futura) consulta o **dia anterior** | A API responde 400 `NO_TODAY_DATE` para o dia atual |
| A data guardada é a que vem na resposta | Em fim de semana ou feriado, a API devolve o último dia útil (ex.: domingo → sexta) |

### API Mock fora do ar

Sem saldo confirmado, o MS2 nunca aprova às cegas (tempo limite de 2 s em cada chamada):

| Onde | Resposta |
|---|---|
| `POST /transacoes` (qualquer tipo) | 201, `REJEITADA` com `SERVICO_SALDO_INDISPONIVEL`; a transação fica gravada e a descrição explica o motivo |
| `GET /contas/{usuarioId}/saldo` | 503, "Serviço de saldo indisponível; tente novamente em instantes." |

Para testar: troque `MOCKAPI_BASE_URL` no `.env` por um endereço inexistente (ex.: `https://invalido.mockapi.io/api/v1`), reinicie o MS2 e faça uma compra. Depois volte a URL certa.

### Consulta ao MS1 (usuários)

O MS2 consulta o MS1 (`GET /usuarios/{id}`) antes de registrar. Pela IDE, suba o MS1 antes do MS2 (endereço em `MS1_BASE_URL`, padrão `http://localhost:8081`).

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