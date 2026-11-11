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
| `BRASILAPI_BASE_URL` | Não | URL base da BrasilAPI, usada no câmbio (padrão `https://brasilapi.com.br/api`) |

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
docker compose ps
```

O Compose sobe os três serviços na ordem certa, e cada um só começa depois que o anterior está saudável:

| Ordem | Serviço | Espera por | Saudável quando |
|---|---|---|---|
| 1 | `postgres` | — | `pg_isready` responde |
| 2 | `ms1-usuarios` | PostgreSQL saudável | `/actuator/health` responde `UP` |
| 3 | `ms2-transacoes` | PostgreSQL e MS1 saudáveis | `/actuator/health` responde `UP` |

O MS2 fala com o MS1 pelo **nome do serviço** (`http://ms1-usuarios:8081`) na rede interna `beca-net`, e não por `localhost`. Sem `MOCKAPI_BASE_URL` no `.env`, o Compose para logo no início, com a mensagem dizendo o que falta. As portas ficam presas em `127.0.0.1`, e os containers rodam sem privilégios extras (`no-new-privileges`, `cap_drop: ALL`).

> Na rede corporativa, o `--build` pode falhar ao baixar as dependências do Maven dentro do container. Nesse caso, rode os serviços pela IDE (passo 3) e use o Compose só para o PostgreSQL.

### Serviços

| Serviço | Porta | Health check |
|---|---|---|
| MS1 - Usuários | 8081 | http://localhost:8081/actuator/health |
| MS2 - Transações | 8082 | http://localhost:8082/actuator/health |
| PostgreSQL | 5433 | — |

## Documentação da API (Swagger)

Com os serviços rodando, pela IDE ou pelo Compose:

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
| `GET /despesas/resumo?usuarioId=3&inicio=2026-11-01&fim=2026-11-30` | Análise de despesas: total por categoria, por dia e por mês, com o total geral; sem datas, usa o mês atual |

Regras principais: valor em BRL com até 2 casas decimais; saídas (retirada, transferência e compra) exigem categoria; transferência exige `usuarioDestinoId`; saídas são conferidas contra saldo, limite por transação e limite diário (dia no fuso de São Paulo); depósito não tem limite nem exige saldo, mas precisa da conta na Mock para ser creditado: sem conta, ou com a Mock fora do ar, ele é rejeitado.

> O saldo e os limites vêm do recurso `contas` da API Mock (mockapi.io). Defina `MOCKAPI_BASE_URL` no `.env` (veja o `.env.example`); sem ela o MS2 não sobe. Usuário sem conta na Mock tem a transação `REJEITADA` com `CONTA_INEXISTENTE`.
>
> Quem atualiza o saldo é o MS2: depois de passar nas regras, ele grava o novo saldo na Mock (`PUT /contas/{id}`) e só então aprova. Depósito soma, saída subtrai. Se o `PUT` falhar, a transação fica `REJEITADA` com `SERVICO_SALDO_INDISPONIVEL`.
>
>  **Transferência ainda debita só quem envia (limitação conhecida).** A decisão do PO é que a transferência credite o destino, senão o dinheiro sai de uma conta e não entra em outra. A transferência fica registrada com `usuarioDestinoId`, mas o saldo do destino na Mock não muda. A implementação proposta segue o mesmo padrão de compensação já usado no MS2: conferir a conta do destino na Mock antes de qualquer `PUT`, debitar quem envia, creditar o destino e, se o crédito falhar, devolver o saldo de quem envia (com log de `AJUSTE MANUAL` se a devolução também falhar).

### Limitações conhecidas (e como resolveríamos)

| Limitação | O que pode acontecer | Como mitigamos hoje | Solução em produção |
|---|---|---|---|
| **Concorrência** | Duas saídas ao mesmo tempo do mesmo usuário leem o mesmo saldo; a segunda sobrescreve a primeira no `PUT` (atualização perdida) e as duas podem passar no limite diário | Nada no código: documentado | Trava por conta (ex.: `SELECT ... FOR UPDATE` num registro do usuário), versão otimista na conta (`If-Match`/versão) ou o saldo como soma das transações no próprio banco |
| **Mock e banco sem transação única** | O `PUT` na Mock acontece antes de gravar; se a gravação falhar, o saldo já mudou sem registro | Log de erro com a conta e o saldo (anterior → novo) e tentativa de voltar o saldo anterior; se não der, log `AJUSTE MANUAL` | Padrão *outbox*/saga: gravar a transação como pendente, atualizar o saldo e confirmar, com reprocessamento |
| **Transferência sem crédito no destino** | O valor sai de quem envia e não entra no destino | Documentado; a decisão do PO é creditar o destino | Debitar quem envia, creditar o destino e, se o crédito falhar, devolver o saldo de quem envia (compensação), conferindo antes se o destino tem conta na Mock |

