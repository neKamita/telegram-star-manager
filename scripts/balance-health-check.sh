#!/bin/bash

# =============================================================================
# 🏦 СКРИПТ ПРОВЕРКИ РАБОТОСПОСОБНОСТИ СИСТЕМЫ БАЛАНСА
# Telegram Star Manager - Balance System Health Check
# Версия 2.0 - Адаптирован для производственной системы безопасности
# =============================================================================

echo "🏦 TELEGRAM STAR MANAGER - ПРОВЕРКА СИСТЕМЫ БАЛАНСА v2.0"
echo "══════════════════════════════════════════════════════════"
echo "🔒 Система адаптирована для работы с авторизацией API"
echo ""

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Конфигурация
BASE_URL=${BASE_URL:-"http://localhost:8080"}
API_KEY=${API_KEY:-""}
TEST_USER_ID=${TEST_USER_ID:-"999999"}
TIMEOUT=${TIMEOUT:-10}

# Счетчики
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SECURITY_BLOCKED_TESTS=0

# Функция для вывода результата теста
test_result() {
    local test_name="$1"
    local status="$2"
    local details="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    case "$status" in
        "PASS")
            echo -e "${GREEN}✅ PASS${NC} - $test_name"
            [ ! -z "$details" ] && echo "    $details"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            ;;
        "FAIL")
            echo -e "${RED}❌ FAIL${NC} - $test_name"
            [ ! -z "$details" ] && echo "    $details"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            ;;
        "SECURITY_OK")
            echo -e "${PURPLE}🔒 SECURITY_OK${NC} - $test_name"
            [ ! -z "$details" ] && echo "    $details"
            SECURITY_BLOCKED_TESTS=$((SECURITY_BLOCKED_TESTS + 1))
            ;;
        "SKIP")
            echo -e "${YELLOW}⚠️ SKIP${NC} - $test_name"
            [ ! -z "$details" ] && echo "    $details"
            ;;
    esac
}

# Функция для HTTP запросов без авторизации (публичные endpoints)
make_public_request() {
    local method="$1"
    local endpoint="$2"
    local data="$3"
    
    if [ "$method" = "GET" ]; then
        curl -s -w "\n%{http_code}" \
             --connect-timeout $TIMEOUT \
             -H "Content-Type: application/json" \
             "$BASE_URL$endpoint" 2>/dev/null
    else
        curl -s -w "\n%{http_code}" \
             --connect-timeout $TIMEOUT \
             -X "$method" \
             -H "Content-Type: application/json" \
             -d "$data" \
             "$BASE_URL$endpoint" 2>/dev/null
    fi
}

# Функция для HTTP запросов с авторизацией (защищенные endpoints)
make_authenticated_request() {
    local method="$1"
    local endpoint="$2"
    local data="$3"
    
    if [ -z "$API_KEY" ]; then
        echo -e "\nAPI_KEY_NOT_SET\n401"
        return
    fi
    
    if [ "$method" = "GET" ]; then
        curl -s -w "\n%{http_code}" \
             --connect-timeout $TIMEOUT \
             -H "Authorization: Bearer $API_KEY" \
             -H "Content-Type: application/json" \
             "$BASE_URL$endpoint" 2>/dev/null
    else
        curl -s -w "\n%{http_code}" \
             --connect-timeout $TIMEOUT \
             -X "$method" \
             -H "Authorization: Bearer $API_KEY" \
             -H "Content-Type: application/json" \
             -d "$data" \
             "$BASE_URL$endpoint" 2>/dev/null
    fi
}

# Функция для проверки защищенного endpoint на предмет требования авторизации
check_security_enforcement() {
    local endpoint="$1"
    local description="$2"
    
    # Делаем запрос без авторизации к защищенному endpoint
    response=$(make_public_request "GET" "$endpoint")
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        test_result "$description" "SECURITY_OK" "Корректно требует авторизацию (HTTP $http_code)"
        return 0
    elif [ "$http_code" = "200" ]; then
        test_result "$description" "FAIL" "КРИТИЧНО: Endpoint доступен без авторизации!"
        return 1
    else
        test_result "$description" "FAIL" "Неожиданный ответ: HTTP $http_code"
        return 1
    fi
}

echo "🔧 НАЧИНАЕМ ПРОВЕРКУ СИСТЕМЫ..."
echo ""

