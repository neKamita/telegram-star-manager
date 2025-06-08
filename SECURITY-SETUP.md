# 🔐 Руководство по безопасной настройке проекта

## 🚨 ВАЖНО: Защита API ключей и секретных данных

Этот проект настроен для безопасного хранения всех секретных данных через переменные окружения. **НИ ОДИН секретный ключ не хранится в коде!**

## 📋 Обязательные переменные окружения

### 🤖 Telegram Bot
```bash
TELEGRAM_BOT_TOKEN=your_bot_token_from_botfather
TELEGRAM_BOT_USERNAME=YourBotUsername
```

### 🔒 Security
```bash
API_SECRET_KEY=your_32_character_secret_key_here
```

### 🌐 Production (Koyeb)
```bash
WEBHOOK_URL=https://your-app-name.koyeb.app
ENVIRONMENT=production
SPRING_PROFILES_ACTIVE=production
```

## 🚀 Настройка для разработки

### 1. Клонируйте репозиторий
```bash
git clone https://github.com/your-username/TelegramStarManager.git
cd TelegramStarManager
```

### 2. Создайте файл .env
```bash
cp .env.example .env
```

### 3. Заполните .env файл
Отредактируйте `.env` файл и укажите ваши реальные значения:
```bash
TELEGRAM_BOT_TOKEN=123456789:Your-Real-Bot-Token-Here
TELEGRAM_BOT_USERNAME=YourRealBotUsername
API_SECRET_KEY=your_real_32_character_secret_key
```

### 4. Запустите проект
```bash
mvn spring-boot:run
```

## 🌐 Настройка для Koyeb (Production)

### 1. Fork репозитория
- Сделайте fork этого репозитория в свой GitHub

### 2. Настройка GitHub Secrets
В настройках вашего GitHub репозитория добавьте следующие Secrets:

**Settings → Secrets and variables → Actions → New repository secret**

```
KOYEB_API_TOKEN=your_koyeb_api_token
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_BOT_USERNAME=your_bot_username
API_SECRET_KEY=your_32_character_secret
WEBHOOK_URL=https://your-app-name.koyeb.app
```

### 3. Настройка Koyeb приложения
1. Зайдите в [Koyeb Dashboard](https://app.koyeb.com)
2. Создайте новое приложение из GitHub репозитория
3. Настройте Environment Variables:
   ```
   TELEGRAM_BOT_TOKEN=your_bot_token
   TELEGRAM_BOT_USERNAME=your_bot_username  
   API_SECRET_KEY=your_secret_key
   WEBHOOK_URL=https://your-app-name.koyeb.app
   ENVIRONMENT=production
   SPRING_PROFILES_ACTIVE=production
   ```

### 4. Автоматический деплой
После настройки каждый push в `main` ветку будет автоматически разворачивать приложение на Koyeb!

## 🔐 Получение токенов

### Telegram Bot Token
1. Откройте [@BotFather](https://t.me/botfather) в Telegram
2. Отправьте `/newbot`
3. Следуйте инструкциям
4. Скопируйте токен (формат: `123456789:ABCDefGhI...`)

### API Secret Key
Сгенерируйте случайную строку из 32 символов:
```bash
# Linux/Mac
openssl rand -hex 16

# Windows PowerShell  
[System.Web.Security.Membership]::GeneratePassword(32, 0)
```

### Koyeb API Token
1. Зайдите в [Koyeb Dashboard](https://app.koyeb.com)
2. Settings → API → Create API token
3. Скопируйте токен

## ✅ Проверка безопасности

### Что НЕЛЬЗЯ делать:
- ❌ Коммитить файлы `.env` 
- ❌ Хардкодить токены в коде
- ❌ Публиковать скриншоты с токенами
- ❌ Отправлять токены в сообщениях

### Что НУЖНО делать:
- ✅ Использовать только Environment Variables
- ✅ Проверять .gitignore перед коммитом
- ✅ Регулярно ротировать токены
- ✅ Использовать GitHub Secrets для CI/CD

## 🔍 Диагностика

После развертывания проверьте работу через diagnostic endpoints:

```
https://your-app.koyeb.app/diagnostic/health
https://your-app.koyeb.app/diagnostic/telegram-config  
https://your-app.koyeb.app/diagnostic/bot-self-test
```

## 📞 Поддержка

Если у вас возникли проблемы:
1. Проверьте логи в Koyeb Dashboard
2. Убедитесь, что все Environment Variables настроены
3. Проверьте, что бот добавлен в качестве администратора (если нужно)
4. Используйте diagnostic endpoints для отладки

## 🎯 Результат

✅ **Публичный репозиторий** - код доступен всем  
✅ **Безопасность** - никаких секретов в коде  
✅ **Автодеплой** - push → автоматическое развертывание  
✅ **Простота настройки** - следуйте этому руководству  

---

**🛡️ Безопасность - это приоритет №1!**
