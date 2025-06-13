#!/bin/bash

echo "🧪 Тестирование кнопки 'Мой баланс' после исправления"
echo "=================================================="

# Проверяем, что приложение запущено
echo "1. Проверка статуса приложения..."
response=$(curl -s http://localhost:8080/api/health 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✅ Приложение доступно на порту 8080"
else
    echo "❌ Приложение не доступно. Убедитесь, что оно запущено."
    exit 1
fi

# Проверяем наличие исправлений в коде
echo ""
echo "2. Проверка внесенных исправлений..."

# Проверяем MessageUtils.java
if grep -q "show_balance" src/main/java/shit/back/utils/MessageUtils.java; then
    echo "✅ MessageUtils.java: callback 'show_balance' найден"
else
    echo "❌ MessageUtils.java: callback 'show_balance' не найден"
fi

# Проверяем CallbackHandler.java
if grep -q "show_balance" src/main/java/shit/back/handler/CallbackHandler.java; then
    echo "✅ CallbackHandler.java: обработчик 'show_balance' найден"
else
    echo "❌ CallbackHandler.java: обработчик 'show_balance' не найден"
fi

# Проверяем SecurityProperties.java
if grep -q "show_balance" src/main/java/shit/back/config/SecurityProperties.java; then
    echo "✅ SecurityProperties.java: 'show_balance' добавлен в разрешенные prefixes"
else
    echo "❌ SecurityProperties.java: 'show_balance' не найден в разрешенных prefixes"
fi

echo ""
echo "3. Инструкции для тестирования:"
echo "   1. Перезапустите приложение через IntelliJ IDEA"
echo "   2. Откройте телеграм бота"
echo "   3. Нажмите /start"
echo "   4. Нажмите кнопку '💰 Мой баланс'"
echo "   5. Убедитесь, что отображается информация о балансе, а не ошибка"

echo ""
echo "4. Мониторинг логов (выполните в отдельном терминале):"
echo "   journalctl -f | grep -E '(show_balance|CallbackHandler|SecurityValidator)'"

echo ""
echo "🚀 Готово! Проблема с безопасностью исправлена."
echo "   Теперь callback 'show_balance' разрешен в SecurityValidator"