package shit.back.service.admin.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Унифицированный сервис валидации для админской панели
 * Выносит общую логику валидации из контроллеров
 * Следует принципам SOLID - Single Responsibility Principle
 */
@Service
public class AdminValidationService {

    private static final Logger log = LoggerFactory.getLogger(AdminValidationService.class);

    // Паттерн для валидации ID заказа
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("^[A-Z0-9]{8}$");

    // Паттерн для валидации пользовательского ввода (безопасность)
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_.@]+$");

    /**
     * Валидация параметров пагинации
     * 
     * @param page номер страницы
     * @param size размер страницы
     * @return Map с результатами валидации
     */
    public Map<String, Object> validatePaginationParams(int page, int size) {
        Map<String, Object> result = new HashMap<>();

        // Нормализация параметров
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(100, size));

        boolean isValid = true;
        StringBuilder errors = new StringBuilder();

        if (page < 0) {
            errors.append("Page cannot be negative. ");
            isValid = false;
        }

        if (size < 1) {
            errors.append("Size must be at least 1. ");
            isValid = false;
        }

        if (size > 100) {
            errors.append("Size cannot exceed 100. ");
            isValid = false;
        }

        result.put("valid", isValid);
        result.put("page", normalizedPage);
        result.put("size", normalizedSize);
        result.put("errors", errors.toString().trim());

        if (!isValid) {
            log.debug("Pagination parameters validated with corrections: page={}->{}, size={}->{}",
                    page, normalizedPage, size, normalizedSize);
        }

        return result;
    }

    /**
     * Создание валидной сортировки
     * 
     * @param sortBy      поле для сортировки
     * @param sortDir     направление сортировки
     * @param validFields допустимые поля для сортировки
     * @return объект Sort
     */
    public Sort createValidSort(String sortBy, String sortDir, Set<String> validFields) {
        // Валидация поля сортировки
        String validSortBy = sortBy;
        if (!validFields.contains(sortBy)) {
            validSortBy = "createdAt"; // поле по умолчанию
            log.debug("Invalid sort field '{}', using default 'createdAt'", sortBy);
        }

        // Валидация направления сортировки
        Sort.Direction direction = Sort.Direction.DESC; // по умолчанию
        if ("asc".equalsIgnoreCase(sortDir)) {
            direction = Sort.Direction.ASC;
        } else if (!"desc".equalsIgnoreCase(sortDir)) {
            log.debug("Invalid sort direction '{}', using default 'desc'", sortDir);
        }

        return Sort.by(direction, validSortBy);
    }

    /**
     * Валидация формата ID заказа
     * 
     * @param orderId ID заказа
     * @return true если формат валиден
     */
    public boolean isValidOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return false;
        }

        return ORDER_ID_PATTERN.matcher(orderId.trim()).matches();
    }

    /**
     * Валидация пользовательского ввода на предмет безопасности
     * 
     * @param input пользовательский ввод
     * @return true если ввод безопасен
     */
    public boolean isSafeInput(String input) {
        if (input == null) {
            return true; // null считается безопасным
        }

        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
            return true; // пустая строка безопасна
        }

        // Проверка на SQL injection паттерны
        String lowerInput = trimmedInput.toLowerCase();
        if (lowerInput.contains("select") || lowerInput.contains("insert") ||
                lowerInput.contains("update") || lowerInput.contains("delete") ||
                lowerInput.contains("drop") || lowerInput.contains("union") ||
                lowerInput.contains("script") || lowerInput.contains("javascript")) {
            log.warn("Potentially malicious input detected: {}", input);
            return false;
        }

        return SAFE_INPUT_PATTERN.matcher(trimmedInput).matches();
    }

    /**
     * Валидация поискового запроса
     * 
     * @param searchQuery поисковый запрос
     * @return Map с результатами валидации
     */
    public Map<String, Object> validateSearchQuery(String searchQuery) {
        Map<String, Object> result = new HashMap<>();

        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            result.put("valid", true);
            result.put("query", "");
            result.put("sanitized", "");
            return result;
        }

        String trimmedQuery = searchQuery.trim();

        // Проверка длины
        if (trimmedQuery.length() > 200) {
            result.put("valid", false);
            result.put("error", "Search query too long (max 200 characters)");
            return result;
        }

        // Проверка безопасности
        if (!isSafeInput(trimmedQuery)) {
            result.put("valid", false);
            result.put("error", "Invalid characters in search query");
            return result;
        }

        // Санитизация запроса
        String sanitizedQuery = sanitizeSearchQuery(trimmedQuery);

        result.put("valid", true);
        result.put("query", trimmedQuery);
        result.put("sanitized", sanitizedQuery);

        return result;
    }

    /**
     * Санитизация поискового запроса
     * 
     * @param query исходный запрос
     * @return санитизированный запрос
     */
    private String sanitizeSearchQuery(String query) {
        if (query == null) {
            return "";
        }

        return query.trim()
                .replaceAll("[<>\"'%;()&+]", "") // удаляем потенциально опасные символы
                .replaceAll("\\s+", " ") // нормализуем пробелы
                .trim();
    }

    /**
     * Валидация диапазона дат
     * 
     * @param startDate начальная дата (строка)
     * @param endDate   конечная дата (строка)
     * @return Map с результатами валидации
     */
    public Map<String, Object> validateDateRange(String startDate, String endDate) {
        Map<String, Object> result = new HashMap<>();

        try {
            if ((startDate == null || startDate.trim().isEmpty()) &&
                    (endDate == null || endDate.trim().isEmpty())) {
                result.put("valid", true);
                result.put("hasDateFilter", false);
                return result;
            }

            // Базовая валидация формата даты (можно расширить)
            boolean validStart = isValidDateFormat(startDate);
            boolean validEnd = isValidDateFormat(endDate);

            if (!validStart || !validEnd) {
                result.put("valid", false);
                result.put("error", "Invalid date format. Use YYYY-MM-DD");
                return result;
            }

            result.put("valid", true);
            result.put("hasDateFilter", true);
            result.put("startDate", startDate);
            result.put("endDate", endDate);

        } catch (Exception e) {
            log.warn("Error validating date range: {} - {}", startDate, endDate, e);
            result.put("valid", false);
            result.put("error", "Error parsing dates");
        }

        return result;
    }

    /**
     * Простая валидация формата даты
     * 
     * @param dateStr строка с датой
     * @return true если формат корректен
     */
    private boolean isValidDateFormat(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return true; // пустая дата считается валидной
        }

        // Простая проверка формата YYYY-MM-DD
        return dateStr.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }

    /**
     * Валидация лимитов для API запросов
     * 
     * @param limit    лимит
     * @param maxLimit максимальный лимит
     * @return Map с результатами валидации
     */
    public Map<String, Object> validateLimit(Integer limit, int maxLimit) {
        Map<String, Object> result = new HashMap<>();

        int normalizedLimit = (limit != null) ? limit : 10; // по умолчанию 10

        if (normalizedLimit < 1) {
            normalizedLimit = 1;
        } else if (normalizedLimit > maxLimit) {
            normalizedLimit = maxLimit;
        }

        result.put("valid", true);
        result.put("limit", normalizedLimit);
        result.put("adjusted", !Integer.valueOf(normalizedLimit).equals(limit));

        return result;
    }

    /**
     * Валидация списка ID
     * 
     * @param ids      список ID
     * @param maxCount максимальное количество
     * @return Map с результатами валидации
     */
    public Map<String, Object> validateIdList(java.util.List<String> ids, int maxCount) {
        Map<String, Object> result = new HashMap<>();

        if (ids == null || ids.isEmpty()) {
            result.put("valid", false);
            result.put("error", "ID list cannot be empty");
            return result;
        }

        if (ids.size() > maxCount) {
            result.put("valid", false);
            result.put("error", "Too many IDs (max " + maxCount + ")");
            return result;
        }

        // Проверка каждого ID
        java.util.List<String> validIds = new java.util.ArrayList<>();
        java.util.List<String> invalidIds = new java.util.ArrayList<>();

        for (String id : ids) {
            if (isValidOrderId(id)) {
                validIds.add(id);
            } else {
                invalidIds.add(id);
            }
        }

        result.put("valid", invalidIds.isEmpty());
        result.put("validIds", validIds);
        result.put("invalidIds", invalidIds);
        result.put("totalCount", ids.size());
        result.put("validCount", validIds.size());

        return result;
    }
}