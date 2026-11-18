#!/usr/bin/env bash
# Carrega dados de exemplo para a demonstração (tarefa 9.3).
# Uso: DEMO_EMAIL=ana.souza@example.com bash docker/demo/carregar-dados-demo.sh [usuarioDestinoId]
# A senha vem de DEMO_SENHA ou é pedida na hora (não fica no arquivo nem no histórico).
# O usuário do login e o destino precisam existir no MS1 e ter conta na API Mock.
set -euo pipefail

DESTINO="${1:-4}"
DEMO_EMAIL="${DEMO_EMAIL:?defina DEMO_EMAIL com o e-mail do usuário da demo}"
MS1_URL="${MS1_URL:-http://localhost:8081}"
MS2_URL="${MS2_URL:-http://localhost:8082}"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"

echo "== Conferindo se os serviços estão no ar"
for url in "$MS1_URL" "$MS2_URL"; do
  if ! curl -fsS "$url/actuator/health" | grep -q UP; then
    echo "   $url não respondeu UP. Suba com 'docker compose up -d' (ou pela IDE) e tente de novo."
    exit 1
  fi
  echo "   $url: UP"
done

# Login (tarefa 6.1): o MS1 confere a senha e devolve o id do usuário.
if [ -z "${DEMO_SENHA-}" ]; then
  read -rsp "Senha de $DEMO_EMAIL: " DEMO_SENHA
  echo
fi
echo "== MS1: login de $DEMO_EMAIL"
login="$(curl -sS -w ' HTTP_%{http_code}' -X POST "$MS1_URL/autenticacao" \
  -H 'Content-Type: application/json' -d "{\"email\": \"$DEMO_EMAIL\", \"senha\": \"$DEMO_SENHA\"}")"
if [ "${login##* HTTP_}" != "200" ]; then
  echo "   Login recusado (HTTP ${login##* HTTP_}): confira o e-mail e a senha."
  exit 1
fi
USUARIO="$(grep -o '"id":[0-9]*' <<< "$login" | head -1 | cut -d: -f2)"
echo "   autenticado como usuário $USUARIO"
# Login básico em todas as chamadas ao MS2 (tarefa 6.2).
LOGIN=(-u "$DEMO_EMAIL:$DEMO_SENHA")

echo "== MS1: importando samples/usuarios-exemplo.csv"
curl -sS -X POST "$MS1_URL/usuarios/importacao" -F "arquivo=@$RAIZ/samples/usuarios-exemplo.csv"
echo

# Cada linha: tipo|valor|categoria|destino|descricao
# Descrições sem acento: o curl do Git Bash no Windows pode não enviar o acento em UTF-8.
TRANSACOES=(
  "DEPOSITO|500.00|||Deposito inicial da demo"
  "COMPRA|45.90|ALIMENTACAO||Mercado"
  "COMPRA|32.50|ALIMENTACAO||Padaria"
  "COMPRA|18.00|TRANSPORTE||Aplicativo de transporte"
  "COMPRA|60.00|LAZER||Cinema"
  "RETIRADA|50.00|OUTROS||Saque"
  "TRANSFERENCIA|25.00|OUTROS|$DESTINO|Transferencia para o destino"
  "COMPRA|99999.00|MORADIA||Compra acima do limite (deve ser REJEITADA)"
)

# A primeira chamada à API Mock pode passar do tempo limite de 2 s (conexão nova, proxy).
# Consultar o saldo antes aquece a conexão e confirma que o usuário tem conta na Mock.
echo "== MS2: conferindo a conta do usuário $USUARIO na API Mock"
for tentativa in 1 2 3; do
  codigo_saldo="$(curl -sS -o /dev/null -w '%{http_code}' "${LOGIN[@]}" "$MS2_URL/contas/saldo" || true)"
  [ "$codigo_saldo" = "200" ] && break
  echo "   tentativa $tentativa: HTTP $codigo_saldo"
  sleep 2
done
if [ "$codigo_saldo" != "200" ]; then
  echo "   O saldo do usuário $USUARIO não respondeu 200 (HTTP $codigo_saldo): confira se ele tem conta na Mock."
  exit 1
fi
echo "   conta encontrada"

echo "== MS2: registrando transações do usuário $USUARIO"
for linha in "${TRANSACOES[@]}"; do
  IFS='|' read -r tipo valor categoria destino descricao <<< "$linha"
  corpo="{\"tipo\": \"$tipo\", \"valor\": $valor"
  [ -n "$categoria" ] && corpo="$corpo, \"categoria\": \"$categoria\""
  [ -n "$destino" ] && corpo="$corpo, \"usuarioDestinoId\": $destino"
  corpo="$corpo, \"descricao\": \"$descricao\"}"

  resposta="$(curl -sS -w ' HTTP_%{http_code}' "${LOGIN[@]}" -X POST "$MS2_URL/transacoes" \
    -H 'Content-Type: application/json' -d "$corpo")"
  codigo="${resposta##* HTTP_}"
  status="$(grep -o '"status":"[A-Z]*"' <<< "$resposta" | head -1 | cut -d'"' -f4 || true)"
  motivo="$(grep -o '"motivoRejeicao":"[A-Z_]*"' <<< "$resposta" | head -1 | cut -d'"' -f4 || true)"
  printf '   %-13s %10s  HTTP %s  %s %s\n' "$tipo" "$valor" "$codigo" "${status:-}" "${motivo:-}"
  if [ "$codigo" != "201" ]; then
    detalhe="$(grep -o '"detail":"[^"]*"' <<< "$resposta" | head -1 | cut -d'"' -f4 || true)"
    echo "      motivo: ${detalhe:-${resposta% HTTP_*}}"
  fi
done

echo "== MS2: saldo do usuário $USUARIO"
curl -sS "${LOGIN[@]}" "$MS2_URL/contas/saldo"
echo
echo "== MS2: resumo de despesas do mês atual"
curl -sS "${LOGIN[@]}" "$MS2_URL/despesas/resumo"
echo
