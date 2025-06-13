# 🛠️ Руководство по настройке окружения

## 📋 Обзор

Данное руководство поможет вам настроить все необходимые environment variables и получить API ключи для полноценной работы системы баланса и платежей TelegramStarManager.

## 🎯 Визуализация процесса настройки

```
┌─────────────────── ПРОЦЕСС НАСТРОЙКИ ОКРУЖЕНИЯ ───────────────────┐
│                                                                   │
│  1. БАЗОВЫЕ НАСТРОЙКИ                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐│
│  │   База данных   │    │     Redis       │    │   Безопасность  ││
│  │                 │    │                 │    │                 ││
│  │ • DATABASE_URL  │    │ • REDIS_HOST    │    │ • API_KEY       ││
│  │ • DB_USER       │    │ • REDIS_PASS    │    │ • JWT_SECRET    ││
│  │ • DB_PASSWORD   │    │ • REDIS_PORT    │    │ • CALLBACK_SEC  ││
│  └─────────────────┘    └─────────────────┘    └─────────────────┘│
│                                                                   │
│  2. TELEGRAM BOT                                                  │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │ • TELEGRAM_BOT_TOKEN (от @BotFather)                       │  │
│  │ • TELEGRAM_BOT_USERNAME                                    │  │
│  │ • TELEGRAM_WEBHOOK_URL                                     │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  3. ПЛАТЕЖНЫЕ СИСТЕМЫ                                             │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │
│  │ TON WALLET  │ │  YOOKASSA   │ │    QIWI     │ │  SBERPAY    │  │
│  │             │ │             │ │             │ │             │  │
│  │ • API_KEY   │ │ • SHOP_ID   │ │ • PUBLIC_K  │ │ • MERCH_ID  │  │
│  │ • SECRET_K  │ │ • SECRET_K  │ │ • SECRET_K  │ │ • SECRET_K  │  │
│  │ • WALLET    │ │ • WEBHOOK   │ │ • SITE_ID   │ │ • API_LOGIN │  │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │
│                                                                   │
│  4. МОНИТОРИНГ И УВЕДОМЛЕНИЯ                                      │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │ • ALERT_EMAIL                                               │  │
│  │ • TELEGRAM_NOTIFICATIONS_BOT_TOKEN                          │  │
│  │ • ADMIN_CHAT_ID                                             │  │
│  └─────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────┘
```

## 🗂️ Структура файлов конфигурации

Создайте следующие файлы в корне проекта:

```
project-root/
├── .env                          # Основной файл переменных
├── .env.local                    # Локальные настройки (не в git)
├── .env.production              # Продакшен настройки
├── .env.development             # Настройки разработки
└── docker-compose.override.yml  # Локальные переопределения Docker
```

## 📝 Создание базового .env файла

Создайте файл `.env` в корне проекта:

