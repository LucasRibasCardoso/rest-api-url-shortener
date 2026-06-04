#!/bin/bash
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ENDPOINT_URL="${LOCALSTACK_ENDPOINT_URL:-http://localhost:4566}"

URL_TABLE_NAME="${URL_TABLE_NAME:-url}"
COUNTER_TABLE_NAME="${COUNTER_TABLE_NAME:-url_counter}"
URL_COUNTER_NAME="${URL_COUNTER_NAME:-url_short_code}"

# Determina qual CLI usar para interagir com o DynamoDB
if command -v awslocal >/dev/null 2>&1; then
    DDB_CMD=(awslocal dynamodb)
elif command -v aws >/dev/null 2>&1; then
    DDB_CMD=(aws --endpoint-url "$ENDPOINT_URL" dynamodb)
else
    echo "Error: neither 'awslocal' nor 'aws' CLI was found in PATH." >&2
    exit 1
fi

# Informa o usuário sobre o processo de criação das tabelas
echo "===================================="
echo "Starting DynamoDB table creation..."
echo "Region: $REGION"
echo "Endpoint: $ENDPOINT_URL"
echo "===================================="

# Cria a tabela de URLs, se ela ainda não existir
create_url_table() {
    if "${DDB_CMD[@]}" describe-table \
        --table-name "$URL_TABLE_NAME" \
        --region "$REGION" >/dev/null 2>&1; then

        echo "DynamoDB table '$URL_TABLE_NAME' already exists. Skipping creation."
        return
    fi

    "${DDB_CMD[@]}" create-table \
        --table-name "$URL_TABLE_NAME" \
        --attribute-definitions \
            AttributeName=shortCode,AttributeType=S \
            AttributeName=userId,AttributeType=S \
            AttributeName=createdAtShortCodeGsi,AttributeType=S \
            AttributeName=statusCreatedAtShortCodeGsi,AttributeType=S \
        --key-schema AttributeName=shortCode,KeyType=HASH \
        --global-secondary-indexes '[
            {
                "IndexName": "user-index",
                "KeySchema": [
                    {"AttributeName": "userId", "KeyType": "HASH"},
                    {"AttributeName": "createdAtShortCodeGsi", "KeyType": "RANGE"}
                ],
                "Projection": {"ProjectionType": "ALL"}
            },
            {
                "IndexName": "user-status-index",
                "KeySchema": [
                    {"AttributeName": "userId", "KeyType": "HASH"},
                    {"AttributeName": "statusCreatedAtShortCodeGsi", "KeyType": "RANGE"}
                ],
                "Projection": {"ProjectionType": "ALL"}
            }
        ]' \
        --billing-mode PAY_PER_REQUEST \
        --region "$REGION"

    echo "DynamoDB table '$URL_TABLE_NAME' created successfully."
}

# Cria a tabela de contadores, se ela ainda não existir
create_counter_table() {
    if "${DDB_CMD[@]}" describe-table \
        --table-name "$COUNTER_TABLE_NAME" \
        --region "$REGION" >/dev/null 2>&1; then

        echo "DynamoDB table '$COUNTER_TABLE_NAME' already exists. Skipping creation."
        return
    fi

    "${DDB_CMD[@]}" create-table \
        --table-name "$COUNTER_TABLE_NAME" \
        --attribute-definitions AttributeName=counterName,AttributeType=S \
        --key-schema AttributeName=counterName,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST \
        --region "$REGION"

    echo "DynamoDB table '$COUNTER_TABLE_NAME' created successfully."
}

# Adiciona um item inicial para o contador de URL, se ele ainda não existir
seed_url_counter() {
    echo "Creating initial counter item '$URL_COUNTER_NAME' if it does not exist..."

    "${DDB_CMD[@]}" put-item \
        --table-name "$COUNTER_TABLE_NAME" \
        --item "{
            \"counterName\": {\"S\": \"$URL_COUNTER_NAME\"},
            \"currentValue\": {\"N\": \"0\"},
            \"description\": {\"S\": \"Global counter used to allocate URL short code ID blocks\"}
        }" \
        --condition-expression "attribute_not_exists(counterName)" \
        --region "$REGION" >/dev/null 2>&1 || {
            echo "Counter item '$URL_COUNTER_NAME' already exists. Skipping seed."
            return
        }

    echo "Counter item '$URL_COUNTER_NAME' created successfully."
}

create_url_table
create_counter_table
seed_url_counter

echo "======================================"
echo "DynamoDB setup completed successfully."
echo "======================================"
