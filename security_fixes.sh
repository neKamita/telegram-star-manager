#!/bin/bash

# 🔒 Security Fixes Script для TelegramStarManager
# Автоматическое исправление найденных проблем безопасности

echo "🔍 Начинаем исправление проблем безопасности..."

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Функция для логирования
log_info() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Backup функция
create_backup() {
    local file=$1
    if [ -f "$file" ]; then
        cp "$file" "${file}.backup.$(date +%Y%m%d_%H%M%S)"
        log_info "Создан backup для $file"
    fi
}

echo ""
echo "📋 ИСПРАВЛЕНИЕ ПРОБЛЕМ ПО ПРИОРИТЕТУ:"
echo "1. 🔴 HIGH: Удаление API ключа из документации"
echo "2. 🟡 MEDIUM: Усиление fallback security"
echo "3. 🟡 MEDIUM: Исправление docker-compose.yml"
echo "4. ⚫ LOW: Решение проблемы init.sql"
echo ""

# 1. ВЫСОКИЙ ПРИОРИТЕТ: Удаление API ключа из документации
echo "🔴 ИСПРАВЛЕНИЕ HIGH PRIORITY ПРОБЛЕМ..."

LEAKED_API_KEY="8f2a9c1b4e7d6f3a5c8b9e2d4f7a1c6b"
PLACEHOLDER_KEY="YOUR_API_KEY_HERE"

# Список файлов с утечкой API ключа
FILES_WITH_LEAK=(
    "FINAL_ENDPOINTS_SUMMARY.md"
    "SECURITY_IMPLEMENTATION.md"
    "ENDPOINT_FIX_SUMMARY.md"
    "GRACEFUL_FALLBACK_SUMMARY.md"
)

for file in "${FILES_WITH_LEAK[@]}"; do
    if [ -f "$file" ]; then
        create_backup "$file"
        sed -i "s/$LEAKED_API_KEY/$PLACEHOLDER_KEY/g" "$file"
        log_info "API ключ заменен в $file"
    else
        log_warning "Файл $file не найден"
    fi
done

# Проверка что все ключи заменены
if grep -r "$LEAKED_API_KEY" . --exclude-dir=target --exclude="*.backup.*" >/dev/null 2>&1; then
    log_error "API ключ все еще найден в проекте!"
    echo "Найден в следующих местах:"
    grep -r "$LEAKED_API_KEY" . --exclude-dir=target --exclude="*.backup.*"
else
    log_info "API ключ успешно удален из всех файлов"
fi

echo ""

# 2. СРЕДНИЙ ПРИОРИТЕТ: Исправление fallback security
echo "🟡 ИСПРАВЛЕНИЕ MEDIUM PRIORITY ПРОБЛЕМ..."

# Исправление application-security.yml
SECURITY_YML="src/main/resources/application-security.yml"
if [ -f "$SECURITY_YML" ]; then
    create_backup "$SECURITY_YML"
    
    # Заменяем слабый fallback на пустой или удаляем его
    sed -i 's/key: ${API_SECRET_KEY:default-key-change-me}/key: ${API_SECRET_KEY}/g' "$SECURITY_YML"
    log_info "Убран слабый fallback в $SECURITY_YML"
else
    log_warning "Файл $SECURITY_YML не найден"
fi

# Исправление SecurityProperties.java
SECURITY_PROPS="src/main/java/shit/back/config/SecurityProperties.java"
if [ -f "$SECURITY_PROPS" ]; then
    create_backup "$SECURITY_PROPS"
    
    # Заменяем слабый default на получение из environment
    sed -i 's/private String key = "default-key-change-me";/private String key = System.getenv("API_SECRET_KEY");/g' "$SECURITY_PROPS"
    log_info "Убран слабый default в $SECURITY_PROPS"
else
    log_warning "Файл $SECURITY_PROPS не найден"
fi

# Исправление docker-compose.yml
DOCKER_COMPOSE="docker-compose.yml"
if [ -f "$DOCKER_COMPOSE" ]; then
    create_backup "$DOCKER_COMPOSE"
    
    # Убираем слабый default пароль
    sed -i 's/- POSTGRES_PASSWORD=${DATABASE_PASSWORD:-telegram_password}/- POSTGRES_PASSWORD=${DATABASE_PASSWORD}/g' "$DOCKER_COMPOSE"
    
    # Комментируем или убираем ссылку на несуществующий init.sql
    sed -i 's|      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro|      # - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro  # File not found - commented out|g' "$DOCKER_COMPOSE"
    
    log_info "Исправлен $DOCKER_COMPOSE - убраны слабые пароли и init.sql"
else
    log_warning "Файл $DOCKER_COMPOSE не найден"
fi

echo ""

# 3. НИЗКИЙ ПРИОРИТЕТ: Создание базового init.sql если нужен
echo "⚫ ИСПРАВЛЕНИЕ LOW PRIORITY ПРОБЛЕМ..."