```bash
# ============================================
# БАЗОВЫЕ НАСТРОЙКИ ПРИЛОЖЕНИЯ
# ============================================

# Профили Spring Boot (development, production, payment, postgresql)
SPRING_PROFILES_ACTIVE=development,payment,postgresql

# Домен приложения (ОБЯЗАТЕЛЬНО: замените на ваш)
DOMAIN_NAME=yourdomain.com
APP_PORT=8080
MANAGEMENT_PORT=8081

# ============================================
# НАСТРОЙКИ БАЗЫ ДАННЫХ
# ============================================

DATABASE_URL=jdbc:postgresql://localhost:5432/telegram_star_db
DATABASE_NAME=telegram_star_db
DATABASE_USER=telegram_user
DATABASE_PASSWORD=secure_password_change_me
DATABASE_PORT=5432

# ============================================
# НАСТРОЙКИ REDIS
# ============================================

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis_secure_change_me

# ============================================
# НАСТРОЙКИ БЕЗОПАСНОСТИ
# ============================================

# API ключ для админ панели (сгенерируйте уникальный)
SECURITY_API_KEY=your_unique_api_key_here

# Пароль администратора
SECURITY_ADMIN_PASSWORD=admin_secure_password

# JWT секрет (сгенерируйте случайную строку 64+ символов)
SECURITY_JWT_SECRET=your_jwt_secret_64_chars_minimum_change_this_in_production

# Секрет для подписи callback'ов платежных систем
PAYMENT_CALLBACK_SECRET=your_payment_callback_secret_change_me

# ============================================
# TELEGRAM BOT НАСТРОЙКИ
# ============================================

# Токен бота (получить от @BotFather)
TELEGRAM_BOT_TOKEN=

# Имя пользователя бота (без @)
TELEGRAM_BOT_USERNAME=

# URL для webhook'ов (https://yourdomain.com)
TELEGRAM_WEBHOOK_URL=

# ============================================
# ОБЩИЕ НАСТРОЙКИ ПЛАТЕЖЕЙ
# ============================================

# Базовый URL для callback'ов платежных систем
PAYMENT_CALLBACK_BASE_URL=https://yourdomain.com

# ============================================
# TON WALLET НАСТРОЙКИ
# ============================================

TON_ENABLED=false
TON_API_KEY=
TON_SECRET_KEY=
TON_WALLET_ADDRESS=
TON_WEBHOOK_URL=
TON_TESTNET=true

# ============================================
# YOOKASSA НАСТРОЙКИ
# ============================================

YOOKASSA_ENABLED=false
YOOKASSA_SHOP_ID=
YOOKASSA_SECRET_KEY=
YOOKASSA_WEBHOOK_URL=
YOOKASSA_TEST_MODE=true

# ============================================
# QIWI НАСТРОЙКИ
# ============================================

QIWI_ENABLED=false
QIWI_PUBLIC_KEY=
QIWI_SECRET_KEY=
QIWI_SITE_ID=
QIWI_WEBHOOK_URL=

# ============================================
# SBERPAY НАСТРОЙКИ
# ============================================

SBERPAY_ENABLED=false
SBERPAY_MERCHANT_ID=
SBERPAY_SECRET_KEY=
SBERPAY_API_LOGIN=
SBERPAY_API_PASSWORD=
SBERPAY_WEBHOOK_URL=
SBERPAY_TEST_MODE=true

# ============================================
# МОНИТОРИНГ И УВЕДОМЛЕНИЯ
# ============================================

# Email для уведомлений
PAYMENT_MONITORING_ALERT_EMAIL=admin@yourdomain.com

# Telegram уведомления
PAYMENT_NOTIFICATIONS_TELEGRAM_ENABLED=false
PAYMENT_NOTIFICATIONS_BOT_TOKEN=
PAYMENT_NOTIFICATIONS_ADMIN_CHAT_ID=

# ============================================
# НАСТРОЙКИ ЛОГИРОВАНИЯ
# ============================================

LOG_LEVEL=INFO
PAYMENT_LOG_LEVEL=INFO
PAYMENT_CALLBACK_LOG_LEVEL=DEBUG

# ============================================
# НАСТРОЙКИ РАЗРАБОТКИ
# ============================================

PAYMENT_DEV_MOCK_MODE=false
PAYMENT_DEV_ENABLE_DEBUG=false
PAYMENT_DEV_SKIP_VERIFICATION=false
```

## 🔑 Получение API ключей

### 1. 🤖 Telegram Bot

