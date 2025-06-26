package shit.back.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shit.back.entity.UserActivityLogEntity;
import shit.back.repository.UserActivityLogJpaRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Диагностический контроллер для проверки проблемы с типами данных в
 * user_activity_logs
 */
@RestController
@RequestMapping("/diagnostic/activity-log")
@RequiredArgsConstructor
@Slf4j
public class DiagnosticActivityLogController {

    private final UserActivityLogJpaRepository activityLogRepository;
    private final DataSource dataSource;

    /**
     * Проверяет реальную схему таблицы user_activity_logs в PostgreSQL
     */
    @GetMapping("/schema")
    public ResponseEntity<Map<String, Object>> checkTableSchema() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> columns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            log.info("=== ДИАГНОСТИКА: Проверка схемы таблицы user_activity_logs ===");

            String sql = """
                    SELECT column_name, data_type, character_maximum_length, is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'user_activity_logs'
                    ORDER BY ordinal_position
                    """;

            ResultSet rs = statement.executeQuery(sql);

            while (rs.next()) {
                Map<String, String> column = new HashMap<>();
                String columnName = rs.getString("column_name");
                String dataType = rs.getString("data_type");
                String maxLength = rs.getString("character_maximum_length");
                String nullable = rs.getString("is_nullable");

                column.put("name", columnName);
                column.put("type", dataType);
                column.put("maxLength", maxLength);
                column.put("nullable", nullable);
                columns.add(column);

                log.info("Колонка: {} | Тип: {} | Длина: {} | Nullable: {}",
                        columnName, dataType, maxLength, nullable);

                // Проверяем проблемные поля
                if ("username".equals(columnName) || "first_name".equals(columnName) ||
                        "last_name".equals(columnName) || "order_id".equals(columnName) ||
                        "action_details".equals(columnName)) {

                    if ("bytea".equals(dataType)) {
                        log.error("🚨 НАЙДЕНА ПРОБЛЕМА: Поле {} имеет тип bytea вместо text/varchar!", columnName);
                    } else {
                        log.info("✅ Поле {} имеет корректный тип: {}", columnName, dataType);
                    }
                }
            }

