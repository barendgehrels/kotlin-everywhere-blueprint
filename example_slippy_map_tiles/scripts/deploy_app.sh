#!/usr/bin/env bash
# Builds the service and the web app, then deploys both workers to Cloudflare.
# Run from anywhere; needs `npx wrangler login` once, and secret.properties in the root.
# The Android APK next to the web app (BLUE_PRINT_GUEST §1) follows when the release key is there.
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

# The real worker config is not in git: see SETUP.md
for config_file in service/wrangler.toml app/webApp/wrangler.toml; do
    if [[ ! -f "$config_file" ]]; then
        echo "$config_file not found: copy it from wrangler.example.toml (see SETUP.md)" >&2
        exit 1
    fi
done

# The deployed app must call its service over https
service_url="$(grep -E '^cloud_service_url=' secret.properties | cut -d= -f2-)"
if [[ "$service_url" != https://* ]]; then
    echo "cloud_service_url in secret.properties is not https: $service_url" >&2
    exit 1
fi

./gradlew :service:build :app:webApp:wasmJsBrowserDistribution

echo "Applying the database migrations (remote)"
(cd service && npx wrangler d1 migrations apply slippy --remote)

echo "Deploying the service"
(cd service && npx wrangler deploy)

echo "Deploying the web app"
(cd app/webApp && npx wrangler deploy)
