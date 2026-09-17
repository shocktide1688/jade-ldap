#!/usr/bin/env bash
set -euo pipefail

ldap_host="${LDAP_HOST:-127.0.0.1}"
ldap_port="${LDAP_PORT:-1389}"
ldaps_port="${LDAPS_PORT:-1636}"
base_dn="${LDAP_BASE_DN:-dc=jade,dc=local}"
admin_dn="${LDAP_ADMIN_DN:-cn=admin,dc=jade,dc=local}"
admin_password="${LDAP_ADMIN_PASSWORD:-local-container-admin-password}"
requests="${LDAP_LOAD_REQUESTS:-200}"
concurrency="${LDAP_LOAD_CONCURRENCY:-20}"

for command in ldapsearch ldapcompare; do
  command -v "$command" >/dev/null || { echo "$command is required" >&2; exit 2; }
done

echo "[compat] LDAP simple bind/search"
ldapsearch -LLL -x -H "ldap://${ldap_host}:${ldap_port}" -D "$admin_dn" -w "$admin_password" \
  -b "$base_dn" -s base '(objectClass=*)' dn >/dev/null

echo "[compat] StartTLS required mode"
LDAPTLS_REQCERT=never ldapsearch -LLL -x -ZZ -H "ldap://${ldap_host}:${ldap_port}" \
  -D "$admin_dn" -w "$admin_password" -b "$base_dn" -s base '(objectClass=*)' dn >/dev/null

echo "[compat] LDAPS"
LDAPTLS_REQCERT=never ldapsearch -LLL -x -H "ldaps://${ldap_host}:${ldaps_port}" \
  -D "$admin_dn" -w "$admin_password" -b "$base_dn" -s base '(objectClass=*)' dn >/dev/null

echo "[compat] RFC 2696 paged search"
ldapsearch -LLL -x -H "ldap://${ldap_host}:${ldap_port}" -D "$admin_dn" -w "$admin_password" \
  -E pr=2/noprompt -b "$base_dn" '(objectClass=*)' dn >/dev/null

echo "[compat] compare"
set +e
ldapcompare -x -H "ldap://${ldap_host}:${ldap_port}" -D "$admin_dn" -w "$admin_password" \
  "${base_dn}" "objectClass:domain" >/dev/null
compare_exit=$?
set -e
if [[ "$compare_exit" -ne 6 ]]; then
  echo "ldapcompare expected LDAP_COMPARE_TRUE (exit 6), got ${compare_exit}" >&2
  exit "$compare_exit"
fi

echo "[load] ${requests} authenticated subtree searches, concurrency=${concurrency}"
seq "$requests" | xargs -P "$concurrency" -I '{}' sh -c \
  'ldapsearch -LLL -x -H "$1" -D "$2" -w "$3" -b "$4" "(objectClass=*)" dn >/dev/null' \
  _ "ldap://${ldap_host}:${ldap_port}" "$admin_dn" "$admin_password" "$base_dn"

echo "LDAP_COMPAT_LOAD=ok"
