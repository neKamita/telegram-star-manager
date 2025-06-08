# 🔐 Настройка GitHub Secrets для автодеплоя

## 📋 Быстрая инструкция

### 1. Перейдите в настройки репозитория
```
GitHub → Ваш репозиторий → Settings → Secrets and variables → Actions
```

### 2. Добавьте следующие Secrets

Нажмите **"New repository secret"** для каждого:

| Secret Name | Value | Где получить |
|-------------|--------|--------------|
| `KOYEB_API_TOKEN` | `koyeb_xxxx...` | [Koyeb Dashboard](https://app.koyeb.com) → Settings → API |
| `TELEGRAM_BOT_TOKEN` | `123456789:ABC...` | [@BotFather](https://t.me/botfather) → `/newbot` |
| `TELEGRAM_BOT_USERNAME` | `YourBotName` | Имя бота без @ |
| `API_SECRET_KEY` | `32символа...` | Сгенерируйте случайную строку |
| `WEBHOOK_URL` | `https://your-app.koyeb.app` | URL вашего Koyeb приложения |

### 3. Генерация API Secret Key

```bash
# Linux/Mac
openssl rand -hex 16

# Windows PowerShell
[System.Web.Security.Membership]::GeneratePassword(32, 0)

# Онлайн
# https://www.random.org/strings/
```

### 4. Получение Koyeb API Token

1. Зайдите в [Koyeb Dashboard](https://app.koyeb.com)
2. Settings → API
3. Create API token
4. Скопируйте токен (начинается с `koyeb_`)

### 5. Проверка настройки

После добавления всех Secrets:

1. Сделайте любое изменение в коде
2. Commit и Push в `main` ветку
3. Перейдите в **Actions** tab вашего репозитория
4. Убедитесь, что деплой запустился автоматически

## ✅ Готово!

Теперь каждый push в `main` ветку будет автоматически разворачивать ваше приложение на Koyeb с правильными environment variables!

---

**🔒 Никогда не коммитьте Secrets в код!**
