#!/bin/bash
set -euo pipefail

# Configurable variables
PROJECT_ID="${PROJECT_ID:-otp-extractor-477323}"
SA_ID="${SA_ID:-extract-otp-sa}"
SA_DISPLAY_NAME="${SA_DISPLAY_NAME:-Extract OTP Service Account}"
TOPIC="${TOPIC:-gmail-notify-topic}"
SUB="${SUB:-shared-gmail-sub}"
KEY_PATH="${KEY_PATH:-./extract-otp-sa-key.json}"
CREATE_KEY="${CREATE_KEY:-true}"   # Set to "false" to skip key creation

# Authenticate glcoud CLI
echo "[info] Authenticating gcloud CLI..."
gcloud auth login --brief || true
gcloud config set project "$PROJECT_ID"

# Create Service Account
echo "[info] Ensuring service account exists..."
if gcloud iam service-accounts describe "$SA_ID@$PROJECT_ID.iam.gserviceaccount.com" --project "$PROJECT_ID" &>/dev/null; then
    echo "[ok] Service account already exists: $SA_ID@$PROJECT_ID.iam.gserviceaccount.com"
else
    gcloud iam service-accounts create "$SA_ID" \
        --display-name "$SA_DISPLAY_NAME" \
        --project "$PROJECT_ID"
    echo "[ok] Service account created: $SA_ID@$PROJECT_ID.iam.gserviceaccount.com"
fi

# Create Service Account Key (optional)
if [ "$CREATE_KEY" = true ]; then
    echo "[info] Creating service account key at $KEY_PATH"
    gcloud iam service-accounts keys create "$KEY_PATH" \
        --iam-account "$SA_ID@$PROJECT_ID.iam.gserviceaccount.com" \
        --project "$PROJECT_ID"
    chmod 600 "$KEY_PATH"
    echo "[ok] Key written to $KEY_PATH (treat as secret)"
fi

# Create Topic
echo "[info] Ensuring Pub/Sub topic exists..."
if gcloud pubsub topics describe "$TOPIC" --project "$PROJECT_ID" &>/dev/null; then
    echo "[ok] Topic already exists: $TOPIC"
else
    gcloud pubsub topics create "$TOPIC" --project "$PROJECT_ID"
    echo "[ok] Topic created: $TOPIC"
fi

# Create Subscription
echo "[info] Ensuring Pub/Sub subscription exists..."
if gcloud pubsub subscriptions describe "$SUB" --project "$PROJECT_ID" &>/dev/null; then
    echo "[ok] Subscription already exists: $SUB"
else
    gcloud pubsub subscriptions create "$SUB" \
        --topic "$TOPIC" \
        --project "$PROJECT_ID"
    echo "[ok] Subscription created: $SUB"
fi

# Grant IAM roles
echo "[info] Granting Pub/Sub roles to service account..."
gcloud pubsub topics add-iam-policy-binding "$TOPIC" \
    --member="serviceAccount:$SA_ID@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/pubsub.publisher" \
    --project "$PROJECT_ID" || true

gcloud pubsub subscriptions add-iam-policy-binding "$SUB" \
    --member="serviceAccount:$SA_ID@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/pubsub.subscriber" \
    --project "$PROJECT_ID" || true

echo "[info] Granting publisher permission to Gmail API"
gcloud pubsub topics add-iam-policy-binding gmail-notify-topic \
  --member="serviceAccount:gmail-api-push@system.gserviceaccount.com" \
  --role="roles/pubsub.publisher" \
  --project=otp-extractor-477323

echo "[ok] IAM bindings applied"

# Done
echo ""
echo "Setup complete!"
echo "Project: $PROJECT_ID"
echo "Service Account: $SA_ID@$PROJECT_ID.iam.gserviceaccount.com"
if [ "$CREATE_KEY" = true ]; then
    echo "Key: $KEY_PATH (treat as secret)"
fi
echo "Topic: $TOPIC"
echo "Subscription: $SUB"
echo ""
echo "Next steps:"
echo " - Verify Gmail Pub/Sub push config points to this topic."
echo " - Use the service account key (or Workload Identity) in your app to subscribe/pull messages."
