#!/usr/bin/env bash
# Cross-platform hosts setup + mkcert certificates for Traefik

HOSTS_ENTRIES=("127.0.0.1 auth.localhost" "127.0.0.1 extract.localhost" "127.0.0.1 notify.localhost")
CERT_DIR="./certs"
CERT_HOSTS=("auth.localhost" "extract.localhost" "notify.localhost")

detect_os() {
    case "$OSTYPE" in
        linux*)   echo "linux";;
        darwin*)  echo "mac";;
        msys*|cygwin*|win32*) echo "windows";;
        *) echo "unknown";;
    esac
}

OS=$(detect_os)

# Step 1: Add /etc/hosts entries
if [[ "$OS" == "windows" ]]; then
    echo "Detected Windows OS. Running PowerShell to update hosts..."
    powershell -Command "
        \$hostsPath = '\$env:SystemRoot\System32\drivers\etc\hosts';
        \$entries = @('127.0.0.1 auth.localhost','127.0.0.1 extract.localhost','127.0.0.1 notify.localhost');
        foreach (\$entry in \$entries) {
            \$content = Get-Content \$hostsPath -ErrorAction SilentlyContinue;
            if (\$content -notcontains \$entry) {
                Add-Content -Path \$hostsPath -Value \$entry;
                Write-Host 'Added' \$entry;
            } else { Write-Host \$entry 'already exists, skipping'; }
        }
    "
    echo "Hosts setup complete on Windows. Make sure you ran this script as Administrator."
else
    echo "Detected $OS. Updating /etc/hosts..."
    HOSTS_FILE="/etc/hosts"
    for ENTRY in "${HOSTS_ENTRIES[@]}"; do
        if ! grep -q "$ENTRY" "$HOSTS_FILE"; then
            echo "Adding $ENTRY"
            echo "$ENTRY" | sudo tee -a "$HOSTS_FILE" > /dev/null
        else
            echo "$ENTRY already exists, skipping"
        fi
    done
    echo "Hosts setup complete on $OS."
fi

# Step 2: Generate mkcert certificates
if ! command -v mkcert &> /dev/null; then
    echo "mkcert not found! Please install mkcert first:"
    echo "Linux/macOS: https://github.com/FiloSottile/mkcert#installation"
    echo "Windows: https://github.com/FiloSottile/mkcert#installation"
    exit 1
fi

mkdir -p "$CERT_DIR"
echo "Generating mkcert certificates for ${CERT_HOSTS[*]} in $CERT_DIR ..."

# mkcert generates a PEM key & cert by default
mkcert -cert-file "$CERT_DIR/cert.pem" -key-file "$CERT_DIR/key.pem" "${CERT_HOSTS[@]}"

echo "Certificates generated:"
ls -l "$CERT_DIR"

echo "Setup complete!"
echo " - Hosts updated for: ${HOSTS_ENTRIES[*]}"
echo " - mkcert certificates available in $CERT_DIR"
echo " - Traefik can now use $CERT_DIR/cert.pem and $CERT_DIR/key.pem for HTTPS"
echo ""
echo "Access your services via:"
echo "  https://auth.localhost"
echo "  https://extract.localhost"
echo "  https://notify.localhost"
