#!/bin/sh
# Hook PreToolUse do Claude Code: pausa a ferramenta até o usuário decidir no app.
#
# Por que arquivos em /pocket-bridge: o hook roda dentro do proot e o app Android
# roda fora dele; a pasta montada é o único canal compartilhado entre os dois.
# Protocolo: este script grava <id>.request (JSON do hook) e espera <id>.response
# com "allow" ou "deny", escrito pelo ClaudeRuntimeBridge.
bridge=/pocket-bridge
id="$(date +%s%N)-$$"

# Grava em .tmp e renomeia para o app nunca ler um JSON pela metade.
cat > "$bridge/$id.tmp"
mv "$bridge/$id.tmp" "$bridge/$id.request"

# ~10 min (2000 x 0,3 s). Sem resposta, nega: é mais seguro do que liberar.
waited=0
while [ ! -s "$bridge/$id.response" ] && [ "$waited" -lt 2000 ]; do
  sleep 0.3
  waited=$((waited + 1))
done

decision="$(cat "$bridge/$id.response" 2>/dev/null)"
rm -f "$bridge/$id.response" "$bridge/$id.request" "$bridge/$id.tmp"

if [ "$decision" = "allow" ]; then
  printf '%s\n' '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow"}}'
else
  printf '%s\n' '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"The user rejected this command in Mobile Harness."}}'
fi
