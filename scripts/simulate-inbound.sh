#!/usr/bin/env bash
# Simulates WhatsApp Cloud API webhooks hitting the local service (no Meta needed).
#
#   ./scripts/simulate-inbound.sh text "oi"
#   ./scripts/simulate-inbound.sh button ANTICIPATE "Antecipar boletos"
#   ./scripts/simulate-inbound.sh text "10 mil"
#   ./scripts/simulate-inbound.sh button CONFIRM_OFFER "Confirmar"
#   ./scripts/simulate-inbound.sh image            # selfie (with the mock adapter: "image small" = low quality)
#   ./scripts/simulate-inbound.sh status <wamid> delivered
#
# Env: BASE_URL (default http://localhost:8080/api), FROM (default 15555550101),
#      APP_SECRET (signs X-Hub-Signature-256 when the service has whatsapp.app-secret set),
#      PHONE_NUMBER_ID (default 1357259444133753)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
FROM="${FROM:-15555550101}"
PHONE_NUMBER_ID="${PHONE_NUMBER_ID:-1357259444133753}"
NOW="$(date +%s)"
WAMID="wamid.SIM_$(uuidgen | tr -d '-')"

case "${1:-}" in
  text)
    VALUE=$(printf '"contacts":[{"profile":{"name":"Simulator"},"wa_id":"%s"}],"messages":[{"from":"%s","id":"%s","timestamp":"%s","type":"text","text":{"body":"%s"}}]' \
      "$FROM" "$FROM" "$WAMID" "$NOW" "$2") ;;
  button)
    VALUE=$(printf '"messages":[{"from":"%s","id":"%s","timestamp":"%s","type":"interactive","interactive":{"type":"button_reply","button_reply":{"id":"%s","title":"%s"}}}]' \
      "$FROM" "$WAMID" "$NOW" "$2" "${3:-$2}") ;;
  image)
    MEDIA_ID="mock-selfie-${2:-ok}-$(uuidgen | tr -d '-' | cut -c1-8)"
    VALUE=$(printf '"messages":[{"from":"%s","id":"%s","timestamp":"%s","type":"image","image":{"id":"%s","mime_type":"image/jpeg","sha256":"sim"}}]' \
      "$FROM" "$WAMID" "$NOW" "$MEDIA_ID") ;;
  status)
    VALUE=$(printf '"statuses":[{"id":"%s","status":"%s","timestamp":"%s","recipient_id":"%s"}]' "$2" "$3" "$NOW" "$FROM") ;;
  *)
    sed -n '2,13p' "$0"; exit 1 ;;
esac

BODY=$(printf '{"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"field":"messages","value":{"messaging_product":"whatsapp","metadata":{"display_phone_number":"15550000000","phone_number_id":"%s"},%s}}]}]}' \
  "$PHONE_NUMBER_ID" "$VALUE")

SIGNATURE_HEADER=()
if [[ -n "${APP_SECRET:-}" ]]; then
  SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$APP_SECRET" | sed 's/^.* //')
  SIGNATURE_HEADER=(-H "X-Hub-Signature-256: sha256=$SIG")
fi

curl -sS -o /dev/null -w "webhook → HTTP %{http_code}\n" -X POST "$BASE_URL/webhooks/whatsapp" \
  -H "Content-Type: application/json" ${SIGNATURE_HEADER[@]+"${SIGNATURE_HEADER[@]}"} --data-binary "$BODY"