            result.put("columns", columns);
            result.put("status", "success");

        } catch (Exception e) {
            log.error("Ошибка при проверке схемы таблицы: ", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Тестирует простой запрос без LOWER() функции
     */
    @GetMapping("/simple-query")
    public ResponseEntity<Map<String, Object>> testSimpleQuery() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("=== ДИАГНОСТИКА: Тестирование простого запроса ===");

            // Простой запрос без LOWER()
            List<UserActivityLogEntity> logs = activityLogRepository.findAll(PageRequest.of(0, 5)).getContent();

            log.info("✅ Простой запрос выполнился успешно. Найдено записей: {}", logs.size());

            // Проверяем типы данных в полученных объектах
            for (UserActivityLogEntity log : logs) {
                this.log.info("ID: {} | Username: {} | FirstName: {} | LastName: {} | OrderId: {}",
                        log.getId(), log.getUsername(), log.getFirstName(), log.getLastName(), log.getOrderId());
            }

            result.put("recordsFound", logs.size());
            result.put("status", "success");
            result.put("message", "Простой запрос выполнен успешно");

        } catch (Exception e) {
            log.error("🚨 Ошибка при выполнении простого запроса: ", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * ИСПРАВЛЕНО: Тестирует ранее проблемный запрос с LOWER() функцией
     * ПРОБЛЕМА РЕШЕНА: Убрана функция LOWER() из HQL запросов в
     * UserActivityLogJpaRepository
     */
    @GetMapping("/problematic-query")
    public ResponseEntity<Map<String, Object>> testProblematicQuery() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("=== ДИАГНОСТИКА: Тестирование ранее проблемного запроса (ИСПРАВЛЕНО) ===");

            // Тестируем исправленный запрос без LOWER()
            var logs = activityLogRepository.findWithFilters(
                    false, // showAll
                    null, // fromTime
                    null, // toTime
                    null, // actionTypes
                    "test", // searchTerm - теперь БЕЗ LOWER()
                    PageRequest.of(0, 5));

            log.info("✅ Исправленный запрос выполнился успешно! Найдено записей: {}", logs.getTotalElements());
            result.put("status", "success");
            result.put("message", "✅ ИСПРАВЛЕНО: Запрос работает без функции LOWER()!");
            result.put("recordsFound", logs.getTotalElements());
            result.put("fix", "Убрана функция LOWER() из HQL запросов для избежания ошибки bytea");

        } catch (Exception e) {
            log.error("🚨 НЕОЖИДАННАЯ ОШИБКА: Исправленный запрос все еще вызывает ошибку: ", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
            result.put("message", "Исправление не помогло - требуется дополнительная диагностика");

            // Анализируем сообщение об ошибке
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("bytea")) {
                result.put("diagnosis", "Проблема bytea все еще существует - нужна проверка схемы БД");
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Проверяет настройки Hibernate
     */
    @GetMapping("/hibernate-config")
    public ResponseEntity<Map<String, Object>> checkHibernateConfig() {
        Map<String, Object> result = new HashMap<>();

        log.info("=== ДИАГНОСТИКА: Проверка конфигурации Hibernate ===");

        // Получаем системные свойства Hibernate
        Map<String, String> hibernateProps = new HashMap<>();

        System.getProperties().entrySet().stream()
                .filter(entry -> entry.getKey().toString().contains("hibernate"))
                .forEach(entry -> {
                    String key = entry.getKey().toString();
                    String value = entry.getValue().toString();
                    hibernateProps.put(key, value);
                    log.info("Hibernate свойство: {} = {}", key, value);
                });

        result.put("hibernateProperties", hibernateProps);
        result.put("status", "success");

        return ResponseEntity.ok(result);
    }

    /**
     * ДИАГНОСТИКА: Проверяет точный SQL запрос, который вызывает ошибку
     */
    @GetMapping("/test-lower-function")
    public ResponseEntity<Map<String, Object>> testLowerFunction() {
        Map<String, Object> result = new HashMap<>();

        log.info("=== ДИАГНОСТИКА: Тестирование LOWER() функции на различных полях ===");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            // Тест 1: Проверяем типы конкретных полей
            String[] testFields = { "username", "first_name", "last_name", "order_id", "action_details" };
            Map<String, String> fieldTypes = new HashMap<>();

            for (String field : testFields) {
                try {
                    String typeQuery = String.format(
                            "SELECT data_type FROM information_schema.columns WHERE table_name='user_activity_logs' AND column_name='%s'",
                            field);
                    ResultSet rs = statement.executeQuery(typeQuery);
                    if (rs.next()) {
                        String dataType = rs.getString("data_type");
                        fieldTypes.put(field, dataType);
                        log.info("🔍 Поле {} имеет тип: {}", field, dataType);

                        if ("bytea".equals(dataType)) {
                            log.error("🚨 НАЙДЕНА ПРОБЛЕМА: Поле {} имеет тип bytea!", field);
                        }
                    }
                } catch (Exception e) {
                    log.error("Ошибка проверки типа поля {}: {}", field, e.getMessage());
                    fieldTypes.put(field, "ERROR: " + e.getMessage());
                }
            }

            // Тест 2: Пытаемся выполнить LOWER() на каждом поле
            Map<String, String> lowerTests = new HashMap<>();

            for (String field : testFields) {
                try {
                    String lowerQuery = String.format(
                            "SELECT LOWER(%s) FROM user_activity_logs WHERE %s IS NOT NULL LIMIT 1",
                            field, field);
                    log.info("Тестируем запрос: {}", lowerQuery);

                    ResultSet rs = statement.executeQuery(lowerQuery);
                    if (rs.next()) {
                        lowerTests.put(field, "SUCCESS");
                        log.info("✅ LOWER({}) работает корректно", field);
                    } else {
                        lowerTests.put(field, "NO_DATA");
                        log.warn("⚠️ LOWER({}) - нет данных для тестирования", field);
                    }
                } catch (Exception e) {
                    lowerTests.put(field, "ERROR: " + e.getMessage());
                    log.error("🚨 LOWER({}) вызывает ошибку: {}", field, e.getMessage());

                    if (e.getMessage().contains("bytea")) {
                        log.error("🎯 ПОДТВЕРЖДЕНА ПРОБЛЕМА: Поле {} интерпретируется как bytea!", field);
                    }
                }
            }

            // Тест 3: Проверяем конкретный проблемный запрос из репозитория
            try {
                String problematicQuery = """
                        SELECT COUNT(*) FROM user_activity_logs
                        WHERE (username IS NOT NULL AND LOWER(username) LIKE LOWER(CONCAT('%', 'test', '%')))
                        LIMIT 1
                        """;
                log.info("Тестируем проблемный запрос из репозитория...");

                ResultSet rs = statement.executeQuery(problematicQuery);
                if (rs.next()) {
                    result.put("repositoryQueryTest", "SUCCESS");
                    log.info("✅ Проблемный запрос из репозитория работает");
                }
            } catch (Exception e) {
                result.put("repositoryQueryTest", "ERROR: " + e.getMessage());
                log.error("🚨 КРИТИЧЕСКАЯ ОШИБКА: Проблемный запрос из репозитория: {}", e.getMessage());
            }

            result.put("fieldTypes", fieldTypes);
            result.put("lowerFunctionTests", lowerTests);
            result.put("status", "success");
            result.put("diagnosis", "Диагностика типов полей и LOWER() функции завершена");

        } catch (Exception e) {
            log.error("Критическая ошибка при диагностике: ", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * ДИАГНОСТИКА: Проверяет реальную структуру таблицы и предлагает исправления
     */
    @GetMapping("/diagnosis-report")
    public ResponseEntity<Map<String, Object>> generateDiagnosisReport() {
        Map<String, Object> result = new HashMap<>();
        List<String> problems = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        log.info("=== ГЕНЕРАЦИЯ ДИАГНОСТИЧЕСКОГО ОТЧЕТА ===");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            // Проверяем критически важные поля
            String[] criticalFields = { "username", "first_name", "last_name", "order_id" };

            for (String field : criticalFields) {
                String query = String.format(
                        "SELECT column_name, data_type, character_maximum_length " +
                                "FROM information_schema.columns " +
                                "WHERE table_name = 'user_activity_logs' AND column_name = '%s'",
                        field);

                ResultSet rs = statement.executeQuery(query);
                if (rs.next()) {
                    String dataType = rs.getString("data_type");
                    String maxLength = rs.getString("character_maximum_length");

                    if ("bytea".equals(dataType)) {
                        problems.add(String.format("Поле '%s' имеет неправильный тип 'bytea' вместо 'varchar'", field));
                        recommendations.add(String
                                .format("ALTER TABLE user_activity_logs ALTER COLUMN %s TYPE VARCHAR(255);", field));
                        log.error("🚨 ПРОБЛЕМА: Поле {} имеет тип bytea", field);
                    } else {
                        log.info("✅ Поле {} имеет корректный тип: {} ({})", field, dataType, maxLength);
                    }
                }
            }

            // Проверяем LOB поле
            String lobQuery = """
                    SELECT column_name, data_type
                    FROM information_schema.columns
                    WHERE table_name = 'user_activity_logs' AND column_name = 'action_details'
                    """;

            ResultSet lobRs = statement.executeQuery(lobQuery);
            if (lobRs.next()) {
                String lobType = lobRs.getString("data_type");
                log.info("📝 LOB поле action_details имеет тип: {}", lobType);

                if ("bytea".equals(lobType)) {
                    problems.add("LOB поле 'action_details' использует bytea, что может влиять на другие поля");
                    recommendations.add(
                            "Рассмотреть изменение типа action_details на TEXT: ALTER TABLE user_activity_logs ALTER COLUMN action_details TYPE TEXT;");
                }
            }

            result.put("problems", problems);
            result.put("recommendations", recommendations);
            result.put("problemsCount", problems.size());
            result.put("status", problems.isEmpty() ? "healthy" : "issues_found");

            if (problems.isEmpty()) {
                result.put("message", "Проблем с типами данных не обнаружено");
                log.info("✅ Диагностика завершена: проблем не найдено");
            } else {
                result.put("message", String.format("Обнаружено %d проблем с типами данных", problems.size()));
                log.error("🚨 Диагностика завершена: найдено {} проблем", problems.size());
            }

        } catch (Exception e) {
            log.error("Ошибка генерации диагностического отчета: ", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return ResponseEntity.ok(result);
    }
}