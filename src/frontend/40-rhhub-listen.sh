#!/bin/sh
# Faz o nginx ouvir na 9000 e também na PORT injetada pela plataforma,
# para o tráfego chegar seja qual for a porta de destino configurada no domínio.
set -eu

OUT="${RHHUB_LISTEN_CONF:-/etc/nginx/rhhub-listen.conf}"
ports="9000"
if [ -n "${PORT:-}" ] && [ "$PORT" != "9000" ]; then
  ports="$ports $PORT"
fi

# IPv6 só quando o kernel suporta; senão o nginx falha ao abrir o socket
ipv6="não"
[ -f /proc/net/if_inet6 ] && ipv6="sim"

: > "$OUT"
for p in $ports; do
  printf 'listen %s;\n' "$p" >> "$OUT"
  [ "$ipv6" = "sim" ] && printf 'listen [::]:%s;\n' "$p" >> "$OUT"
done

echo "rhhub: nginx ouvindo nas portas: $ports (IPv6: $ipv6, PORT=${PORT:-não definida})"
