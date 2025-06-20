# 🚀 Руководство по настройке TelegramStarManager

## 📋 Обзор

Полное руководство по настройке окружения разработки и продакшн-окружения для системы баланса и платежей TelegramStarManager.

## 🏗️ Быстрый старт (Локальная разработка)

### 1. 🔧 Подготовка окружения

```bash
# 1. Скопируйте файл переменных окружения
cp .env.example .env

# 2. Отредактируйте .env файл и укажите токен вашего бота
# TELEGRAM_BOT_TOKEN=ВАШ_РЕАЛЬНЫЙ_ТОКЕН_БОТА
```

### 2. 🎯 Конфигурация IntelliJ IDEA

#### Вариант A: Использование профиля dev (Рекомендуется)
```
Run Configuration:
- Main class: shit.back.TelegramStarManagerApplication
- VM options: -Dspring.profiles.active=dev
- Environment variables: (загрузятся из .env файла)
```

#### Вариант B: Использование Environment Variables в IDE
```
Environment Variables в Run Configuration:
SPRING_PROFILES_ACTIVE=dev
TELEGRAM_BOT_TOKEN=ваш_токен
TELEGRAM_BOT_USERNAME=MirzaShop_bot
API_SECRET_KEY=dev-secret-key-12345
H2_CONSOLE_ENABLED=true
```

### 3. ▶️ Запуск приложения

1. Откройте проект в IntelliJ IDEA
2. Найдите `TelegramStarManagerApplication.java`
3. Нажмите **Run** (зеленая стрелка)
4. Дождитесь успешного запуска

## 🎯 Что проверить после запуска

### ✅ Логи приложения
```
🤖 Initializing Telegram Bot Service...
✅ Telegram bot 'MirzaShop_bot' registered successfully!
🚀 Telegram Bot Service initialization completed. Status: Active and registered
```

### ✅ Доступные URL-адреса
- **Admin Panel**: http://localhost:8080/admin
- **Activity Logs**: http://localhost:8080/admin/activity-logs
- **Feature Flags**: http://localhost:8080/admin/feature-flags  
- **Monitoring**: http://localhost:8080/admin/monitoring
- **H2 Database Console**: http://localhost:8080/h2-console
- **Health Check**: http://localhost:8080/actuator/health

### ✅ H2 Database Console
```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/local_starmanager
User Name: sa
Password: (оставить пустым)
```

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

# Настройки для разработки (используют DEV_ префикс)
DEV_DATABASE_HOST=localhost
DEV_DATABASE_NAME=telegram_star_dev_db
DEV_DATABASE_USERNAME=dev_user
DEV_DATABASE_PASSWORD=dev_password_change_me
DEV_DATABASE_PORT=5432

# ============================================
# НАСТРОЙКИ REDIS
# ============================================

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis_secure_change_me
REDIS_DATABASE=0

# ============================================
# НАСТРОЙКИ БЕЗОПАСНОСТИ
# ============================================

# API ключ для админ панели (сгенерируйте уникальный)
API_SECRET_KEY=your_unique_api_key_here

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
LOGGING_LEVEL=INFO

# ============================================
# НАСТРОЙКИ РАЗРАБОТКИ
# ============================================

PAYMENT_DEV_MOCK_MODE=false
PAYMENT_DEV_ENABLE_DEBUG=false
PAYMENT_DEV_SKIP_VERIFICATION=false
H2_CONSOLE_ENABLED=true
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

## 🛡️ Безопасность

### Генерация безопасных ключей:

```bash
# Генерация API ключа (Linux/Mac)
API_SECRET_KEY=$(openssl rand -hex 32)

# Генерация JWT секрета
SECURITY_JWT_SECRET=$(openssl rand -base64 64)

# Генерация callback секрета
PAYMENT_CALLBACK_SECRET=$(openssl rand -hex 32)

# Для Windows (PowerShell)
$API_SECRET_KEY = -join ((1..64) | ForEach {'{0:X}' -f (Get-Random -Max 16)})
```

## 🔄 Различия между локальной и продакшн-версией

### 📊 Сравнительная таблица

| Параметр | Локальная разработка | Продакшн (Koyeb) |
|----------|---------------------|------------------|
| **Профиль** | `dev` | `production` |
| **Bot Service** | `TelegramBotService` (Polling) | `TelegramWebhookBotService` (Webhook) |
| **База данных** | H2 (встроенная) | PostgreSQL (Neon) |
| **URL** | http://localhost:8080 | https://brave-selina-g45-16b60ff3.koyeb.app |
| **Bot режим** | Long Polling | Webhook |
| **Логирование** | DEBUG (подробно) | INFO (минимально) |
| **H2 Console** | Включена | Отключена |

## 🐳 Docker настройка

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

## 🐛 Решение проблем

### ❌ Проблема: Bot token not configured
```
⚠️ Bot token not configured! Please set telegram.bot.token
```
**Решение**: Проверьте .env файл или переменные окружения в IDE.

### ❌ Проблема: Не запускается H2 Console
```
Whitelabel Error Page
```
**Решение**: Убедитесь, что `H2_CONSOLE_ENABLED=true` в .env файле.

### ❌ Проблема: Ошибка подключения к базе данных
```
SQLException: Database may be already in use
```
**Решение**: Закройте все подключения к H2/PostgreSQL и перезапустите приложение.

### ❌ Проблема: Проблемы с Redis
```bash
# Проверка подключения
docker exec -it telegram-star-redis redis-cli ping

# Проверка авторизации
docker exec -it telegram-star-redis redis-cli -a $REDIS_PASSWORD ping
```

## 🧪 Тестирование

### 1. 🤖 Тестирование бота
1. Найдите своего бота в Telegram
2. Отправьте команду `/start`
3. Проверьте логи в IntelliJ на получение сообщения

### 2. 🔧 Тестирование админки
1. Откройте http://localhost:8080/admin
2. Проверьте все страницы:
   - Dashboard
   - Activity Logs
   - Feature Flags  
   - Monitoring

### 3. 🗄️ Тестирование базы данных
1. Откройте http://localhost:8080/h2-console
2. Выполните запрос: `SELECT * FROM user_activity_log`
3. Проверьте создание таблиц

## 📝 Полезные команды

### Maven команды
```bash
# Компиляция проекта
mvn clean compile

# Запуск тестов
mvn test

# Создание JAR файла
mvn clean package

# Запуск приложения через Maven
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Команды для отладки
```bash
# Проверить переменные окружения
echo $SPRING_PROFILES_ACTIVE

# Проверить состояние приложения
curl http://localhost:8080/actuator/health

# Проверить конфигурацию
curl http://localhost:8080/actuator/env
```

## 🎯 Следующие шаги

После успешного запуска:

1. **Протестируйте бота** в Telegram
2. **Изучите админку** по всем разделам
3. **Проверьте логи активности** в консоли БД
4. **Настройте Feature Flags** через админку
5. **Мониторьте производительность** в разделе Monitoring

---

*Последнее обновление: 17 декабря 2024*