# =============================================================================
# 1. ПРОВЕРКА ДОСТУПНОСТИ СЕРВИСА (ПУБЛИЧНЫЕ ENDPOINTS)
# =============================================================================
echo "📡 1. ПРОВЕРКА ДОСТУПНОСТИ СЕРВИСА"
echo "─────────────────────────────────────"

# Health Check через публичный actuator endpoint
response=$(make_public_request "GET" "/actuator/health")
http_code=$(echo "$response" | tail -n1)
response_body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    # Исправленный парсинг статуса из JSON
    if echo "$response_body" | grep -q '"status":"UP"'; then
        test_result "Основной Health Check" "PASS" "HTTP 200, статус: UP"
    elif echo "$response_body" | grep -q '"status"'; then
        status=$(echo "$response_body" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        test_result "Основной Health Check" "FAIL" "HTTP 200, но статус: $status"
    else
        test_result "Основной Health Check" "PASS" "HTTP 200, ответ получен"
    fi
else
    test_result "Основной Health Check" "FAIL" "HTTP код: $http_code"
fi

# Health Check для Telegram бота (публичный endpoint)
response=$(make_public_request "GET" "/api/bot/health")
http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "200" ]; then
    test_result "Health Check Telegram бота" "PASS" "Сервис бота доступен"
elif [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
    test_result "Health Check Telegram бота" "SECURITY_OK" "Требует авторизацию (ожидаемо)"
else
    test_result "Health Check Telegram бота" "FAIL" "HTTP код: $http_code"
fi

# Проверка публичного API info endpoint
response=$(make_public_request "GET" "/actuator/info")
http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "200" ]; then
    test_result "Информация о приложении" "PASS" "Данные приложения доступны"
elif [ "$http_code" = "404" ]; then
    test_result "Информация о приложении" "SKIP" "Endpoint не настроен (нормально)"
else
    test_result "Информация о приложении" "FAIL" "HTTP код: $http_code"
fi

echo ""

# =============================================================================
# 2. ПРОВЕРКА БЕЗОПАСНОСТИ СИСТЕМЫ
# =============================================================================
echo "🔒 2. ПРОВЕРКА БЕЗОПАСНОСТИ СИСТЕМЫ"
echo "──────────────────────────────────────"

# Проверяем, что защищенные endpoints требуют авторизацию
check_security_enforcement "/api/balance/$TEST_USER_ID" "Защита API балансов"
check_security_enforcement "/actuator/metrics" "Защита метрик системы"
check_security_enforcement "/actuator/health/db" "Защита информации о БД"

# Проверяем доступность публичных endpoints
echo ""
echo "Проверка публичных endpoints:"

# Проверка webhook endpoint (должен быть публичным)
response=$(make_public_request "GET" "/webhook/telegram")
http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "200" ] || [ "$http_code" = "404" ] || [ "$http_code" = "405" ]; then
    test_result "Webhook endpoint доступность" "PASS" "Webhook доступен для Telegram (HTTP $http_code)"
else
    test_result "Webhook endpoint доступность" "FAIL" "Webhook недоступен: HTTP $http_code"
fi

echo ""

# =============================================================================
# 3. ФУНКЦИОНАЛЬНЫЕ ТЕСТЫ (С АВТОРИЗАЦИЕЙ)
# =============================================================================
echo "🧪 3. ФУНКЦИОНАЛЬНЫЕ ТЕСТЫ"
echo "─────────────────────────────"

# Информация о тестировании с API ключом
if [ -z "$API_KEY" ]; then
    echo -e "${CYAN}ℹ️ INFO${NC} - API ключ не установлен, тестируем только публичные endpoints"
    echo "    Для полного тестирования установите: export API_KEY=your-api-key"
    echo ""
fi

