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

# Endereço que os clientes recebem para reconectar (nome do serviço no Compose).
export KAFKA_ADVERTISED_LISTENERS="SASL_PLAINTEXT://${KAFKA_ADVERTISED_HOST:-kafka}:9092"

# Passa para o entrypoint original da imagem apache/kafka.
exec /__cacert_entrypoint.sh "$@"
