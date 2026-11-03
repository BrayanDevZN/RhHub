#!/bin/sh
# Monta a credencial SASL a partir de KAFKA_API_KEY em tempo de execução,
# para que a chave nunca fique gravada na imagem.
set -eu

if [ -z "${KAFKA_API_KEY:-}" ]; then
  echo "KAFKA_API_KEY nao definida: o Kafka do RhHub nao sobe sem chave." >&2
  exit 1
fi

# Aceita só caracteres seguros (a chave entra entre aspas no JAAS) e exige tamanho mínimo.
case "$KAFKA_API_KEY" in
  *[!A-Za-z0-9_-]*)
    echo "KAFKA_API_KEY so pode ter letras, numeros, '_' e '-'." >&2
    exit 1
    ;;
esac
if [ "${#KAFKA_API_KEY}" -lt 32 ]; then
  echo "KAFKA_API_KEY precisa ter pelo menos 32 caracteres." >&2
  exit 1
fi

KAFKA_API_USER="${KAFKA_API_USER:-rhhub}"

# Um único usuário (o do sistema); o mesmo login é usado entre brokers.
# A imagem exige o JAAS em arquivo, apontado por KAFKA_OPTS. Fica em /tmp, só do dono.
JAAS_FILE=/tmp/rhhub_kafka_jaas.conf
umask 077
cat > "$JAAS_FILE" <<EOF
KafkaServer {
  org.apache.kafka.common.security.plain.PlainLoginModule required
  username="${KAFKA_API_USER}"
  password="${KAFKA_API_KEY}"
  user_${KAFKA_API_USER}="${KAFKA_API_KEY}";
};
EOF
export KAFKA_OPTS="${KAFKA_OPTS:-} -Djava.security.auth.login.config=${JAAS_FILE}"

# Endereço que os clientes recebem para reconectar. É o que o Kafka devolve depois do primeiro contato, então tem de
# ser alcançável por quem conecta: o nome do serviço (Compose, rede privada do Railway) ou, para acesso de fora, o
# host e a porta do TCP Proxy público (KAFKA_ADVERTISED_HOST e KAFKA_ADVERTISED_PORT).
ADV_HOST="${KAFKA_ADVERTISED_HOST:-kafka}"
ADV_PORT="${KAFKA_ADVERTISED_PORT:-9092}"
case "$ADV_HOST" in
  *[!A-Za-z0-9.-]*|"") echo "KAFKA_ADVERTISED_HOST so pode ter letras, numeros, '.' e '-'." >&2; exit 1 ;;
esac
case "$ADV_PORT" in
  *[!0-9]*|"") echo "KAFKA_ADVERTISED_PORT precisa ser um numero." >&2; exit 1 ;;
esac
if [ "$ADV_PORT" -lt 1 ] || [ "$ADV_PORT" -gt 65535 ]; then
  echo "KAFKA_ADVERTISED_PORT fora do intervalo 1-65535." >&2
  exit 1
fi
export KAFKA_ADVERTISED_LISTENERS="SASL_PLAINTEXT://${ADV_HOST}:${ADV_PORT}"
echo "kafka: anunciando ${ADV_HOST}:${ADV_PORT} (o broker escuta na 9092; o TCP Proxy do Railway deve apontar para a 9092)"

# Onde o Kafka grava os dados. Um volume novo vem com a pasta lost+found na raiz, e o Kafka recusa subir se achar nela
# qualquer coisa que não seja tópico ("log directories should only contain Kafka topic data"). Por isso os dados ficam
# numa subpasta do volume, nunca na raiz dele.
VOLUME_ROOT=/var/lib/kafka/data
LOG_DIRS="${KAFKA_LOG_DIRS:-$VOLUME_ROOT/kafka-logs}"
case "$LOG_DIRS" in
  "$VOLUME_ROOT"|"$VOLUME_ROOT"/)
    LOG_DIRS="$VOLUME_ROOT/kafka-logs"
    echo "kafka: KAFKA_LOG_DIRS apontava para a raiz do volume (tem lost+found); usando $LOG_DIRS" >&2
    ;;
esac
export KAFKA_LOG_DIRS="$LOG_DIRS"
echo "kafka: dados em $LOG_DIRS"

# Passa para o entrypoint original da imagem apache/kafka.
exec /__cacert_entrypoint.sh "$@"
