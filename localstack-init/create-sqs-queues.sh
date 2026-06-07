#!/bin/bash
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ENDPOINT_URL="${LOCALSTACK_ENDPOINT_URL:-http://localhost:4566}"

QUEUE_NAME="${URL_REDIRECT_EVENTS_QUEUE_NAME:-url-redirect-events-queue}"
DLQ_NAME="${URL_REDIRECT_EVENTS_DLQ_NAME:-url-redirect-events-dlq}"

VISIBILITY_TIMEOUT="${URL_REDIRECT_EVENTS_VISIBILITY_TIMEOUT:-30}"
RECEIVE_WAIT_TIME="${URL_REDIRECT_EVENTS_RECEIVE_WAIT_TIME:-20}"
MESSAGE_RETENTION_PERIOD="${URL_REDIRECT_EVENTS_MESSAGE_RETENTION_PERIOD:-345600}"
MAX_RECEIVE_COUNT="${URL_REDIRECT_EVENTS_MAX_RECEIVE_COUNT:-5}"

if command -v awslocal >/dev/null 2>&1; then
    SQS_CMD=(awslocal sqs)
elif command -v aws >/dev/null 2>&1; then
    SQS_CMD=(aws --endpoint-url "$ENDPOINT_URL" sqs)
else
    echo "Error: neither 'awslocal' nor 'aws' CLI was found." >&2
    exit 1
fi

queue_exists() {
    local queue_name="$1"

    "${SQS_CMD[@]}" get-queue-url \
        --queue-name "$queue_name" \
        --region "$REGION" >/dev/null 2>&1
}

get_queue_url() {
    local queue_name="$1"

    "${SQS_CMD[@]}" get-queue-url \
        --queue-name "$queue_name" \
        --region "$REGION" \
        --query "QueueUrl" \
        --output text
}

get_queue_arn() {
    local queue_url="$1"

    "${SQS_CMD[@]}" get-queue-attributes \
        --queue-url "$queue_url" \
        --attribute-names QueueArn \
        --region "$REGION" \
        --query "Attributes.QueueArn" \
        --output text
}

create_dlq_if_absent() {
    if queue_exists "$DLQ_NAME"; then
        echo "SQS DLQ '$DLQ_NAME' already exists. Skipping creation."
        return
    fi

    local dlq_attributes
    dlq_attributes="$(
cat <<EOF
{
  "VisibilityTimeout": "$VISIBILITY_TIMEOUT",
  "ReceiveMessageWaitTimeSeconds": "$RECEIVE_WAIT_TIME",
  "MessageRetentionPeriod": "$MESSAGE_RETENTION_PERIOD"
}
EOF
)"

    "${SQS_CMD[@]}" create-queue \
        --queue-name "$DLQ_NAME" \
        --region "$REGION" \
        --attributes "$dlq_attributes" >/dev/null

    echo "SQS DLQ '$DLQ_NAME' created."
}

create_or_update_main_queue() {
    local dlq_url
    local dlq_arn
    local redrive_policy
    local queue_attributes

    dlq_url="$(get_queue_url "$DLQ_NAME")"
    dlq_arn="$(get_queue_arn "$dlq_url")"

    redrive_policy="{\\\"deadLetterTargetArn\\\":\\\"$dlq_arn\\\",\\\"maxReceiveCount\\\":\\\"$MAX_RECEIVE_COUNT\\\"}"

    queue_attributes="$(
cat <<EOF
{
  "VisibilityTimeout": "$VISIBILITY_TIMEOUT",
  "ReceiveMessageWaitTimeSeconds": "$RECEIVE_WAIT_TIME",
  "MessageRetentionPeriod": "$MESSAGE_RETENTION_PERIOD",
  "RedrivePolicy": "$redrive_policy"
}
EOF
)"

    if queue_exists "$QUEUE_NAME"; then
        echo "SQS queue '$QUEUE_NAME' already exists. Updating attributes."

        local queue_url
        queue_url="$(get_queue_url "$QUEUE_NAME")"

        "${SQS_CMD[@]}" set-queue-attributes \
            --queue-url "$queue_url" \
            --region "$REGION" \
            --attributes "$queue_attributes" >/dev/null

        echo "SQS queue '$QUEUE_NAME' attributes updated."
        return
    fi

    "${SQS_CMD[@]}" create-queue \
        --queue-name "$QUEUE_NAME" \
        --region "$REGION" \
        --attributes "$queue_attributes" >/dev/null

    echo "SQS queue '$QUEUE_NAME' created with DLQ '$DLQ_NAME'."
}

create_dlq_if_absent
create_or_update_main_queue

echo "SQS queues are ready."
