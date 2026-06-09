#!/bin/bash
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ENDPOINT_URL="${LOCALSTACK_ENDPOINT_URL:-http://localhost:4566}"

VISIBILITY_TIMEOUT="${SQS_VISIBILITY_TIMEOUT:-30}"
RECEIVE_WAIT_TIME="${SQS_RECEIVE_WAIT_TIME:-20}"
MESSAGE_RETENTION_PERIOD="${SQS_MESSAGE_RETENTION_PERIOD:-345600}"
MAX_RECEIVE_COUNT="${SQS_MAX_RECEIVE_COUNT:-5}"

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
    local dlq_name="$1"

    if queue_exists "$dlq_name"; then
        echo "SQS DLQ '$dlq_name' already exists. Skipping creation."
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
        --queue-name "$dlq_name" \
        --region "$REGION" \
        --attributes "$dlq_attributes" >/dev/null

    echo "SQS DLQ '$dlq_name' created."
}

create_or_update_main_queue() {
    local queue_name="$1"
    local dlq_name="$2"
    local dlq_url
    local dlq_arn
    local redrive_policy
    local queue_attributes

    dlq_url="$(get_queue_url "$dlq_name")"
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

    if queue_exists "$queue_name"; then
        echo "SQS queue '$queue_name' already exists. Updating attributes."

        local queue_url
        queue_url="$(get_queue_url "$queue_name")"

        "${SQS_CMD[@]}" set-queue-attributes \
            --queue-url "$queue_url" \
            --region "$REGION" \
            --attributes "$queue_attributes" >/dev/null

        echo "SQS queue '$queue_name' attributes updated."
        return
    fi

    "${SQS_CMD[@]}" create-queue \
        --queue-name "$queue_name" \
        --region "$REGION" \
        --attributes "$queue_attributes" >/dev/null

    echo "SQS queue '$queue_name' created with DLQ '$dlq_name'."
}

create_queue_pair() {
    local queue_name="$1"
    local dlq_name="$2"

    create_dlq_if_absent "$dlq_name"
    create_or_update_main_queue "$queue_name" "$dlq_name"
}

create_queue_pair \
    "${URL_REDIRECT_EVENTS_QUEUE_NAME:-url-redirect-events-queue}" \
    "${URL_REDIRECT_EVENTS_DLQ_NAME:-url-redirect-events-dlq}"

create_queue_pair \
    "${EMAIL_VERIFICATION_EVENTS_QUEUE_NAME:-email-verification-events-queue}" \
    "${EMAIL_VERIFICATION_EVENTS_DLQ_NAME:-email-verification-events-dlq}"

echo "SQS queues are ready."
