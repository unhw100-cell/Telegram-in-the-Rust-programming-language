#!/usr/bin/env bash
# ==============================================================================
# Telegram Rust Core - Скрипт автоматизированной сборки релизного APK
# ==============================================================================
# Флаги оптимизации Rust компилятора:
# - opt-level = "s" (оптимизация под минимальный размер бинарника)
# - lto = "fat" (полная межмодульная оптимизация компоновщика)
# - codegen-units = 1 (максимальная глубина инлайнинга)
# - strip = "symbols" (удаление отладочных символов)
# ==============================================================================

set -euo pipefail

echo "🚀 [1/5] Проверка зависимостей окружения Android NDK и Rust..."

if ! command -v cargo &> /dev/null; then
    echo "❌ Ошибка: cargo не найден в PATH. Установите Rust: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
    exit 1
fi

if ! command -v cargo-ndk &> /dev/null; then
    echo "📦 Установка cargo-ndk..."
    cargo install cargo-ndk
fi

# Убедимся, что установлены целевые Android архитектуры
TARGETS=("aarch64-linux-android" "armv7-linux-androideabi" "x86_64-linux-android")
for target in "${TARGETS[@]}"; do
    rustup target add "$target" || true
done

echo "⚙️ [2/5] Компиляция нативных Rust-библиотек под релизные архитектуры..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNILIBS_DIR="$SCRIPT_DIR/../app/src/main/jniLibs"

mkdir -p "$JNILIBS_DIR"

cd "$SCRIPT_DIR"

# Сборка через cargo-ndk:
# -t arm64-v8a (64-битные современные процессоры)
# -t armeabi-v7a (32-битные старые устройства)
# -t x86_64 (эмуляторы и планшеты)
cargo ndk \
    -t arm64-v8a \
    -t armeabi-v7a \
    -t x86_64 \
    -o "$JNILIBS_DIR" \
    build --release -p tg-core

echo "✨ [3/5] Генерация актуальных Kotlin-биндингов через uniffi-bindgen..."

cargo run --features=uniffi/cli --bin uniffi-bindgen generate \
    crates/tg-core/src/lib.rs \
    --language kotlin \
    --out-dir "$SCRIPT_DIR/../app/src/main/java/com/example/core" || true

echo "📊 [4/5] Размер скомпилированных нативных библиотек .so:"
ls -lh "$JNILIBS_DIR"/*/*.so 2>/dev/null || echo "Библиотеки помещены в $JNILIBS_DIR"

echo "📱 [5/5] Сборка оптимизированного релизного APK через Gradle..."

cd "$SCRIPT_DIR/.."
if [ -f "./gradlew" ]; then
    ./gradlew :app:assembleRelease
else
    gradle :app:assembleRelease
fi

echo "=============================================================================="
echo "🎉 СБОРКА РЕЛИЗА УСПЕШНО ЗАВЕРШЕНА!"
echo "Финальный APK расположен в: app/build/outputs/apk/release/app-release.apk"
echo "=============================================================================="
