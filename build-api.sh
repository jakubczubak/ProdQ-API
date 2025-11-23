#!/bin/bash

# ============================================
# ProdQ API - Multi-Arch Docker Build Script
# ============================================
# Builduje obraz Docker dla linux/amd64 i linux/arm64 (Raspberry Pi)
#
# Wymagania:
# - Docker Desktop z włączonym buildx
# - Zalogowanie do Docker Hub (docker login)
# - Połączenie z internetem
#
# Użycie:
#   bash build-api.sh
#   bash build-api.sh --no-cache  # rebuild od zera
# ============================================

set -e  # Exit on error

# Kolory dla output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Konfiguracja
IMAGE_NAME="czubakjakub/prodq-api"
IMAGE_TAG="latest"
PLATFORMS="linux/amd64,linux/arm64"
BUILDER_NAME="multiarch"

echo "╔══════════════════════════════════════════════════════════╗"
echo "║      ProdQ API - Multi-Arch Docker Build                ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ============================================
# 1. Sprawdzenie wymagań
# ============================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔍 Sprawdzanie wymagań...${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Sprawdź czy Docker działa
if ! docker ps &> /dev/null; then
    echo -e "${RED}❌ Docker daemon nie działa!${NC}"
    echo "Uruchom Docker Desktop i spróbuj ponownie."
    exit 1
fi
echo -e "${GREEN}✅ Docker daemon: uruchomiony${NC}"

# Sprawdź czy buildx jest dostępny
if ! docker buildx version &> /dev/null; then
    echo -e "${RED}❌ Docker buildx nie jest zainstalowany!${NC}"
    echo "Zainstaluj Docker Desktop (najnowsza wersja) z buildx."
    exit 1
fi
BUILDX_VERSION=$(docker buildx version | head -1)
echo -e "${GREEN}✅ Docker buildx: $BUILDX_VERSION${NC}"

# Sprawdź czy jesteś zalogowany do Docker Hub
if ! docker info | grep -q "Username"; then
    echo -e "${YELLOW}⚠️  Nie jesteś zalogowany do Docker Hub${NC}"
    echo ""
    read -p "Zalogować teraz? (t/n) [t]: " login_now
    login_now=${login_now:-t}
    if [[ "$login_now" == "t" || "$login_now" == "T" ]]; then
        docker login
    else
        echo -e "${RED}❌ Musisz być zalogowany do Docker Hub aby pushować obrazy.${NC}"
        exit 1
    fi
fi
echo -e "${GREEN}✅ Docker Hub: zalogowany${NC}"

echo ""

# ============================================
# 2. Setup buildx builder
# ============================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔧 Konfiguracja buildx builder...${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Sprawdź czy builder już istnieje
if docker buildx inspect "$BUILDER_NAME" &> /dev/null; then
    echo -e "${GREEN}✅ Builder '$BUILDER_NAME' już istnieje${NC}"
    docker buildx use "$BUILDER_NAME"
else
    echo "📦 Tworzenie nowego buildera '$BUILDER_NAME'..."
    docker buildx create --name "$BUILDER_NAME" --use --bootstrap
    echo -e "${GREEN}✅ Builder '$BUILDER_NAME' utworzony${NC}"
fi

echo ""

# ============================================
# 3. Informacje o buildzie
# ============================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📋 Szczegóły buildu${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  🏷️  Obraz:      $IMAGE_NAME:$IMAGE_TAG"
echo "  🖥️  Platformy:  $PLATFORMS"
echo "  📦 Builder:    $BUILDER_NAME"
echo "  ☕ Stack:      Spring Boot + Java 21 (JRE)"
echo ""

# Parsowanie argumentów
BUILD_ARGS=""
if [[ "$1" == "--no-cache" ]]; then
    BUILD_ARGS="--no-cache"
    echo -e "${YELLOW}  ⚠️  Rebuild od zera (--no-cache)${NC}"
    echo ""
