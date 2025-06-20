package shit.back.application.balance.common;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Functional Result pattern для обработки ошибок без try-catch
 * 
 * Заменяет nested try-catch blocks на functional approach
 * Следует принципам functional programming и error handling
 */
public sealed interface Result<T> permits Result.Success, Result.Error {

    /**
     * Создание успешного результата
     */
    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Создание результата с ошибкой
     */
    static <T> Result<T> error(Exception exception) {
        return new Error<>(exception);
    }

    /**
     * Создание результата с ошибкой из сообщения
     */
    static <T> Result<T> error(String message) {
        return new Error<>(new RuntimeException(message));
    }

    /**
     * Проверка на успех
     */
    boolean isSuccess();

    /**
     * Проверка на ошибку
     */
    boolean isError();

    /**
     * Получение значения (может вызвать исключение)
     */
    T getValue();

    /**
     * Получение ошибки
     */
    Exception getError();

    /**
     * Получение значения или default
     */
    T getValueOrElse(T defaultValue);

    /**
     * Получение значения или вычисление через supplier
     */
    T getValueOrElse(Supplier<T> supplier);

    /**
     * Functional map operation
     */
    <U> Result<U> map(Function<T, U> mapper);

    /**
     * Functional flatMap operation для цепочки операций
     */
    <U> Result<U> flatMap(Function<T, Result<U>> mapper);

    /**
     * Map error operation
     */
    Result<T> mapError(Function<Exception, Exception> mapper);

    /**
     * Tap operation для side effects при успехе
     */
    Result<T> tapSuccess(Consumer<T> action);

    /**
     * Tap operation для side effects при ошибке
     */
    Result<T> tapError(Consumer<Exception> action);

    /**
     * Восстановление от ошибки
     */
    Result<T> recover(Function<Exception, T> recovery);

    /**
     * Восстановление от ошибки с другим Result
     */
    Result<T> recoverWith(Function<Exception, Result<T>> recovery);

    /**
     * Success implementation
     */
    record Success<T>(T value) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public Exception getError() {
            throw new UnsupportedOperationException("Success result has no error");
        }

        @Override
        public T getValueOrElse(T defaultValue) {
            return value;
        }

        @Override
        public T getValueOrElse(Supplier<T> supplier) {
            return value;
        }

        @Override
        public <U> Result<U> map(Function<T, U> mapper) {
            try {
                return Result.success(mapper.apply(value));
            } catch (Exception e) {
                return Result.error(e);
            }
        }

        @Override
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return Result.error(e);
            }
        }

        @Override
        public Result<T> mapError(Function<Exception, Exception> mapper) {
            return this;
        }

        @Override
        public Result<T> tapSuccess(Consumer<T> action) {
            try {
                action.accept(value);
            } catch (Exception e) {
                // Log but don't change the result
            }
            return this;
        }

        @Override
        public Result<T> tapError(Consumer<Exception> action) {
            return this;
        }

        @Override
        public Result<T> recover(Function<Exception, T> recovery) {
            return this;
        }

        @Override
        public Result<T> recoverWith(Function<Exception, Result<T>> recovery) {
            return this;
        }
    }

    /**
     * Error implementation
     */
    record Error<T>(Exception exception) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isError() {
            return true;
        }

        @Override
        public T getValue() {
            throw new RuntimeException("Error result has no value", exception);
        }

        @Override
        public Exception getError() {
            return exception;
        }

        @Override
        public T getValueOrElse(T defaultValue) {
            return defaultValue;
        }

        @Override
        public T getValueOrElse(Supplier<T> supplier) {
            return supplier.get();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> map(Function<T, U> mapper) {
            return (Result<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            return (Result<U>) this;
        }

        @Override
        public Result<T> mapError(Function<Exception, Exception> mapper) {
            try {
                return Result.error(mapper.apply(exception));
            } catch (Exception e) {
                return Result.error(e);
            }
        }

        @Override
        public Result<T> tapSuccess(Consumer<T> action) {
            return this;
        }

        @Override
        public Result<T> tapError(Consumer<Exception> action) {
            try {
                action.accept(exception);
            } catch (Exception e) {
                // Log but don't change the result
            }
            return this;
        }

        @Override
        public Result<T> recover(Function<Exception, T> recovery) {
            try {
                return Result.success(recovery.apply(exception));
            } catch (Exception e) {
                return Result.error(e);
            }
        }

        @Override
        public Result<T> recoverWith(Function<Exception, Result<T>> recovery) {
            try {
                return recovery.apply(exception);
            } catch (Exception e) {
                return Result.error(e);
            }
        }
    }

    /**
     * Utility methods
     */
    static <T> Result<T> fromOptional(java.util.Optional<T> optional, String errorMessage) {
        return optional.map(Result::<T>success)
                .orElse(Result.error(errorMessage));
    }

    static <T> Result<T> fromOptional(java.util.Optional<T> optional, Supplier<Exception> errorSupplier) {
        return optional.map(Result::<T>success)
                .orElse(Result.error(errorSupplier.get()));
    }

    static <T> Result<T> fromSupplier(Supplier<T> supplier) {
        try {
            return Result.success(supplier.get());
        } catch (Exception e) {
            return Result.error(e);
        }
    }

    /**
     * Combine multiple results
     */
    static <T> Result<java.util.List<T>> sequence(java.util.List<Result<T>> results) {
        java.util.List<T> values = new java.util.ArrayList<>();

        for (Result<T> result : results) {
            if (result.isError()) {
                return Result.error(result.getError());
            }
            values.add(result.getValue());
        }

        return Result.success(values);
    }
}