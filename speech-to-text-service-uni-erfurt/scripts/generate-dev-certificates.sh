#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CERT_DIR="${PROJECT_DIR}/certificates/dev"

mkdir -p "${CERT_DIR}"

ROOT_KEY="${CERT_DIR}/root-ca.key.pem"
ROOT_CERT="${CERT_DIR}/root-ca.cert.pem"
SERVER_KEY="${CERT_DIR}/speech-to-text-service.key.pem"
SERVER_CSR="${CERT_DIR}/speech-to-text-service.csr.pem"
SERVER_CERT="${CERT_DIR}/speech-to-text-service.cert.pem"
SERVER_EXT="${CERT_DIR}/speech-to-text-service.ext"
CLIENT_KEY="${CERT_DIR}/speech-to-text-client.key.pem"
CLIENT_CSR="${CERT_DIR}/speech-to-text-client.csr.pem"
CLIENT_CERT="${CERT_DIR}/speech-to-text-client.cert.pem"
CLIENT_EXT="${CERT_DIR}/speech-to-text-client.ext"

cat > "${SERVER_EXT}" <<'EOF'
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,IP:127.0.0.1
EOF

cat > "${CLIENT_EXT}" <<'EOF'
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth
EOF

openssl genrsa -out "${ROOT_KEY}" 4096
openssl req -x509 -new -nodes -key "${ROOT_KEY}" -sha256 -days 3650 \
  -out "${ROOT_CERT}" \
  -subj "/C=DE/ST=HE/L=Dauernheim/O=NKO Dev Studio/OU=Development/CN=speech-to-text-root-ca"

openssl genrsa -out "${SERVER_KEY}" 4096
openssl req -new -key "${SERVER_KEY}" -out "${SERVER_CSR}" \
  -subj "/C=DE/ST=HE/L=Dauernheim/O=NKO Dev Studio/OU=Development/CN=speech-to-text-service"
openssl x509 -req -in "${SERVER_CSR}" -CA "${ROOT_CERT}" -CAkey "${ROOT_KEY}" \
  -CAcreateserial -out "${SERVER_CERT}" -days 825 -sha256 -extfile "${SERVER_EXT}"

openssl genrsa -out "${CLIENT_KEY}" 4096
openssl req -new -key "${CLIENT_KEY}" -out "${CLIENT_CSR}" \
  -subj "/C=DE/ST=HE/L=Dauernheim/O=NKO Dev Studio/OU=Development/CN=speech-to-text-client"
openssl x509 -req -in "${CLIENT_CSR}" -CA "${ROOT_CERT}" -CAkey "${ROOT_KEY}" \
  -CAcreateserial -out "${CLIENT_CERT}" -days 825 -sha256 -extfile "${CLIENT_EXT}"

rm -f "${SERVER_CSR}" "${CLIENT_CSR}" "${SERVER_EXT}" "${CLIENT_EXT}" "${CERT_DIR}/root-ca.cert.srl"

cat <<EOF
Generated development certificates in:
  ${CERT_DIR}

Root CA:
  ${ROOT_CERT}

gRPC server:
  ${SERVER_CERT}
  ${SERVER_KEY}

Client:
  ${CLIENT_CERT}
  ${CLIENT_KEY}
EOF
