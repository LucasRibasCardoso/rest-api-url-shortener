#!/bin/bash
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
SES_FROM_EMAIL="${SES_FROM_EMAIL:-no-reply@url-shortener.local}"

# Verifica se o awslocal está instalado
if ! command -v awslocal >/dev/null 2>&1; then
    echo "Erro: O comando 'awslocal' não foi encontrado." >&2
    echo "Para este projeto, é obrigatório o uso do wrapper do LocalStack." >&2
    echo "Instale executando: pip install awscli-local" >&2
    exit 1
fi

identity_exists() {
    awslocal ses list-identities \
        --identity-type EmailAddress \
        --region "$REGION" \
        --query "Identities[?@ == '$SES_FROM_EMAIL'] | [0]" \
        --output text 2>/dev/null | grep -Fxq "$SES_FROM_EMAIL"
}

if identity_exists; then
    echo "SES email identity '$SES_FROM_EMAIL' already exists. Skipping verification."
    exit 0
fi

awslocal ses verify-email-identity \
    --email-address "$SES_FROM_EMAIL" \
    --region "$REGION"

echo "SES email identity '$SES_FROM_EMAIL' created and verified."