#### Создание бота:
1. Отправьте `/start` боту [@BotFather](https://t.me/BotFather)
2. Выполните команду `/newbot`
3. Укажите имя бота (например: `Star Manager Bot`)
4. Укажите username бота (например: `your_star_manager_bot`)
5. Получите токен и сохраните в `TELEGRAM_BOT_TOKEN`

#### Настройка webhook'а:
```bash
# Установить webhook
curl -F "url=https://yourdomain.com/api/telegram/webhook" \
     "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook"

# Проверить webhook
curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getWebhookInfo"
```

### 2. 🪙 TON Wallet

#### Получение API ключей:
1. Перейдите на [TON Center](https://toncenter.com)
2. Зарегистрируйтесь и получите API ключ
3. Создайте кошелек или используйте существующий
4. Настройте webhook URL в личном кабинете

```bash
# Пример настройки переменных
TON_ENABLED=true
TON_API_KEY=your_ton_api_key_here
TON_SECRET_KEY=your_ton_secret_key_here
TON_WALLET_ADDRESS=EQA...your_wallet_address_here
TON_WEBHOOK_URL=https://yourdomain.com/api/payment/callback/ton
TON_TESTNET=false  # false для mainnet
```

### 3. 💎 YooKassa

#### Регистрация в YooKassa:
1. Перейдите на [YooKassa](https://yookassa.ru)
2. Зарегистрируйте магазин
3. Получите Shop ID и секретный ключ в личном кабинете
4. Настройте HTTP уведомления

```bash
# Настройка переменных
YOOKASSA_ENABLED=true
YOOKASSA_SHOP_ID=your_shop_id_here
YOOKASSA_SECRET_KEY=your_secret_key_here
YOOKASSA_WEBHOOK_URL=https://yourdomain.com/api/payment/callback/yookassa
YOOKASSA_TEST_MODE=false  # false для продакшена
```

#### Настройка webhook'а в YooKassa:
1. Войдите в личный кабинет YooKassa
2. Перейдите в "Настройки" → "HTTP уведомления"
3. Укажите URL: `https://yourdomain.com/api/payment/callback/yookassa`
4. Выберите события: `payment.succeeded`, `payment.canceled`

### 4. 🥝 QIWI

#### Настройка QIWI P2P:
1. Перейдите на [p2p.qiwi.com](https://p2p.qiwi.com)
2. Создайте пару ключей для P2P API
3. Настройте webhook уведомления

```bash
# Настройка переменных
QIWI_ENABLED=true
QIWI_PUBLIC_KEY=your_public_key_here
QIWI_SECRET_KEY=your_secret_key_here
QIWI_SITE_ID=your_site_id_here
QIWI_WEBHOOK_URL=https://yourdomain.com/api/payment/callback/qiwi
```

#### Настройка webhook'а QIWI:
```bash
# Установить webhook через API
curl -X PUT \
  "https://api.qiwi.com/partner/payin/v1/sites/${QIWI_SITE_ID}/webhook" \
  -H "Authorization: Bearer ${QIWI_SECRET_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://yourdomain.com/api/payment/callback/qiwi",
    "txnType": "IN"
  }'
```

### 5. 🏦 SberPay

#### Подключение SberPay:
1. Обратитесь в Сбербанк для подключения к эквайрингу
2. Получите Merchant ID и API credentials
3. Настройте callback URL в личном кабинете

```bash
# Настройка переменных
SBERPAY_ENABLED=true
SBERPAY_MERCHANT_ID=your_merchant_id_here
SBERPAY_SECRET_KEY=your_secret_key_here
SBERPAY_API_LOGIN=your_api_login_here
SBERPAY_API_PASSWORD=your_api_password_here
SBERPAY_WEBHOOK_URL=https://yourdomain.com/api/payment/callback/sberpay
SBERPAY_TEST_MODE=false
```

## 🛡️ Безопасность

### Генерация безопасных ключей:

```bash
# Генерация API ключа (Linux/Mac)
SECURITY_API_KEY=$(openssl rand -hex 32)

# Генерация JWT секрета
SECURITY_JWT_SECRET=$(openssl rand -base64 64)

# Генерация callback секрета
PAYMENT_CALLBACK_SECRET=$(openssl rand -hex 32)

# Для Windows (PowerShell)
$SECURITY_API_KEY = -join ((1..64) | ForEach {'{0:X}' -f (Get-Random -Max 16)})
```

### Настройка SSL/TLS:

#### Получение Let's Encrypt сертификата:
```bash
# Установка Certbot (Ubuntu/Debian)
sudo apt install certbot python3-certbot-nginx

# Получение сертификата
sudo certbot --nginx -d yourdomain.com

# Автообновление
sudo crontab -e
# Добавить строку:
# 0 12 * * * /usr/bin/certbot renew --quiet
```

### IP Whitelist для платежных систем:

```bash
# TON Wallet IP адреса
PAYMENT_SECURITY_TON_IPS=95.142.46.34,95.142.46.35

# YooKassa IP адреса  
PAYMENT_SECURITY_YOOKASSA_IPS=185.71.76.0/27,185.71.77.0/27

# QIWI IP адреса
PAYMENT_SECURITY_QIWI_IPS=79.142.16.0/20,195.189.100.0/22

# SberPay IP адреса
PAYMENT_SECURITY_SBERPAY_IPS=185.71.76.0/27,212.19.125.0/25
```

## 🐳 Docker настройка

### Создание .env.docker файла:

```bash
# Копировать основной .env файл
cp .env .env.docker

# Изменить хосты для Docker сети
sed -i 's/localhost/postgres/g' .env.docker
sed -i 's/127.0.0.1/redis/g' .env.docker
```

### Переопределения для Docker Compose:

Создайте `docker-compose.override.yml`:

```yaml
version: '3.8'

services:
  telegram-star-manager:
    environment:
      # Переопределения для локальной разработки
      SPRING_PROFILES_ACTIVE: development,payment,postgresql
      PAYMENT_DEV_MOCK_MODE: true
      PAYMENT_DEV_ENABLE_DEBUG: true
      LOG_LEVEL: DEBUG
    
    volumes:
      # Подключение локальных файлов для разработки
      - ./logs:/app/logs
      - ./config:/app/config

  postgres:
    ports:
      # Открыть порт для локального подключения
      - "5432:5432"
    
    volumes:
      # Локальная БД для разработки
      - ./data/postgres:/var/lib/postgresql/data

  redis:
    ports:
      # Открыть порт для локального подключения
      - "6379:6379"
```

## 🔧 Скрипты настройки

### Скрипт инициализации (setup.sh):

```bash
#!/bin/bash

echo "🚀 Настройка TelegramStarManager..."

# Проверка требований
command -v docker >/dev/null 2>&1 || { echo "❌ Docker не установлен"; exit 1; }
command -v docker-compose >/dev/null 2>&1 || { echo "❌ Docker Compose не установлен"; exit 1; }

# Создание директорий
mkdir -p data/{postgres,redis,grafana,prometheus}
mkdir -p logs/{app,nginx}
mkdir -p config/{nginx,prometheus,grafana}

# Установка прав доступа
chmod 755 data logs config
chmod 600 .env*

# Генерация секретов
if [ ! -f .env.local ]; then
    echo "🔐 Генерация секретов..."
    cat > .env.local << EOF
SECURITY_API_KEY=$(openssl rand -hex 32)
SECURITY_JWT_SECRET=$(openssl rand -base64 64)
PAYMENT_CALLBACK_SECRET=$(openssl rand -hex 32)
DATABASE_PASSWORD=$(openssl rand -base64 16)
REDIS_PASSWORD=$(openssl rand -base64 16)
EOF
    echo "✅ Секреты сохранены в .env.local"
fi

echo "✅ Настройка завершена!"
echo "📝 Отредактируйте .env файл и добавьте API ключи"
echo "🚀 Запуск: docker-compose -f docker-compose-full.yml up -d"
```

### Скрипт проверки конфигурации (check-config.sh):

```bash
#!/bin/bash

echo "🔍 Проверка конфигурации..."

# Загрузка переменных
source .env
source .env.local 2>/dev/null || true

# Проверка обязательных переменных
required_vars=(
    "TELEGRAM_BOT_TOKEN"
    "SECURITY_API_KEY"
    "DATABASE_PASSWORD"
    "PAYMENT_CALLBACK_SECRET"
)

for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "❌ Переменная $var не установлена"
        exit 1
    fi
done

# Проверка платежных систем
payment_systems=("TON" "YOOKASSA" "QIWI" "SBERPAY")
enabled_count=0

for system in "${payment_systems[@]}"; do
    enabled_var="${system}_ENABLED"
    if [ "${!enabled_var}" = "true" ]; then
        ((enabled_count++))
        echo "✅ $system включен"
        
        # Проверка ключей для включенной системы
        case $system in
            "TON")
                [ -z "$TON_API_KEY" ] && echo "⚠️  TON_API_KEY не установлен"
                ;;
            "YOOKASSA")
                [ -z "$YOOKASSA_SHOP_ID" ] && echo "⚠️  YOOKASSA_SHOP_ID не установлен"
                ;;
            "QIWI")
                [ -z "$QIWI_PUBLIC_KEY" ] && echo "⚠️  QIWI_PUBLIC_KEY не установлен"
                ;;
            "SBERPAY")
                [ -z "$SBERPAY_MERCHANT_ID" ] && echo "⚠️  SBERPAY_MERCHANT_ID не установлен"
                ;;
        esac
    fi
done

if [ $enabled_count -eq 0 ]; then
    echo "⚠️  Ни одна платежная система не включена"
fi

# Проверка доступности URL
if [ -n "$DOMAIN_NAME" ] && [ "$DOMAIN_NAME" != "yourdomain.com" ]; then
    echo "🌐 Проверка домена $DOMAIN_NAME..."
    if curl -s -o /dev/null -w "%{http_code}" "https://$DOMAIN_NAME" | grep -q "200\|301\|302"; then
        echo "✅ Домен доступен"
    else
        echo "⚠️  Домен недоступен или не настроен"
    fi
fi

echo "✅ Проверка завершена"
```

## 🔍 Тестирование настройки

### Проверка подключения к платежным системам:

```bash
# Проверка TON API
curl -H "Authorization: Bearer $TON_API_KEY" \
     "https://toncenter.com/api/v3/account?address=$TON_WALLET_ADDRESS"

# Проверка YooKassa API
curl -u "$YOOKASSA_SHOP_ID:$YOOKASSA_SECRET_KEY" \
     "https://api.yookassa.ru/v3/payments"

# Проверка QIWI API
curl -H "Authorization: Bearer $QIWI_SECRET_KEY" \
     "https://api.qiwi.com/partner/bill/v1/bills/test-bill-id"
```

### Проверка webhook'ов:

```bash
# Тест webhook endpoint'ов
curl -X POST https://yourdomain.com/api/payment/callback/test \
     -H "Content-Type: application/json" \
     -d '{"test": "webhook"}'
```

## 🚨 Troubleshooting

### Частые проблемы:

#### 1. Ошибка подключения к БД
```bash
# Проверка соединения
docker exec -it telegram-star-postgres psql -U $DATABASE_USER -d $DATABASE_NAME -c "\dt"

# Проверка логов
docker logs telegram-star-postgres
```

#### 2. Проблемы с Redis
```bash
# Проверка подключения
docker exec -it telegram-star-redis redis-cli ping

# Проверка авторизации
docker exec -it telegram-star-redis redis-cli -a $REDIS_PASSWORD ping
```

#### 3. SSL сертификат
```bash
# Проверка сертификата
openssl s_client -connect yourdomain.com:443 -servername yourdomain.com

# Обновление сертификата
sudo certbot renew --dry-run
```

### Логи и мониторинг:

```bash
# Просмотр логов приложения
docker logs telegram-star-app -f

# Мониторинг системы
docker stats

# Проверка health check'ов
curl https://yourdomain.com/actuator/health
```

---

*Последнее обновление: 12 декабря 2024*