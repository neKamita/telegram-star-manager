# 🤝 Contributing to Telegram Star Manager

Thank you for your interest in contributing to Telegram Star Manager! This document provides guidelines and information for contributors.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Making Contributions](#making-contributions)
- [Code Style](#code-style)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)

## 📜 Code of Conduct

By participating in this project, you agree to abide by our Code of Conduct:

- **Be respectful** - Treat all contributors with respect
- **Be inclusive** - Welcome newcomers and diverse perspectives
- **Be collaborative** - Work together towards common goals
- **Be patient** - Help others learn and grow

## 🚀 Getting Started

### Prerequisites

- **Java 21+**
- **Maven 3.6+**
- **Git**
- **Telegram Bot Token** for testing

### Development Setup

1. **Fork the repository**
   ```bash
   # Click "Fork" on GitHub, then:
   git clone https://github.com/yourusername/telegram-star-manager.git
   cd telegram-star-manager
   ```

2. **Set up environment**
   ```bash
   cp .env.example .env
   # Edit .env with your test bot credentials
   ```

3. **Install dependencies**
   ```bash
   ./mvnw clean install
   ```

4. **Run tests**
   ```bash
   ./mvnw test
   ```

5. **Start development server**
   ```bash
   ./mvnw spring-boot:run
   ```

## 🛠️ Making Contributions

### Types of Contributions

- 🐛 **Bug fixes**
- ✨ **New features**
- 📚 **Documentation improvements**
- 🧪 **Tests**
- 🎨 **Code style improvements**
- 🔒 **Security enhancements**

### Before You Start

1. **Check existing issues** - Look for related issues or discussions
2. **Create an issue** - Describe your proposed changes
3. **Get feedback** - Discuss your approach with maintainers
4. **Create a branch** - Use descriptive branch names

### Branch Naming Convention

```bash
# Feature branches
feature/add-payment-processing
feature/improve-security

# Bug fix branches
bugfix/fix-session-timeout
bugfix/resolve-memory-leak

# Documentation branches
docs/update-api-documentation
docs/add-deployment-guide
```

## 📝 Code Style

### Java Code Style

- **Use Java 21 features** when appropriate
- **Follow Spring Boot conventions**
- **Use meaningful names** for variables and methods
- **Add comprehensive Javadoc** for public methods
- **Use Lombok** to reduce boilerplate code

### Example Code Style

```java
/**
 * Processes Telegram bot messages with security validation.
 * 
 * @param message The incoming Telegram message
 * @return Processed response message
 * @throws SecurityException if message fails validation
 */
@Service
@Slf4j
public class MessageProcessor {
    
    private final SecurityValidator securityValidator;
    private final MessageHandler messageHandler;
    
    public MessageProcessor(SecurityValidator securityValidator, 
                           MessageHandler messageHandler) {
        this.securityValidator = securityValidator;
        this.messageHandler = messageHandler;
    }
    
    public SendMessage processMessage(Message message) {
        log.debug("Processing message from user: {}", message.getFrom().getId());
        
        // Validate message
        securityValidator.validateMessage(message);
        
        // Process message
        return messageHandler.handleMessage(message);
    }
}
```

### Configuration Style

- **Use YAML** for Spring configuration
- **Group related properties** logically
- **Add comments** for complex configurations
- **Use profiles** for environment-specific settings

## 🧪 Testing

### Testing Strategy

- **Unit Tests** - Test individual components
- **Integration Tests** - Test component interactions
- **Security Tests** - Test security features
- **API Tests** - Test REST endpoints

### Writing Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "telegram.bot.token=test_token",
    "security.api.enabled=false"
})
class MessageProcessorTest {
    
    @Autowired
    private MessageProcessor messageProcessor;
    
    @MockBean
    private SecurityValidator securityValidator;
    
    @Test
    @DisplayName("Should process valid message successfully")
    void shouldProcessValidMessage() {
        // Given
        Message message = createTestMessage();
        
        // When
        SendMessage result = messageProcessor.processMessage(message);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("processed");
    }
}
```

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=MessageProcessorTest

# Run with coverage
./mvnw test jacoco:report
```

## 🔄 Pull Request Process

### 1. Prepare Your Branch

```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Make your changes
# ... code changes ...

# Commit changes
git add .
git commit -m "feat: add new feature description"
```

### 2. Commit Message Format

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `style:` - Code style changes
- `refactor:` - Code refactoring
- `test:` - Adding tests
- `chore:` - Maintenance tasks

**Examples:**
```
feat(security): add rate limiting for API endpoints
fix(bot): resolve message handling timeout issue
docs(readme): update installation instructions
test(security): add tests for authentication flow
```

### 3. Submit Pull Request

1. **Push your branch**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Create Pull Request** on GitHub with:
   - Clear title and description
   - Reference related issues
   - Screenshots if UI changes
   - Testing instructions

3. **PR Template**
   ```markdown
   ## Description
   Brief description of changes
   
   ## Related Issues
   - Fixes #123
   - Related to #456
   
   ## Changes Made
   - [ ] Added new feature X
   - [ ] Fixed bug Y
   - [ ] Updated documentation
   
   ## Testing
   - [ ] Unit tests pass
   - [ ] Integration tests pass
   - [ ] Manual testing completed
   
   ## Screenshots (if applicable)
   ```

### 4. Code Review Process

- **Automated checks** must pass
- **At least one reviewer** approval required
- **Address feedback** promptly
- **Squash commits** if requested

## 🛡️ Security Considerations

### Security Guidelines

- **Never commit secrets** - Use environment variables
- **Validate all inputs** - Sanitize user data
- **Use parameterized queries** - Prevent SQL injection
- **Implement rate limiting** - Prevent abuse
- **Log security events** - Monitor for threats

### Reporting Security Issues

**DO NOT** create public issues for security vulnerabilities.

Instead:
1. Email security issues to: [security-email-here]
2. Include detailed description
3. Provide steps to reproduce
4. Wait for response before disclosure

## 📚 Additional Resources

### Documentation

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Java 21 Features](https://openjdk.org/projects/jdk/21/)

### Tools

- **IDE**: IntelliJ IDEA or VS Code
- **Testing**: JUnit 5, Mockito, TestContainers
- **Code Quality**: SonarQube, SpotBugs
- **Documentation**: Javadoc, Swagger

## ❓ Getting Help

- **GitHub Issues** - For bugs and feature requests
- **GitHub Discussions** - For questions and ideas
- **Documentation** - Check README and inline comments
- **Code Review** - Ask questions during PR review

## 🎉 Recognition

Contributors are recognized in:
- **README.md** - Contributors section
- **Release Notes** - Feature acknowledgments
- **GitHub** - Contributor graphs and statistics

Thank you for contributing to Telegram Star Manager! 🚀

---

**Happy coding!** 💻✨