fi

read -p "Czy kontynuować build? (t/n) [t]: " continue_build
continue_build=${continue_build:-t}
if [[ "$continue_build" != "t" && "$continue_build" != "T" ]]; then
    echo "Build anulowany."
    exit 0
fi

echo ""

# ============================================
# 4. Build multi-arch
# ============================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔨 Buildowanie obrazu...${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${YELLOW}⏳ To może potrwać 10-20 minut (Maven dependencies + 2 platformy)...${NC}"
echo ""

# Zapisz czas startu
START_TIME=$(date +%s)

# Build i push
if docker buildx build \
    --platform "$PLATFORMS" \
    -t "$IMAGE_NAME:$IMAGE_TAG" \
    --push \
    $BUILD_ARGS \
    .; then

    # Oblicz czas
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    MINUTES=$((DURATION / 60))
    SECONDS=$((DURATION % 60))

    echo ""
    echo -e "${GREEN}✅ Build zakończony pomyślnie!${NC}"
    echo -e "${GREEN}   Czas: ${MINUTES}m ${SECONDS}s${NC}"
else
    echo ""
    echo -e "${RED}❌ Build nie powiódł się!${NC}"
    exit 1
fi

echo ""

# ============================================
# 5. Weryfikacja manifestu
# ============================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔍 Weryfikacja multi-arch support...${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Sprawdź manifest
echo "📋 Sprawdzanie manifestu..."
if docker manifest inspect "$IMAGE_NAME:$IMAGE_TAG" > /tmp/manifest-api.json 2>&1; then
    # Sprawdź architektury
    if grep -q '"architecture": "amd64"' /tmp/manifest-api.json && \
       grep -q '"architecture": "arm64"' /tmp/manifest-api.json; then
        echo -e "${GREEN}✅ AMD64 support: TAK${NC}"
        echo -e "${GREEN}✅ ARM64 support: TAK${NC}"

        # Pokaż szczegóły
        echo ""
        echo "Platformy dostępne w manifeście:"
        grep -A2 '"platform"' /tmp/manifest-api.json | grep -E '"architecture"|"os"' | sed 's/^/  /'
    else
        echo -e "${RED}❌ Brak pełnego multi-arch support!${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}⚠️  Nie można pobrać manifestu (może to chwilę potrwać)${NC}"
fi

echo ""

# ============================================
# 6. Podsumowanie
# ============================================
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ BUILD ZAKOŃCZONY POMYŚLNIE!${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "🎉 Obraz $IMAGE_NAME:$IMAGE_TAG jest gotowy!"
echo ""
echo "📋 Następne kroki:"
echo ""
echo "1️⃣  Testowanie na Raspberry Pi:"
echo "   ssh prodq@<raspberry-pi-ip>"
echo "   docker pull $IMAGE_NAME:$IMAGE_TAG"
echo "   docker inspect $IMAGE_NAME:$IMAGE_TAG | grep Architecture"
echo "   # Powinno pokazać: \"Architecture\": \"arm64\""
echo ""
echo "2️⃣  Testowanie lokalnie (AMD64):"
echo "   docker run --rm -p 8080:8080 \\"
echo "     -e SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/prodq_db \\"
echo "     -e SPRING_DATASOURCE_USERNAME=prodq_user \\"
echo "     -e SPRING_DATASOURCE_PASSWORD=prodq_user_2025 \\"
echo "     $IMAGE_NAME:$IMAGE_TAG"
echo "   # Sprawdź: http://localhost:8080/actuator/health"
echo ""
echo "3️⃣  Weryfikacja manifestu:"
echo "   docker manifest inspect $IMAGE_NAME:$IMAGE_TAG | grep -A3 architecture"
echo ""
echo -e "${YELLOW}💡 Pamiętaj: Obraz jest już na Docker Hub i gotowy do użycia!${NC}"
echo ""
