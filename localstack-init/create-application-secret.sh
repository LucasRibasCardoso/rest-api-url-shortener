#!/bin/bash
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
SECRET_NAME="${APPLICATION_SECRET_NAME:-/url-shortener/dev/application}"
SECRETS_FILE="${APPLICATION_SECRETS_FILE:-/etc/localstack/init/ready.d/secrets/application-dev.json}"

# Verifica se o awslocal está instalado
if ! command -v awslocal >/dev/null 2>&1; then
    echo "Erro: O comando 'awslocal' não foi encontrado." >&2
    echo "Para este projeto, é obrigatório o uso do wrapper do LocalStack." >&2
    echo "Instale executando: pip install awscli-local" >&2
    exit 1
fi

if [ ! -f "$SECRETS_FILE" ]; then
    echo "Erro: arquivo de secrets não encontrado em '$SECRETS_FILE'." >&2
    exit 1
fi

if awslocal secretsmanager describe-secret \
    --secret-id "$SECRET_NAME" \
    --region "$REGION" >/dev/null 2>&1; then
    awslocal secretsmanager put-secret-value \
        --secret-id "$SECRET_NAME" \
        --secret-string "file://$SECRETS_FILE" \
        --region "$REGION"

    echo "Secret '$SECRET_NAME' atualizado com sucesso."
    exit 0
fi

awslocal secretsmanager create-secret \
    --name "$SECRET_NAME" \
    --description "Sensitive configuration for URL Shortener development environment" \
    --secret-string "file://$SECRETS_FILE" \
    --region "$REGION"

echo "Secret '$SECRET_NAME' criado com sucesso."
