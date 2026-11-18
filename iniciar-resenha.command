#!/bin/bash
set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR"
if [ -f "$PROJECT_DIR/Resenha/pom.xml" ]; then
  BACKEND_DIR="$PROJECT_DIR/Resenha"
fi
FRONT_DIR="$PROJECT_DIR/resenha-front"
FRONT_URL="http://localhost:5510"
API_URL="http://localhost:8081"
BACK_PID=""
FRONT_PID=""
STARTED_SERVICES=()

echo "=========================================================="
echo "               INICIANDO O RESENHA                        "
echo "=========================================================="

if [ ! -x "$PROJECT_DIR/mvnw" ]; then
  echo "❌ Não encontrei o Maven Wrapper em: $PROJECT_DIR/mvnw"
  exit 1
fi
if [ ! -f "$FRONT_DIR/index.html" ]; then
  echo "❌ Não encontrei a interface em: $FRONT_DIR"
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ Python 3 é necessário para servir a interface."
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "❌ Docker não está instalado ou não está disponível no PATH."
  exit 1
fi

cleanup() {
  echo ""
  echo "Encerrando os processos iniciados por este lançador..."
  if [ -n "$BACK_PID" ]; then
    kill "$BACK_PID" 2>/dev/null || true
  fi
  if [ -n "$FRONT_PID" ]; then
    kill "$FRONT_PID" 2>/dev/null || true
  fi
  if [ -n "$BACK_PID" ] || [ -n "$FRONT_PID" ]; then
    wait 2>/dev/null || true
  fi
  if [ ${#STARTED_SERVICES[@]} -gt 0 ]; then
    docker compose -f "$PROJECT_DIR/docker-compose.yml" stop "${STARTED_SERVICES[@]}" >/dev/null 2>&1 || true
    echo "✓ Containers iniciados por este lançador foram parados."
  fi
}
trap cleanup EXIT INT TERM

if ! docker info >/dev/null 2>&1; then
  echo "→ Abrindo o Docker Desktop..."
  if ! open -a Docker; then
    echo "❌ Não consegui abrir o Docker Desktop. Inicie-o manualmente e tente novamente."
    exit 1
  fi
  echo "→ Aguardando o Docker ficar pronto..."
  for i in {1..120}; do
    if docker info >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  if ! docker info >/dev/null 2>&1; then
    echo "❌ O Docker não iniciou. Abra o Docker Desktop e execute este arquivo novamente."
    exit 1
  fi
fi

COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
RUNNING_SERVICES="$(docker compose -f "$COMPOSE_FILE" ps --services --status running 2>/dev/null || true)"
for service in postgres redis; do
  if ! printf '%s\n' "$RUNNING_SERVICES" | grep -qx "$service"; then
    STARTED_SERVICES+=("$service")
  fi
done

echo "→ Iniciando PostgreSQL e Redis..."
docker compose -f "$COMPOSE_FILE" up -d postgres redis

echo "→ Aguardando o PostgreSQL..."
DB_READY=false
for i in {1..60}; do
  if docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U postgres -d resenha >/dev/null 2>&1; then
    DB_READY=true
    break
  fi
  sleep 1
done
if [ "$DB_READY" != true ]; then
  echo "❌ O PostgreSQL não ficou pronto a tempo."
  exit 1
fi
echo "✓ PostgreSQL pronto."

if curl -fsS --max-time 2 "$FRONT_URL" >/dev/null 2>&1; then
  echo "✓ Interface já está ativa em $FRONT_URL"
else
  echo "→ Iniciando interface web na porta 5510..."
  (cd "$FRONT_DIR" && python3 -m http.server 5510 --bind 127.0.0.1) &
  FRONT_PID=$!
fi

if curl -sS --max-time 2 "$API_URL/auth/login" >/dev/null 2>&1; then
  echo "✓ Backend já está ativo em $API_URL"
else
  echo "→ Iniciando backend Spring Boot na porta 8081..."
  (
    cd "$PROJECT_DIR"
    export SERVER_PORT=8081
    export DB_HOST=localhost
    export DB_PORT=5432
    export DB_NAME_SPRING=resenha
    export DB_USERNAME=postgres
    export DB_PASSWORD_POSTGRES=postgres
    ./mvnw -f "$BACKEND_DIR/pom.xml" spring-boot:run
  ) &
  BACK_PID=$!
fi

echo "→ Aguardando a interface e o backend..."
FRONT_READY=false
API_READY=false
for i in {1..120}; do
  if [ "$FRONT_READY" != true ] && curl -fsS --max-time 1 "$FRONT_URL" >/dev/null 2>&1; then
    FRONT_READY=true
    echo "✓ Interface pronta."
  fi
  if [ "$API_READY" != true ] && curl -sS --max-time 1 "$API_URL/auth/login" >/dev/null 2>&1; then
    API_READY=true
    echo "✓ Backend pronto."
  fi
  if [ "$FRONT_READY" = true ] && [ "$API_READY" = true ]; then
    break
  fi
  sleep 1
done

if [ "$FRONT_READY" != true ] || [ "$API_READY" != true ]; then
  echo "❌ O front ou o backend não ficou pronto. Confira as mensagens acima."
  exit 1
fi

echo ""
echo "=========================================================="
echo "  RESENHA ONLINE — abrindo $FRONT_URL"
echo "  Feche esta janela para encerrar os serviços iniciados."
echo "=========================================================="
open "$FRONT_URL"

if [ -n "$BACK_PID" ]; then
  wait "$BACK_PID"
elif [ -n "$FRONT_PID" ]; then
  wait "$FRONT_PID"
else
  echo "Os serviços já estavam rodando antes deste lançador."
  while true; do sleep 3600; done
fi
