#!/bin/bash
set -e

# Validate required environment variables
if [ -z "$LOGZIO_API_KEY" ]; then
    echo "ERROR: LOGZIO_API_KEY environment variable is required"
    exit 1
fi

if [ -z "$ENV_ID" ]; then
    echo "ERROR: ENV_ID environment variable is required"
    exit 1
fi

API_URL="${LOGZIO_API_URL:-https://api.logz.io/v1}"

echo "=== Validating Logs in Logz.io ==="
echo "ENV_ID: $ENV_ID"
echo "API URL: $API_URL"
echo "=================================="

echo "Querying Logz.io API for logs..."

RESPONSE=$(curl -s -X POST "${API_URL}/search" \
    -H "X-API-TOKEN: ${LOGZIO_API_KEY}" \
    -H "Content-Type: application/json" \
    -d "{
        \"query\": {
            \"bool\": {
                \"must\": [
                    {\"match\": {\"env_id\": \"${ENV_ID}\"}},
                    {\"match\": {\"type\": \"${ENV_ID}\"}}
                ]
            }
        },
        \"from\": 0,
        \"size\": 10,
        \"sort\": [{\"@timestamp\": {\"order\": \"desc\"}}]
    }")

if echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
    echo "ERROR: API returned an error"
    echo "$RESPONSE" | jq .
    exit 1
fi

HITS=$(echo "${RESPONSE}" | jq -r '.hits.total.value // .hits.total // 0')

if [ "${HITS}" -eq 0 ]; then
    echo "ERROR: No logs found with env_id: ${ENV_ID}"
    echo "API Response: ${RESPONSE}"
    exit 1
fi

echo "SUCCESS: Found ${HITS} log(s) with env_id: ${ENV_ID}"

FIRST_LOG=$(echo "${RESPONSE}" | jq -r '.hits.hits[0]._source')

echo "Validating required fields..."

MESSAGE=$(echo "${FIRST_LOG}" | jq -r '.message // empty')
TIMESTAMP=$(echo "${FIRST_LOG}" | jq -r '.["@timestamp"] // empty')
TYPE=$(echo "${FIRST_LOG}" | jq -r '.type // empty')
LOGLEVEL=$(echo "${FIRST_LOG}" | jq -r '.loglevel // empty')

ERRORS=0

if [ -z "${MESSAGE}" ]; then
    echo "ERROR: message field is missing"
    ERRORS=$((ERRORS + 1))
fi

if [ -z "${TIMESTAMP}" ]; then
    echo "ERROR: @timestamp field is missing"
    ERRORS=$((ERRORS + 1))
fi

if [ -z "${TYPE}" ]; then
    echo "ERROR: type field is missing"
    ERRORS=$((ERRORS + 1))
fi

if [ -z "${LOGLEVEL}" ]; then
    echo "ERROR: loglevel field is missing"
    ERRORS=$((ERRORS + 1))
fi

if [ "${ERRORS}" -gt 0 ]; then
    echo "Validation failed with ${ERRORS} error(s)"
    echo "Sample log: ${FIRST_LOG}"
    exit 1
fi

echo "All required fields validated successfully!"
echo ""
echo "=== Sample Log ==="
echo "${FIRST_LOG}" | jq .
echo "=================="