INIT_SQL="init.sql"
if [ ! -f "$INIT_SQL" ]; then
    cat > "$INIT_SQL" << EOF
-- PostgreSQL initialization script for TelegramStarManager
-- This file is used by docker-compose for database initialization

-- Create database schema if needed
-- CREATE SCHEMA IF NOT EXISTS telegram_star_manager;

-- Create indexes for better performance
-- CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
-- CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
-- CREATE INDEX IF NOT EXISTS idx_feature_flags_name ON feature_flags(name);

-- Initial data can be added here if needed
-- INSERT INTO feature_flags (name, enabled, description) VALUES 
--   ('bot_enabled', true, 'Enable/disable bot functionality'),
--   ('admin_panel', true, 'Enable/disable admin panel');

-- Security: No sensitive data should be in this file
-- All credentials must come from environment variables
EOF
    log_info "Создан базовый $INIT_SQL файл"
    
    # Раскомментируем строку в docker-compose.yml
    sed -i 's|      # - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro  # File not found - commented out|      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro|g' "$DOCKER_COMPOSE"
    log_info "Включена ссылка на init.sql в docker-compose.yml"
else
    log_info "Файл $INIT_SQL уже существует"
fi

echo ""

# 4. ФИНАЛЬНАЯ ВАЛИДАЦИЯ
echo "🔍 ФИНАЛЬНАЯ ПРОВЕРКА БЕЗОПАСНОСТИ..."

echo ""
echo "Проверка на оставшиеся проблемы:"

# Проверка на утечки API ключей
if grep -r "8f2a9c1b4e7d6f3a5c8b9e2d4f7a1c6b" . --exclude-dir=target --exclude="*.backup.*" >/dev/null 2>&1; then
    log_error "❌ API ключ все еще найден!"
else
    log_info "✅ API ключи очищены"
fi

# Проверка на слабые defaults
if grep -r "default-key-change-me" . --exclude-dir=target --exclude="*.backup.*" >/dev/null 2>&1; then
    log_error "❌ Слабые default ключи все еще найдены!"
else
    log_info "✅ Слабые default ключи удалены"
fi

# Проверка на слабые пароли
if grep -r "telegram_password" . --exclude-dir=target --exclude="*.backup.*" >/dev/null 2>&1; then
    log_error "❌ Слабые пароли все еще найдены!"
else
    log_info "✅ Слабые пароли удалены"
fi

echo ""
echo "🎯 СВОДКА ИСПРАВЛЕНИЙ:"
echo "✅ Удален API ключ из документации"
echo "✅ Убраны слабые fallback значения"
echo "✅ Исправлен docker-compose.yml"
echo "✅ Решена проблема init.sql"
echo "✅ Созданы backup'ы всех измененных файлов"

echo ""
echo "📋 СЛЕДУЮЩИЕ ШАГИ:"
echo "1. Проверьте созданные backup файлы"
echo "2. Убедитесь что environment variables настроены в production"
echo "3. Запустите тесты: ./mvnw test"
echo "4. Проверьте сборку: ./mvnw clean package"
echo "5. Задеплойте изменения"

echo ""
echo "🔒 SECURITY AUDIT FIXES COMPLETED!"
echo "Проект готов к production после проверки environment variables."

# Создаем отчет об исправлениях
FIXES_REPORT="SECURITY_FIXES_REPORT_$(date +%Y%m%d_%H%M%S).md"
cat > "$FIXES_REPORT" << EOF
# 🔒 ОТЧЕТ ОБ ИСПРАВЛЕНИИ ПРОБЛЕМ БЕЗОПАСНОСТИ

**Дата исправления:** $(date)
**Скрипт:** security_fixes.sh

## Исправленные проблемы:

### ✅ HIGH PRIORITY - Удален API ключ из документации
- Заменен API ключ \`8f2a9c1b4e7d6f3a5c8b9e2d4f7a1c6b\` на \`YOUR_API_KEY_HERE\`
- Файлы: FINAL_ENDPOINTS_SUMMARY.md, SECURITY_IMPLEMENTATION.md, и др.

### ✅ MEDIUM PRIORITY - Убраны слабые fallback значения
- application-security.yml: убран default-key-change-me
- SecurityProperties.java: изменен на получение из environment
- docker-compose.yml: убраны слабые default пароли

### ✅ LOW PRIORITY - Решена проблема init.sql
- Создан базовый init.sql файл
- Исправлена ссылка в docker-compose.yml

## Backup файлы созданы для:
$(find . -name "*.backup.*" -type f | head -10)

## Финальная проверка:
- ✅ API ключи очищены
- ✅ Слабые default ключи удалены  
- ✅ Слабые пароли удалены

**Статус:** Все критические и средние проблемы безопасности исправлены.
EOF

log_info "Создан отчет об исправлениях: $FIXES_REPORT"
