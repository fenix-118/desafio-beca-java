# Desafio Beca Java - API de Gestão Financeira

Projeto do Desafio Beca Java (Beca Bradesco · NTT DATA).

## Arquitetura

- **MS1 Usuários:** CRUD de usuários e importação via CSV (PostgreSQL)
- **MS2 Transações:** depósito, retirada, transferência e compra, em arquitetura hexagonal
- **Integrações:** API Mock (saldo e limites) e BrasilAPI (câmbio)

## O que está pronto

| Entrega | Onde ler mais |
|---|---|
| Cadastro, consulta, alteração e exclusão de usuários, importação por CSV e senha com BCrypt (MS1) | [Validação de usuários](#validação-de-usuários-ms1) |
| Depósito, retirada, transferência e compra com saldo, limite por transação e limite diário (MS2) | [Transações](#transações-ms2) |
| Câmbio do dia (USD e EUR) guardado em cada transação, com cache e pausa quando a BrasilAPI cai | [Câmbio](#câmbio-brasilapi) |
| Análise de despesas por categoria, dia e mês | [Análise de despesas](#análise-de-despesas-ms2) |
| Relatório CSV das transações, que abre direto no Excel em português | [Relatório CSV](#relatório-csv-ms2) |
| Login (HTTP Basic) e dono dos dados: cada usuário só vê o que é seu | [Login](#login-ms1-e-ms2) |
| Docker Compose com healthchecks, script de dados para a demonstração e Swagger | [Como rodar](#como-rodar-o-projeto) |
| Flyway, testes com JaCoCo, `@DataJpaTest` e revisão de segredos com hook de pre-commit | [Testes](#testes-e-cobertura) e [Segurança](#segurança-e-segredos) |

Testes: **79 no MS1 e 174 no MS2**, todos passando (`mvn clean test`, 12/11/2026).

Ficou de fora, documentado: o crédito no destino da transferência (a transferência debita só quem envia), a concorrência entre saídas simultâneas e o limite de tentativas de login. O detalhe e a solução proposta estão em [Limitações conhecidas](#limitações-conhecidas-e-como-resolveríamos) e em [Login](#login-ms1-e-ms2).

## Como rodar o projeto

### Pré-requisitos
- Java 25
- Maven 3.9
- Docker com Docker Compose (Docker Desktop ou Rancher Desktop)

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
| `SERVICO_TOKEN` | Sim | Token que o MS2 manda ao MS1 para consultar usuários (o mesmo nos dois serviços); use um valor aleatório e longo |
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

> **Na rede corporativa, rode o MS1 e o MS2 pela IDE (passo 3) e use o Compose só para o PostgreSQL.** A rede da empresa inspeciona o HTTPS com um certificado próprio. Pela IDE, o Java confia nele com `-Djavax.net.ssl.trustStoreType=Windows-ROOT`, mas o Java do container (Linux) não conhece esse certificado. O sintoma é o saldo responder 503 e o log do MS2 mostrar `API Mock indisponível: SunCertPathBuilderException`. Pelo mesmo motivo, o `--build` também pode falhar ao baixar as dependências do Maven. Fora da rede da empresa, o Compose funciona completo.
>
> ```bash
> docker compose stop ms1-usuarios ms2-transacoes   # deixa só o PostgreSQL no ar
> ```

### 5. Derrubar e subir de novo

| Comando | O que acontece com os dados |
|---|---|
| `docker compose down` e depois `docker compose up -d` | **Ficam**: o banco está no volume `pgdata`, que sobrevive ao `down` |
| `docker compose restart ms2-transacoes` | Ficam; só o MS2 reinicia (útil para testar o MS2 com o MS1 no ar) |
| `docker compose stop ms1-usuarios` | Ficam; serve para mostrar o MS2 com o MS1 fora do ar (503 ao conferir o dono) |
| `docker compose down -v` e depois `docker compose up -d --build` | **Somem**: o `-v` apaga o volume, e o banco é recriado vazio pelo `docker/postgres/init.sql` |

Para conferir que os dados sobreviveram, rode antes e depois do `down`/`up`:

```bash
docker exec beca-postgres sh -c 'psql -U "$POSTGRES_USER" -d usuarios_db -c "select id, email from usuarios order by id"'
```

> Depois de um `down -v`, ou na primeira vez que o Compose cria o volume, o banco começa vazio e os ids recomeçam do 1. Quem nunca tinha sido cadastrado precisa ser cadastrado de novo, e o `usuarioId` das contas na API Mock precisa bater com os ids novos (veja o passo 6).

### 6. Dados de exemplo para a demonstração

Com os serviços no ar, o script abaixo faz login no MS1, cadastra os usuários do `samples/usuarios-exemplo.csv` e registra no MS2 uma sequência de transações do usuário do login:

Antes do script, o usuário do login precisa existir no MS1. Num banco novo, importe os usuários de exemplo primeiro:

```bash
curl -s -X POST http://localhost:8081/usuarios/importacao -F "arquivo=@samples/usuarios-exemplo.csv"
```

Depois rode o script com o e-mail do usuário e o id do destino da transferência. A senha é lida com `read -s` e passada pelo `export`, **nunca escrita na linha de comando**:

```bash
export DEMO_EMAIL=ana.souza@example.com
read -rsp 'Senha: ' DEMO_SENHA; echo   # digita sem aparecer na tela
export DEMO_SENHA
bash docker/demo/carregar-dados-demo.sh 2
```

Assim a senha não aparece na tela, não fica no histórico do terminal (`history`) nem em arquivo. Ela fica só na sessão aberta; para tirar, `unset DEMO_SENHA` (ou feche o terminal). O argumento é o id do destino da transferência (padrão `4`). O usuário do login e o destino precisam ter conta na Mock: o MS2 procura a conta pelo campo `usuarioId` (`GET /contas?usuarioId=1`), então esse campo precisa ser o id do usuário no MS1. Para conferir e corrigir:

```bash
MOCK="$(grep '^MOCKAPI_BASE_URL=' .env | cut -d= -f2- | tr -d '\r"')"   # lê a URL sem mostrá-la
curl -s "$MOCK/contas"; echo                                              # lista as contas
curl -s -X PUT "$MOCK/contas/ID_DA_CONTA" -H 'Content-Type: application/json' \
  -d '{"usuarioId": 1, "saldo": 1938.5, "limitePorTransacao": 500, "limiteDiario": 800}'; echo
```

Usuário sem conta na Mock tem o saldo respondendo 404 e as transações `REJEITADA` com `CONTA_INEXISTENTE`. O script mostra o status de cada transação e termina com o saldo e o resumo de despesas do mês. Rodar de novo cria transações novas (o CSV repetido só gera falhas de e-mail já cadastrado). Os endereços podem ser trocados por `MS1_URL` e `MS2_URL`.

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
| `POST /transacoes` | Registra depósito, retirada, transferência ou compra do usuário do login e responde 201 com o status (`APROVADA` ou `REJEITADA`, com o motivo) |
| `GET /transacoes/{id}` | Consulta uma transação |
| `GET /transacoes?inicio=2026-11-01&fim=2026-11-30&tipo=COMPRA` | Lista as transações do usuário do login; período e tipo são opcionais |
| `GET /contas/saldo` | Saldo e limites do usuário do login, lidos da API Mock no momento, o gasto de hoje e quanto ainda pode sair |
| `GET /despesas/resumo?inicio=2026-11-01&fim=2026-11-30` | Análise de despesas do usuário do login: total por categoria, por dia e por mês, com o total geral; sem datas, usa o mês atual |
| `GET /transacoes/relatorio?inicio=2026-11-01&fim=2026-11-30` | Baixa o relatório CSV das transações do usuário do login; sem datas, usa o mês atual |

Regras principais: valor em BRL com até 2 casas decimais; saídas (retirada, transferência e compra) exigem categoria; depósito não aceita `categoria` nem `usuarioDestinoId` (400); transferência exige `usuarioDestinoId`; saídas são conferidas contra saldo, limite por transação e limite diário (dia no fuso de São Paulo); depósito não tem limite nem exige saldo, mas precisa da conta na Mock para ser creditado: sem conta, ou com a Mock fora do ar, ele é rejeitado.

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

**BrasilAPI fora do ar** (ou qualquer falha: tempo limite de 2 s, erro 4xx/5xx, resposta inesperada): o câmbio é informativo e nunca quebra a transação. Ela é registrada normalmente, e a consulta responde com a moeda marcada como `"disponivel": false`. A consulta nunca chama a BrasilAPI (lê o que foi guardado no registro), então ela não cai por causa da API. As falhas também ficam em cache, para a transação não esperar a BrasilAPI de novo a cada registro.

#### Pausa de 5 minutos e cache de falhas

| Situação | O que fica em cache | Por quanto tempo | Efeito |
|---|---|---|---|
| Cotação encontrada | A cotação daquela moeda e dia | Até o MS2 reiniciar (cotação de dia passado não muda) | Não chama a BrasilAPI de novo |
| Sem conexão, tempo limite (2 s) ou erro 5xx | A falha da **BrasilAPI inteira** (pausa) | 5 minutos | Nenhuma moeda é consultada; a conversão sai indisponível na hora, sem esperar 2 s por moeda |
| Recusa 4xx, resposta sem cotação ou em formato inesperado | A falha **daquela moeda e dia** | 5 minutos | Só aquela moeda e dia deixam de ser consultados; as outras seguem normais |

O tempo é ajustável em `brasilapi.pausa-apos-falha-segundos` (padrão `300`). Cotações já guardadas em cache continuam valendo durante a pausa. Passados os 5 minutos, a próxima transação consulta a BrasilAPI de novo. Para testar, aponte `BRASILAPI_BASE_URL` no `.env` para um endereço inexistente (ex.: `https://invalido.brasilapi.com.br/api`) e reinicie o MS2.

### API Mock fora do ar

Sem saldo confirmado, o MS2 nunca aprova às cegas (tempo limite de 2 s em cada chamada):

| Onde | Resposta |
|---|---|
| `POST /transacoes` (qualquer tipo) | 201, `REJEITADA` com `SERVICO_SALDO_INDISPONIVEL`; a transação fica gravada e a descrição explica o motivo |
| `GET /contas/saldo` | 503, "Serviço de saldo indisponível; tente novamente em instantes." |

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

## Relatório CSV (MS2)

`GET /transacoes/relatorio` baixa um arquivo `transacoes-<inicio>-a-<fim>.csv` com todas as transações do usuário do login no período, **aprovadas e rejeitadas**, da mais antiga para a mais recente. Não há `usuarioId` na URL: o relatório é sempre de quem fez o login, então não dá para pedir o de outra pessoa.

O período segue as mesmas regras da análise de despesas (o código é o mesmo, `PeriodoSaoPaulo`): sem datas, o mês atual; só uma data, `inicio` depois de `fim` ou mais de 366 dias → 400.

Colunas: `Data; Id; Tipo; Categoria; Descrição; Usuário destino; Valor (BRL); Status; Motivo da rejeição`.

| Detalhe do arquivo | Por quê |
|---|---|
| Separador `;` | No Brasil a vírgula é o separador decimal, então o Excel em português espera `;` |
| BOM UTF-8 no início | Sem ele, o Excel abre o arquivo como ANSI e troca os acentos por símbolos |
| Valor com vírgula decimal, sem milhar (`1500,00`) | O Excel em português lê como número, e dá para somar a coluna |
| Data `dd/MM/aaaa HH:mm` no fuso de São Paulo | O Excel lê como data e hora; é o mesmo dia usado no limite diário |
| Campo com `;`, aspas ou quebra de linha vai entre aspas | Uma descrição com `;` não quebra as colunas |
| Texto que começa com `=`, `+`, `-` ou `@` ganha um `'` na frente | A descrição é texto livre: sem isso, uma descrição como `=HYPERLINK(...)` viraria fórmula ao abrir (injeção de CSV, OWASP) |

**Como baixar:**

| Forma | Como | Abre certo no Excel? |
|---|---|---|
| Link direto no navegador (recomendado) | Abra `http://localhost:8082/transacoes/relatorio?inicio=2026-11-01&fim=2026-11-30`; o navegador pede o login e baixa o arquivo | Sim |
| Terminal | Com `DEMO_EMAIL` e `DEMO_SENHA` exportados (veja [Senha no terminal](#senha-no-terminal)): `curl -u "$DEMO_EMAIL:$DEMO_SENHA" -OJ "http://localhost:8082/transacoes/relatorio?inicio=2026-11-01&fim=2026-11-30"` (o `-OJ` salva com o nome que o servidor manda) | Sim |
| Swagger | **Authorize**, **Try it out** em `GET /transacoes/relatorio` e **Download file** | Colunas certas, mas os acentos se perdem |

O botão **Download file** do Swagger lê a resposta como texto e grava o arquivo de novo, e nisso o BOM se perde. Sem o BOM, o Excel abre como ANSI ("DescriÃ§Ã£o"). O arquivo que o MS2 envia está certo: pelo link direto ou pelo `curl`, ele chega intacto. O Swagger continua útil para ver a resposta e testar o 400 e o 401.

Conferido no Excel em português com os dados da demo: os acentos aparecem certos, a data vira data, o valor vira número (formate a coluna como **Moeda** para ver `45,90` em vez de `45,9`) e a descrição com `=` fica como texto.

## Validação de usuários (MS1)

As regras de nome, e-mail e senha ficam num lugar só (`MensagensUsuario`), com os limites e as mensagens. O `@Valid` dos DTOs e o `UsuarioService` usam as mesmas constantes, então a mensagem de erro é igual pela API e pela importação de CSV, e um teste (`MensagensDeValidacaoTest`) confere isso regra por regra.

| Campo | Regra | Mensagem |
|---|---|---|
| `nome` | Obrigatório, até 120 caracteres | "O campo 'nome' é obrigatório." / "... no máximo 120 caracteres." |
| `email` | Obrigatório, formato válido, até 160 caracteres, único | "E-mail inválido." / "... no máximo 160 caracteres." |
| `senha` | Obrigatória, no mínimo 8 caracteres e no máximo 72 bytes | "A senha deve ter no mínimo 8 caracteres." / "A senha é longa demais: o limite é de 72 bytes..." |

O limite da senha é em **bytes**, não em caracteres, porque o BCrypt só usa os primeiros 72 bytes: uma senha maior teria o final ignorado sem ninguém saber. Letras acentuadas ocupam 2 bytes, por isso a conferência fica no service, que mede os bytes, e não no `@Size` do DTO.

## Banco de dados e migrações (Flyway)

O esquema de cada serviço é criado pelas migrações do Flyway, em `src/main/resources/db/migration`, e o Hibernate só **confere** se as entidades batem com ele (`ddl-auto: validate`). Assim, a estrutura do banco fica versionada no Git e muda só por migração revisada.

| Serviço | Migração | Cria |
|---|---|---|
| MS1 | `V1__cria_tabela_usuarios.sql` | `usuarios`, com a restrição `uk_usuarios_email` |
| MS2 | `V1__cria_tabela_transacoes.sql` | `transacoes`, já com as colunas do câmbio (`cotacao_usd`, `cotacao_eur` e as datas), as restrições dos enums e o índice `idx_transacoes_usuario_data` |
| MS2 | `V2__garante_colunas_do_cambio.sql` | As colunas do câmbio e o índice com `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`, para o banco antigo que recebeu o baseline sem elas; num banco criado pela `V1`, não muda nada |

| Situação do banco | O que o Flyway faz |
|---|---|
| Vazio (clone novo ou `docker compose down -v`) | Roda a `V1`, que cria as tabelas, e a `V2`, que não muda nada |
| Já tinha as tabelas, criadas pelo antigo `ddl-auto: update` | Registra a `V1` como aplicada (**baseline**) sem rodá-la e roda a `V2`; os dados ficam como estão |
| Antigo e de antes do câmbio (pedido 3), sem as colunas da cotação | O baseline sozinho deixaria o banco sem as colunas e o MS2 não subiria (`ddl-auto: validate`); a `V2` cria as que faltam |

A `V1` foi escrita a partir do esquema real (`pg_dump -s`), com os mesmos tipos e nomes de restrição. As colunas do câmbio (pedido 3) entram na mesma `V1`, e não num `ALTER TABLE` separado, porque nenhum banco tinha rodado migração antes: a `V1` descreve o esquema completo de hoje, e os bancos que já existiam recebem o baseline. Uma mudança no banco entra numa nova versão (`V2`, `V3`...), nunca editando uma migração já aplicada: o Flyway confere o checksum e o serviço não sobe se ela mudar. O `MigracoesFlywayTest` roda as migrações num H2 nos dois cenários: banco novo e banco antigo sem as colunas do câmbio. Para ver o histórico: `docker exec beca-postgres sh -c 'psql -U "$POSTGRES_USER" -d transacoes_db -c "select version, description, success from flyway_schema_history"'`.

## Testes e cobertura

```bash
cd ms1-usuarios && mvn clean test
cd ../ms2-transacoes && mvn clean test
```

Resultado esperado: **79 testes no MS1 e 174 no MS2**, sem falhas.

Pelo IntelliJ: painel **Maven** → módulo → **Lifecycle** → `clean` + `test`. Cada build gera o relatório de cobertura do JaCoCo em `target/site/jacoco/index.html` (abra no navegador): a cobertura por pacote e por classe, e as linhas cobertas e não cobertas de cada arquivo. As classes `*Application` ficam de fora, porque só sobem o Spring.

As consultas do MS2 (soma das saídas aprovadas do dia e listagem por período e tipo) são testadas de verdade com `@DataJpaTest` num H2 em memória, com o esquema criado pela mesma migração `V1` do Flyway. Não precisa de Docker nem do PostgreSQL no ar.

## Login (MS1 e MS2)

Todas as rotas do MS2 pedem **login básico** (HTTP Basic) com o e-mail e a senha cadastrados no MS1. A cada requisição, o MS2 confere as credenciais no MS1 pelo nome do serviço; o MS2 nunca guarda senha.

| Rota | Quem acessa |
|---|---|
| MS1 `POST /autenticacao` | Aberta: confere e-mail e senha e devolve `id`, `nome` e `email` (sem senha) |
| MS1 `POST /usuarios` (cadastro) e `/usuarios/importacao` | Abertas |
| MS1 `GET`, `PUT` e `DELETE /usuarios/{id}` | Login do **próprio** usuário; o `GET` também aceita o MS2, com o token de serviço (`X-Servico-Token`) |
| MS1 `GET /usuarios` (lista de todos) | Removida: expunha o nome e o e-mail de todos os usuários |
| MS2 `/transacoes`, `/transacoes/relatorio`, `/contas/saldo`, `/despesas/resumo` | Login obrigatório. O usuário vem **sempre do login**: não há `usuarioId` no corpo nem na URL, então não dá para pedir ou registrar em nome de outro |
| `/swagger-ui.html`, `/v3/api-docs`, `/actuator/health` (MS1 e MS2) | Abertas: documentação e health check do Compose |

| Situação | Resposta |
|---|---|
| Sem login, ou login em formato errado | 401, com `WWW-Authenticate: Basic` |
| E-mail inexistente ou senha errada | 401 "E-mail ou senha inválidos." (a mesma mensagem nos dois casos) |
| `GET /transacoes/{id}` de uma transação de outro usuário | 403 |
| MS1 fora do ar ao conferir o login | 503 |

**No Swagger do MS2:** clique em **Authorize**, informe o e-mail e a senha do MS1 e depois use o **Try it out** normalmente. Se o login estiver errado, o navegador abre a própria janela "Fazer login", porque a resposta 401 traz `WWW-Authenticate: Basic`. Depois de entrar por essa janela, o navegador guarda o login e passa a mandá-lo sozinho para o `localhost:8082`, então as chamadas funcionam mesmo com o cadeado do Swagger aberto. Sem login nenhum, a resposta continua sendo 401. **Pelo terminal:** veja abaixo.

#### Senha no terminal

Nenhum comando deste README leva a senha escrita na linha de comando: ela iria para o histórico do terminal e apareceria na tela. Leia uma vez com `read -s` e exporte:

```bash
export DEMO_EMAIL=ana.souza@example.com
read -rsp 'Senha: ' DEMO_SENHA; echo
export DEMO_SENHA

curl -s -u "$DEMO_EMAIL:$DEMO_SENHA" http://localhost:8082/contas/saldo; echo
unset DEMO_SENHA   # no fim
```

**Como o MS2 consulta o MS1:** para conferir o dono e o destino das transações, o MS2 chama `GET /usuarios/{id}` com o cabeçalho `X-Servico-Token`, que precisa ser igual ao `SERVICO_TOKEN` do MS1. O token só vale para essa consulta: alterar e excluir exigem o login do próprio usuário. Sem `SERVICO_TOKEN` no `.env`, os dois serviços param logo na subida com uma mensagem dizendo o que falta.

> **Melhoria conhecida:** o `POST /autenticacao` não limita tentativas, então alguém poderia testar muitas senhas seguidas. Em produção, a solução seria limitar as tentativas por e-mail e por IP (por exemplo, bloquear por alguns minutos depois de 5 erros) e registrar as falhas para monitoramento.

## Segurança e segredos

Revisão feita em 11/11/2026 (tarefa 8.7), em **todo o histórico** do Git, não só na versão atual:

| O que foi conferido | Resultado |
|---|---|
| Arquivos `.env`, `.pem`, `.key`, `.p12`, `.jks` em algum commit | Nenhum; o `.gitignore` bloqueia todos, menos o `.env.example` |
| Senha, token ou chave com valor literal em código ou configuração | Nenhum; `application.yml` e `docker-compose.yml` só usam `${...}` |
| URL real da API Mock (com o id do projeto) | Nenhuma; fica só no `.env` |
| `.env.example` | Só valores de exemplo (`troque_aqui`, `SEU_ID`) |
| Senhas nos CSVs de `samples/` e nos testes | Fictícias, de propósito (`Exemplo#2026a`, `Senha@123`) |
| Senha nas respostas e nos logs | Fora: nem a resposta de usuário nem a do login têm senha, o `toString` dos pedidos mascara a senha, o log de violação no banco grava só o nome da restrição, e o login (no MS1 e no filtro do MS2) não registra e-mail nem senha |
| Actuator | Só `/actuator/health` exposto |
| Containers | Portas só em `127.0.0.1`, `no-new-privileges` e `cap_drop: ALL` |

### Como refazer a revisão

Os comandos abaixo varrem **todos os commits de todas as branches** (`--all`), não só a versão atual dos arquivos. Rode na raiz do projeto, no Git Bash:

```bash
# 1. Arquivos de segredo que já foram versionados alguma vez (o esperado é não aparecer nada)
git log --all --name-only --format= | sort -u | grep -iE '\.env$|\.pem$|\.key$|\.p12$|\.jks$|secret|credential'

# 2. Senha, token ou chave com valor literal em arquivos de configuração (ignora ${...} e troque_aqui)
git log --all -p -- '*.yml' '*.yaml' '*.properties' '*.env*' 'docker-compose*' '*.json' | grep -E '^\+' \
  | grep -iE '(password|senha|secret|token|api[_-]?key)[a-z_]*["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"']?[^"'"'"'$?{[:space:]][^"'"'"'[:space:]]{5,}' \
  | grep -viE 'troque_aqui'

# 3. URL real da API Mock (com o id do projeto) em algum commit
git log --all -p | grep -oE '[a-z0-9]{16,}\.mockapi\.io' | sort -u

# 4. Chaves privadas
git log --all -p | grep -cE '^\+-----BEGIN [A-Z ]*PRIVATE KEY-----'
```

Resultado em 11/11/2026: **nada** nos comandos 1, 2 e 3, e `0` no 4. O comando 2 foi conferido com um caso de teste (`password: minhaSenha123` é encontrado; `password: ${...}` e `troque_aqui` não). Senhas em código Java e nos testes ficam a cargo do hook abaixo.

### Hook de pre-commit versionado

O `.githooks/pre-commit` barra o commit quando encontra um arquivo de segredo, uma senha literal em configuração, uma chave privada ou a URL real da Mock. Ele aceita placeholders (`${...}`) e os valores de exemplo, e ignora `src/test/` e `samples/`. Ative uma vez em cada clone:

```bash
git config core.hooksPath .githooks
```

Em um falso positivo confirmado, `git commit --no-verify` pula a checagem só daquele commit.

## Tecnologias

| Camada | Tecnologias |
|---|---|
| Linguagem e framework | Java 25, Spring Boot 3.5 (Web, Data JPA, Validation, Actuator), arquitetura hexagonal no MS2 |
| Banco | PostgreSQL 16, Flyway (migrações), H2 (só nos testes `@DataJpaTest`) |
| Segurança | HTTP Basic, BCrypt, token entre serviços (`X-Servico-Token`), hook de pre-commit contra segredos |
| Integrações | API Mock (mockapi.io) para saldo e limites, BrasilAPI (PTAX) para câmbio, `RestClient` |
| Testes | JUnit 5, Mockito, AssertJ, `@WebMvcTest`, `@DataJpaTest`, `MockRestServiceServer`, JaCoCo |
| Documentação e entrega | springdoc/Swagger UI, Docker e Docker Compose, Git com PRs empilhados, Jira (Kanban) |