# 🌟 Telegram Star Manager

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Telegram Bot API](https://img.shields.io/badge/Telegram%20Bot%20API-6.8.0-blue.svg)](https://core.telegram.org/bots/api)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Professional Telegram Bot for managing Telegram Star purchases with advanced security and monitoring**

[Features](#-features) • [Quick Start](#-quick-start) • [API Documentation](#-api-documentation) • [Security](#-security) • [Monitoring](#-monitoring) • [Deploy to Koyeb](#-deploy-to-koyeb)

</div>

---

## 🚀 Features

### 💬 Telegram Bot Capabilities
- ✅ **Interactive Star Packages** - Multiple pricing tiers with custom buttons
- ✅ **Order Management** - Complete order processing workflow
- ✅ **User Sessions** - Persistent user state management
- ✅ **Callback Handling** - Advanced inline keyboard interactions
- ✅ **Graceful Error Handling** - Bot continues working even with API issues

### 🛡️ Enterprise Security
- 🔐 **API Key Authentication** - Secure REST API access
- 🚦 **Rate Limiting** - Configurable request throttling
- ✅ **Input Validation** - Comprehensive message and callback validation
- 🌐 **CORS Protection** - Configurable cross-origin policies
- 📝 **Security Logging** - Detailed audit trails

### 📊 Advanced Monitoring
- ⚕️ **Health Checks** - Multi-level system diagnostics
- 📈 **Status Endpoints** - Real-time bot and system status
- 🔍 **Detailed Diagnostics** - Deep system insights with recommendations
- 📊 **Security Status** - Protected security configuration overview

### 🏗️ Production Ready
- 🔄 **Graceful Fallback** - Application runs even if Telegram is unavailable
- ⚙️ **Environment Configuration** - `.env` file support with validation
- 📦 **Docker Ready** - Easy containerization
- 🚀 **Auto-scaling Support** - Stateless design for horizontal scaling

---

## 🛠️ Quick Start

### Prerequisites
- **Java 21+**
- **Maven 3.6+**
- **Telegram Bot Token** from [@BotFather](https://t.me/botfather)

### 1. Clone & Setup
```bash
git clone https://github.com/yourusername/telegram-star-manager.git
cd telegram-star-manager

# Copy environment template
cp .env.example .env
```

### 2. Configure Environment
Edit `.env` file with your credentials:
```env
TELEGRAM_BOT_TOKEN=your_bot_token_from_botfather
TELEGRAM_BOT_USERNAME=YourBotUsername
API_KEY=your_secure_api_key_here
```

### 3. Run Application
```bash
# Using Maven
./mvnw spring-boot:run

# Or build and run JAR
./mvnw clean package
java -jar target/TelegramStarManager-1.0-SNAPSHOT.jar
```

### 4. Verify Installation
```bash
# Health check
curl http://localhost:8080/api/ping

# Bot status
curl http://localhost:8080/api/health
```

---

## 📚 API Documentation

### 🏥 Health & Monitoring Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/api/ping` | Simple availability check | ❌ |
| `GET` | `/api/health` | Comprehensive system health | ❌ |
| `GET` | `/api/health/bot` | Detailed bot diagnostics | ❌ |
| `GET` | `/api/security/status` | Security configuration | ✅ |

### 🤖 Bot Operations

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/api/bot/status` | Quick bot status | ❌ |
| `GET` | `/api/bot/health` | Bot health check | ❌ |
| `GET` | `/api/bot/prices` | Available star packages | ❌ |
| `POST` | `/api/bot/send-message` | Send message to user | ❌ |
| `POST` | `/api/bot/cleanup-sessions` | Clean old sessions | ❌ |

### 📖 Example Usage

#### Health Check
```bash
curl http://localhost:8080/api/health
```

```json
{
  "status": "UP",
  "healthy": true,
  "telegram": {
    "registered": true,
    "status": "Active and registered",
    "username": "StarManagerBot"
  },
  "security": {
    "apiProtection": true,
    "rateLimiting": true,
    "validation": true,
    "cors": true
  }
}
```

#### Send Message
```bash
curl -X POST "http://localhost:8080/api/bot/send-message" \
  -d "chatId=123456789&message=Hello from API!"
```

#### Security Status (Protected)
```bash
curl -H "X-API-KEY: your_api_key" \
  http://localhost:8080/api/security/status
```

---

## 🛡️ Security

### Authentication
- **API Key**: Add `X-API-KEY` header for protected endpoints
- **Rate Limiting**: Configurable per-minute request limits
- **Input Validation**: All inputs are sanitized and validated

### Configuration
Security settings in `src/main/resources/application-security.yml`:

```yaml
security:
  api:
    enabled: true
    header-name: "X-API-KEY"
  rate-limit:
    enabled: true
    user-requests-per-minute: 30
    api-requests-per-minute: 100
  validation:
    enabled: true
    max-message-length: 4096
```

### Best Practices
- ✅ Never commit `.env` files
- ✅ Use strong API keys (32+ characters)
- ✅ Enable rate limiting in production
- ✅ Monitor security logs regularly

---

## 📊 Monitoring

### Health Check Levels

1. **🟢 Ping** (`/api/ping`) - Basic availability
2. **🟡 Bot Status** (`/api/bot/status`) - Operational status  
3. **🔵 System Health** (`/api/health`) - Full overview
4. **🟣 Diagnostics** (`/api/health/bot`) - Deep insights
5. **🔒 Security Status** (`/api/security/status`) - Protected info

### Troubleshooting

#### Bot Not Working?
Check `/api/health/bot` for detailed diagnostics:
```json
{
  "registered": false,
  "status": "Registration failed", 
  "recommendations": {
    "action": "Check bot configuration",
    "steps": [
      "1. Verify TELEGRAM_BOT_TOKEN in .env file",
      "2. Ensure bot exists and is active in @BotFather",
      "3. Check bot username matches configuration",
      "4. Restart application after fixing"
    ]
  }
}
```

#### Common Issues
- **Token Invalid**: Verify token from @BotFather
- **Bot Inactive**: Enable bot in BotFather settings
- **Rate Limited**: Check Telegram API limits
- **Network Issues**: Verify connectivity to api.telegram.org

---

## 🏗️ Architecture

### Project Structure
```
src/main/java/shit/back/
├── config/          # Configuration classes
├── controller/      # REST API endpoints
├── handler/         # Telegram message handlers
├── model/          # Data models
├── security/       # Security components
├── service/        # Business logic
└── utils/          # Utility classes
```

### Key Components
- **TelegramBotService** - Core bot functionality with graceful fallback
- **SecurityConfig** - Comprehensive security configuration
- **HealthController** - Advanced monitoring and diagnostics
- **RateLimitService** - Request throttling and protection

---

## 🚀 Deployment

### Docker (Recommended)
```dockerfile
FROM openjdk:21-jre-slim
COPY target/TelegramStarManager-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Environment Variables
- `TELEGRAM_BOT_TOKEN` - Bot token from BotFather
- `TELEGRAM_BOT_USERNAME` - Bot username
- `API_KEY` - Secure API key for protected endpoints
- `SERVER_PORT` - Application port (default: 8080)

### Production Checklist
- [ ] Set strong API keys
- [ ] Enable HTTPS
- [ ] Configure rate limiting
- [ ] Set up monitoring
- [ ] Configure logging
- [ ] Set up backup strategy

## 🌐 Deploy to Koyeb

### Quick Deploy to Free Cloud ☁️

Deploy your Telegram Star Manager to **Koyeb** completely free! 

[![Deploy to Koyeb](https://www.koyeb.com/static/images/deploy/button.svg)](https://app.koyeb.com/deploy?type=git&repository=github.com/yourusername/telegram-star-manager&branch=main&name=telegram-star-manager)

**Koyeb Free Tier includes:**
- 🆓 **Forever Free** - No time limits
- 💾 **512MB RAM** - Perfect for Telegram bots
- ⚡ **0.1 vCPU** - Sufficient for most workloads  
- 🌍 **Global Edge Network** - Fast worldwide delivery
- 🔒 **Free HTTPS** & custom domains
- 📊 **Built-in monitoring** & logging

### Manual Setup

1. **Register at [koyeb.com](https://www.koyeb.com)** (GitHub login)
2. **Create new app** → Connect GitHub repository
3. **Configure environment variables:**
   ```bash
   TELEGRAM_BOT_TOKEN=your_bot_token
   TELEGRAM_BOT_USERNAME=your_bot_username  
   API_KEY=your_secure_api_key
   ENVIRONMENT=production
   ```
4. **Set Docker file:** `Dockerfile.koyeb`
5. **Deploy!** 🚀

### Post-Deploy Setup

After deployment, configure Telegram webhook:

```bash
# Get your Koyeb app URL
APP_URL="https://your-app-name.koyeb.app"

# Set webhook
curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook" \
  -d "url=${APP_URL}/webhook"

# Verify webhook
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo"
```

### Monitoring Your Deployment

```bash
# Health check
curl https://your-app.koyeb.app/api/ping

# Bot status
curl https://your-app.koyeb.app/api/health

# View logs in Koyeb Dashboard
```

📖 **Full deployment guide:** [KOYEB_DEPLOYMENT.md](KOYEB_DEPLOYMENT.md)

---

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🆘 Support

- 📖 **Documentation**: Check this README and inline code comments
- 🐛 **Issues**: [GitHub Issues](https://github.com/yourusername/telegram-star-manager/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/yourusername/telegram-star-manager/discussions)

---

<div align="center">

**⭐ Star this repository if it helped you! ⭐**

Made with ❤️ for the Telegram Bot community

</div>