### Câmbio (BrasilAPI)

Cada transação mostra o valor em real e em **USD** e **EUR** (`conversoes`), com a taxa e a data da cotação. A cotação é buscada **no registro** e guardada na transação, então a consulta mostra sempre o câmbio do dia em que ela foi feita.

| Regra | Por quê |
|---|---|
| Endpoint `GET https://brasilapi.com.br/api/cambio/v1/cotacao/{moeda}/{data}` | Cotação oficial do Banco Central por moeda e data |
| Taxa = boletim `FECHAMENTO PTAX`, valor de **venda** | Referência oficial do dia |
| Transação de hoje (ou data futura) consulta o **dia anterior** | A API responde 400 `NO_TODAY_DATE` para o dia atual |
| A data guardada é a que vem na resposta | Em fim de semana ou feriado, a API devolve o último dia útil (ex.: domingo → sexta) |

**BrasilAPI fora do ar** (ou qualquer falha: tempo limite de 2 s, erro 4xx/5xx, resposta inesperada): o câmbio é informativo e nunca quebra a transação. Ela é registrada normalmente, e a consulta responde com a moeda marcada como `"disponivel": false`. A consulta nunca chama a BrasilAPI (lê o que foi guardado no registro), então ela não cai por causa da API. Depois de uma falha de conexão (sem rede, tempo limite ou 5xx), a BrasilAPI fica **5 minutos sem ser chamada**: nesse tempo, as transações gravam na hora com a conversão indisponível, em vez de esperar 2 s por moeda a cada registro (ajustável em `brasilapi.pausa-apos-falha-segundos`). Cotações já guardadas em cache continuam valendo. Para testar, aponte `BRASILAPI_BASE_URL` no `.env` para um endereço inexistente (ex.: `https://invalido.brasilapi.com.br/api`) e reinicie o MS2.

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

## Análise de despesas (MS2)

`GET /despesas/resumo` soma as despesas do usuário no período e responde com o total geral, a quantidade e três agrupamentos:

| Campo | O que traz |
|---|---|
| `porCategoria` | Uma linha por categoria, da maior para a menor |
| `porDia` | Uma linha por dia com despesa (aaaa-mm-dd), em ordem cronológica |
| `porMes` | Uma linha por mês com despesa (aaaa-mm), em ordem cronológica |

| Regra | Por quê |
|---|---|
| Só entram saídas **aprovadas**: retirada, transferência enviada e compra | Depósito não é gasto, e rejeitada não tirou dinheiro da conta |
| O dia e o mês são os de **São Paulo** | Mesma regra do limite diário: uma compra às 22h do dia 31 conta no dia 31, mesmo sendo dia 1º em UTC |
| Sem `inicio` e `fim`, o período é o **mês atual** (do dia 1º ao último dia) | O caso mais comum não precisa de parâmetros; a resposta mostra o período usado |
| Só uma das datas → 400; `inicio` depois de `fim` → 400 | Evita um período ambíguo |
| Período de no máximo 366 dias → senão 400 | Um intervalo de vários anos carregaria todas as transações na memória de uma vez |

> **Pendência de segurança:** neste PR, `GET /despesas/resumo` ainda não exige login, como as outras rotas do MS2: qualquer pessoa consulta o resumo de outro usuário trocando o `usuarioId`. A autenticação, com o `usuarioId` conferido contra o usuário do login, entra no PR do pedido 6 (login).

Exemplo de resposta:

```json
{
  "usuarioId": 3, "inicio": "2026-11-01", "fim": "2026-11-30", "moeda": "BRL",
  "totalGeral": 150.00, "quantidade": 2,
  "porCategoria": [ { "categoria": "MORADIA", "total": 100.00, "quantidade": 1 },
                    { "categoria": "ALIMENTACAO", "total": 50.00, "quantidade": 1 } ],
  "porDia": [ { "dia": "2026-11-10", "total": 150.00, "quantidade": 2 } ],
  "porMes": [ { "mes": "2026-11", "total": 150.00, "quantidade": 2 } ]
}
```

## Tecnologias

Java, Spring Boot, JPA, PostgreSQL, Docker Compose, JUnit, Mockito, Swagger/OpenAPI.