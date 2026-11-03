#!/bin/sh
# Com API_UPSTREAM (ex.: http://api.railway.internal:8080), o nginx repassa /api, /oauth2 e /login/oauth2 para a API.
# Assim o navegador fala só com o domínio do front e os cookies SameSite=Strict funcionam.
# Sem API_UPSTREAM o arquivo fica vazio e o front sobe sozinho.
set -eu

OUT="${RHHUB_API_CONF:-/etc/nginx/rhhub-api.conf}"
: > "$OUT"

if [ -z "${API_UPSTREAM:-}" ]; then
  echo "rhhub: API_UPSTREAM não definida, /api não será repassado"
  exit 0
fi

# Só o esquema e o host:porta (sem barra no fim). Recusa caracteres que quebrariam o arquivo do nginx.
upstream="${API_UPSTREAM%/}"
case "$upstream" in
  http://*|https://*) ;;
  *) echo "rhhub: API_UPSTREAM precisa começar com http:// ou https://" >&2; exit 1 ;;
esac
case "$upstream" in
  *[!A-Za-z0-9.:/_-]*) echo "rhhub: API_UPSTREAM tem caracteres inválidos" >&2; exit 1 ;;
esac

# O DNS do nginx é o do container (resolvido de novo a cada 10 s, para a API poder trocar de IP).
dns="$(awk '/^nameserver/ {print $2; exit}' /etc/resolv.conf 2>/dev/null || true)"
case "$dns" in
  *:*) dns="[$dns]" ;;
esac
[ -n "$dns" ] || dns="127.0.0.11"

# A rede privada do Railway pode ter só IPv6 (AAAA): com ipv6=off o nome "não existe". Só desliga sem suporte no kernel.
ipv6opt=""
[ -f /proc/net/if_inet6 ] || ipv6opt=" ipv6=off"

proxy() {
  cat <<CONF
location $1 {
  resolver $dns valid=10s$ipv6opt;
  set \$rhhub_api $upstream;
  proxy_pass \$rhhub_api;
  proxy_set_header Host \$host;
  proxy_set_header X-Forwarded-For \$rhhub_client_ip;
  proxy_set_header X-Forwarded-Proto \$http_x_forwarded_proto;
  client_max_body_size 8m;
  proxy_read_timeout 90s;
}
CONF
}

{
  proxy /api/
  proxy /oauth2/
  proxy /login/oauth2/
} >> "$OUT"

echo "rhhub: /api repassado para $upstream (DNS $dns)"
