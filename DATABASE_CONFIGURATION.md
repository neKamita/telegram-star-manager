# Конфигурация базы данных TelegramStarManager

## 🚨 КРИТИЧЕСКОЕ ПРЕДУПРЕЖДЕНИЕ О БЕЗОПАСНОСТИ

⚠️ **ВНИМАНИЕ**: В файле Run Configuration были найдены хардкод-токены и пароли!

### Обязательные действия по безопасности:
1. **Все секретные данные удалены** из `.idea/runConfigurations/TelegramStarManager_Local.xml`
2. **Обязательно скопируйте** `.env.example` в `.env` и заполните все токены
3. **Обновите токены бота** - получите новые от @BotFather
4. **Смените пароли баз данных** на уникальные и сложные
5. **Никогда не коммитьте** файлы с секретными данными

### ❌ Что ЗАПРЕЩЕНО:
- Хардкодить токены в Run Configurations или коде
- Использовать дефолтные пароли
- Делиться секретными ключами
- Коммитить .env файлы

### ✅ Правильная настройка:
- Все секреты только в .env файле
- IntelliJ автоматически загружает переменные из .env
- Регулярная смена паролей и токенов

---

## Обзор

Приложение поддерживает два режима работы с базой данных:
- **Разработка (dev)**: Использует DEV_DATABASE_* переменные для подключения к NeonDB
- **Продакшн (prod)**: Использует DATABASE_* переменные для продакшн базы данных

## Настройка для разработки

### 1. Скопируйте файл переменных окружения
```bash
cp .env.example .env
```

### 2. Заполните переменные разработки в .env файле
```bash
# Обязательные переменные для разработки
SPRING_PROFILES_ACTIVE=dev
TELEGRAM_BOT_TOKEN=ваш_токен_от_botfather
TELEGRAM_BOT_USERNAME=ваше_имя_бота
API_SECRET_KEY=ваш_уникальный_секретный_ключ

# База данных разработки (NeonDB)
# ⚠️ ВАЖНО: Замените на свои данные из NeonDB dashboard
DEV_DATABASE_HOST=ваш_neon_host.neon.tech
DEV_DATABASE_PORT=5432
DEV_DATABASE_NAME=neondb
DEV_DATABASE_USERNAME=ваш_username
DEV_DATABASE_PASSWORD=ваш_пароль
```

### 3. IntelliJ IDEA Run Configuration

Run Configuration уже настроен в `.idea/runConfigurations/TelegramStarManager_Local.xml` с:
- Профиль: `dev`
- DEV переменные базы данных
- Удаленные placeholder-значения продакшн переменных

## Структура конфигурационных файлов

### application.properties
- Основная конфигурация приложения
- Импортирует другие конфигурационные файлы
- Конфигурация по умолчанию (H2 для тестирования)

### application-dev.properties
- Конфигурация для профиля разработки
- Специфичные настройки dev окружения

### application-dev-postgresql.properties
- **НОВЫЙ**: Конфигурация PostgreSQL для разработки
- Использует DEV_DATABASE_* переменные
- Оптимизирован для локальной разработки

### application-postgresql.properties
- Конфигурация PostgreSQL для продакшн
- Использует DATABASE_* переменные
- Оптимизирован для продакшн окружения

## Решенные проблемы

### ✅ UnknownHostException исправлен
- Удалены placeholder-значения `YOUR_PRODUCTION_HOST_FALLBACK`
- Разделены конфигурации dev и prod
- Правильная логика переключения профилей

### ✅ Переменные окружения
- DEV_* переменные для разработки
- DATABASE_* переменные для продакшн
- Полная документация в .env.example

### ✅ Безопасность
- Нет хардкод-значений в конфигурации
- .env файлы исключены из git
- Отдельные ключи для dev и prod

## Запуск приложения

### В IntelliJ IDEA
1. Использовать run configuration "TelegramStarManager Local"
2. Приложение запустится с профилем `dev`
3. Подключится к NeonDB через DEV_* переменные

### Из командной строки
```bash
# С .env файлом
set -a && source .env && set +a
mvn spring-boot:run -Dspring.profiles.active=dev

# Или с прямыми переменными
SPRING_PROFILES_ACTIVE=dev \
DEV_DATABASE_HOST=ep-bitter-star-a8c1yqlj-pooler.eastus2.azure.neon.tech \
DEV_DATABASE_NAME=neondb \
DEV_DATABASE_USERNAME=neondb_owner \
DEV_DATABASE_PASSWORD=npg_jRu8nmVOQd1G \
TELEGRAM_BOT_TOKEN=ваш_токен \
mvn spring-boot:run
```

## Продакшн развертывание

Для продакшн используйте:
- Профиль: `prod` или `postgresql`
- Переменные: `DATABASE_HOST`, `DATABASE_NAME`, etc.
- Отдельные продакшн учетные данные

## Troubleshooting

### Проблемы подключения к БД
1. Проверьте переменные окружения: `echo $DEV_DATABASE_HOST`
2. Убедитесь что используется dev профиль: `SPRING_PROFILES_ACTIVE=dev`
3. Проверьте логи подключения к базе данных

### IntelliJ IDEA не видит переменные
1. Перезапустите IDE после изменения .env
2. Проверьте Run Configuration
3. Убедитесь что .env файл в корне проекта

## Безопасность

⚠️ **ВАЖНО**:
- Никогда не коммитьте .env файлы
- Используйте разные пароли для dev и prod
- Регулярно обновляйте секретные ключи
- Не используйте dev учетные данные в продакшн