#!/bin/sh
# Com BACKEND_URL (ex.: http://api.railway.internal:8080 ou https://api-production.up.railway.app), o nginx repassa /api, /oauth2 e /login/oauth2 para a API.
# Assim o navegador fala só com o domínio do front e os cookies SameSite=Strict funcionam.
# API_UPSTREAM continua valendo como nome antigo. Sem nenhuma das duas, o arquivo fica vazio e o front sobe sozinho.
set -eu

OUT="${RHHUB_API_CONF:-/etc/nginx/rhhub-api.conf}"
: > "$OUT"

BACKEND="${BACKEND_URL:-${API_UPSTREAM:-}}"
if [ -z "$BACKEND" ]; then
  echo "rhhub: BACKEND_URL não definida, /api não será repassado"
  exit 0
fi

# Aceita o valor como o Railway mostra (api.railway.internal:8080, sem o http://) e limpa aspas e espaços.
upstream="$(printf '%s' "$BACKEND" | tr -d '"'"'"' \t\r\n')"
upstream="${upstream%/}"
case "$upstream" in
  http://*|https://*) ;;
  *://*) echo "rhhub: ERRO: BACKEND_URL só aceita http:// ou https:// (recebi: $upstream). /api NÃO será repassado." >&2; exit 0 ;;
  *) upstream="http://$upstream" ;;
esac
# Recusa o que quebraria o arquivo do nginx. O front continua no ar, só sem o /api.
case "$upstream" in
  *[!A-Za-z0-9.:/_-]*) echo "rhhub: ERRO: BACKEND_URL tem caracteres inválidos. /api NÃO será repassado." >&2; exit 0 ;;
esac

# Host que o back end recebe: o do próprio BACKEND_URL. Com a URL pública do Railway o roteador escolhe o serviço pelo
# Host, então repassar o do front mandaria a chamada de volta para o front.
backend_host="${upstream#*://}"
backend_host="${backend_host%%/*}"

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
  proxy_set_header Host $backend_host;
  proxy_ssl_server_name on;
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