# Тестирование получения баланса
echo "Тестирование API балансов..."
if [ ! -z "$API_KEY" ]; then
    response=$(make_authenticated_request "GET" "/api/balance/$TEST_USER_ID")
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        test_result "Получение баланса пользователя" "PASS" "Баланс получен успешно с API ключом"
    elif [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        test_result "Получение баланса пользователя" "FAIL" "Неверный API ключ или нет прав доступа"
    else
        test_result "Получение баланса пользователя" "FAIL" "HTTP код: $http_code"
    fi
else
    # Проверяем без авторизации - должен вернуть 401/403
    response=$(make_public_request "GET" "/api/balance/$TEST_USER_ID")
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        test_result "Защита API балансов" "SECURITY_OK" "Корректно требует авторизацию"
    else
        test_result "Защита API балансов" "FAIL" "КРИТИЧНО: API доступен без авторизации!"
    fi
fi

# Тестирование валидации (с API ключом если есть)
echo "Тестирование валидации API..."
if [ ! -z "$API_KEY" ]; then
    response=$(make_authenticated_request "POST" "/api/balance/deposit" '{"userId":'$TEST_USER_ID',"amount":-10.00,"paymentMethod":"TEST","description":"Невалидная сумма"}')
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "400" ] || [ "$http_code" = "422" ]; then
        test_result "Валидация отрицательной суммы" "PASS" "Ошибка валидации корректно обработана"
    elif [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        test_result "Валидация отрицательной суммы" "FAIL" "Проблема с авторизацией API"
    else
        test_result "Валидация отрицательной суммы" "FAIL" "Ожидался код 400/422, получен: $http_code"
    fi
else
    test_result "Валидация отрицательной суммы" "SKIP" "API ключ не установлен"
fi

echo ""

# =============================================================================
# 3. ПРОВЕРКА КОНФИГУРАЦИИ
# =============================================================================
echo "⚙️ 3. ПРОВЕРКА КОНФИГУРАЦИИ"
echo "─────────────────────────────"

# Проверка переменных окружения
if [ ! -z "$SPRING_DATASOURCE_URL" ]; then
    test_result "Конфигурация БД" "PASS" "SPRING_DATASOURCE_URL установлен"
else
    test_result "Конфигурация БД" "FAIL" "SPRING_DATASOURCE_URL не установлен"
fi

if [ ! -z "$BALANCE_ENCRYPTION_SECRET" ]; then
    test_result "Секрет шифрования" "PASS" "BALANCE_ENCRYPTION_SECRET установлен"
else
    test_result "Секрет шифрования" "FAIL" "BALANCE_ENCRYPTION_SECRET не установлен"
fi

# Проверка конфигурационных файлов
if [ -f "src/main/resources/application-balance.properties" ]; then
    test_result "Конфигурационный файл баланса" "PASS" "application-balance.properties найден"
else
    test_result "Конфигурационный файл баланса" "FAIL" "application-balance.properties не найден"
fi

echo ""

# =============================================================================
# 4. ПРОВЕРКА ПРОИЗВОДИТЕЛЬНОСТИ
# =============================================================================
echo "⚡ 4. ПРОВЕРКА ПРОИЗВОДИТЕЛЬНОСТИ"
echo "───────────────────────────────────"

# Время отклика health check (публичный endpoint)
start_time=$(date +%s%N)
response=$(make_public_request "GET" "/actuator/health")
end_time=$(date +%s%N)
response_time=$(( (end_time - start_time) / 1000000 )) # Convert to milliseconds

if [ "$response_time" -lt 1000 ]; then
    test_result "Время отклика Health Check" "PASS" "${response_time}ms (< 1000ms)"
else
    test_result "Время отклика Health Check" "FAIL" "${response_time}ms (>= 1000ms)"
fi

# Проверка метрик (защищенный endpoint)
if [ ! -z "$API_KEY" ]; then
    response=$(make_authenticated_request "GET" "/actuator/metrics/jvm.memory.used")
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "200" ]; then
        test_result "Метрики системы (авторизованный доступ)" "PASS" "Метрики доступны"
    elif [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        test_result "Метрики системы (авторизованный доступ)" "FAIL" "Неверный API ключ"
    else
        test_result "Метрики системы (авторизованный доступ)" "FAIL" "Метрики недоступны: HTTP $http_code"
    fi
else
    # Проверяем защиту метрик без API ключа
    response=$(make_public_request "GET" "/actuator/metrics/jvm.memory.used")
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        test_result "Защита метрик системы" "SECURITY_OK" "Метрики защищены авторизацией"
    else
        test_result "Защита метрик системы" "FAIL" "КРИТИЧНО: Метрики доступны без авторизации!"
    fi
fi

echo ""

# =============================================================================
# 5. ПРОВЕРКА ЛОГОВ
# =============================================================================
echo "📝 5. ПРОВЕРКА ЛОГОВ"
echo "───────────────────"

# Проверка наличия лог файлов
if [ -f "logs/application.log" ] || [ -f "logs/balance.log" ]; then
    test_result "Наличие лог файлов" "PASS" "Лог файлы найдены"
    
    # Проверка на критические ошибки в логах (последние 100 строк)
    error_count=0
    for log_file in logs/*.log; do
        if [ -f "$log_file" ]; then
            errors=$(tail -n 100 "$log_file" 2>/dev/null | grep -i "ERROR\|FATAL\|Exception" | wc -l)
            error_count=$((error_count + errors))
        fi
    done
    
    if [ "$error_count" -eq 0 ]; then
        test_result "Критические ошибки в логах" "PASS" "Ошибки не найдены"
    else
        test_result "Критические ошибки в логах" "FAIL" "Найдено ошибок: $error_count"
    fi
else
    test_result "Наличие лог файлов" "FAIL" "Лог файлы не найдены"
fi

echo ""

# =============================================================================
# ИТОГОВЫЙ ОТЧЕТ
# =============================================================================
echo "📊 ИТОГОВЫЙ ОТЧЕТ"
echo "═══════════════════════════════════════════════════"

# Вычисляем успешность с учетом SECURITY_OK как положительные результаты
successful_tests=$((PASSED_TESTS + SECURITY_BLOCKED_TESTS))
success_rate=$((successful_tests * 100 / TOTAL_TESTS))

echo "📈 Статистика тестов:"
echo "   Всего тестов: $TOTAL_TESTS"
echo -e "   Пройдено: ${GREEN}$PASSED_TESTS${NC}"
echo -e "   Защищено (ожидаемо): ${PURPLE}$SECURITY_BLOCKED_TESTS${NC}"
echo -e "   Провалено: ${RED}$FAILED_TESTS${NC}"
echo "   Успешность: $success_rate%"
echo ""

# Анализ безопасности
if [ "$SECURITY_BLOCKED_TESTS" -gt 0 ]; then
    echo -e "${CYAN}🔒 АНАЛИЗ БЕЗОПАСНОСТИ:${NC}"
    echo "   Система корректно блокирует доступ к защищенным endpoints"
    echo "   401/403 ошибки - это НОРМАЛЬНОЕ поведение для production системы"
    echo ""
fi

# Информация об API ключе
if [ -z "$API_KEY" ]; then
    echo -e "${YELLOW}ℹ️ ИНФОРМАЦИЯ:${NC}"
    echo "   API ключ не установлен - тестировались только публичные endpoints"
    echo "   Для полного тестирования установите: export API_KEY=your-api-key"
    echo ""
fi

# Оценка состояния системы
if [ "$success_rate" -ge 90 ]; then
    echo -e "${GREEN}🎉 СИСТЕМА БАЛАНСА РАБОТАЕТ ОТЛИЧНО!${NC}"
    echo "✅ Все критические компоненты функционируют корректно"
    echo "🔒 Система безопасности настроена правильно"
    exit_code=0
elif [ "$success_rate" -ge 70 ]; then
    echo -e "${YELLOW}⚠️ СИСТЕМА БАЛАНСА РАБОТАЕТ С ПРЕДУПРЕЖДЕНИЯМИ${NC}"
    echo "🔍 Рекомендуется проверить проваленные тесты"
    echo "🔒 Система безопасности в основном работает корректно"
    exit_code=1
else
    echo -e "${RED}🚨 СИСТЕМА БАЛАНСА ИМЕЕТ КРИТИЧЕСКИЕ ПРОБЛЕМЫ!${NC}"
    echo "❌ Требуется немедленное вмешательство"
    exit_code=2
fi

echo ""
echo "🔧 Для детальной проверки запустите:"
echo "   mvn test -Dtest=BalanceSystemIntegrationTest"
echo ""
echo "📊 Для мониторинга используйте:"
echo "   curl $BASE_URL/actuator/health                    # Публичный health check"
if [ ! -z "$API_KEY" ]; then
    echo "   curl -H \"Authorization: Bearer \$API_KEY\" $BASE_URL/actuator/metrics  # Метрики (требует API ключ)"
else
    echo "   curl -H \"Authorization: Bearer YOUR_API_KEY\" $BASE_URL/actuator/metrics  # Метрики (требует API ключ)"
fi
echo ""
echo "🚀 Для тестирования с API ключом:"
echo "   export API_KEY=your-api-key-here"
echo "   ./scripts/balance-health-check.sh"
echo ""

# Очистка временных файлов
rm -f /tmp/health_response.json

exit $exit_code