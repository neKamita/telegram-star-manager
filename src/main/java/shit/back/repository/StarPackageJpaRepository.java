package shit.back.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.entity.StarPackageEntity;
import shit.back.entity.StarPackageEntity.PackageType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StarPackageJpaRepository extends JpaRepository<StarPackageEntity, Long> {
    
    // Поиск по имени
    Optional<StarPackageEntity> findByName(String name);
    
    boolean existsByName(String name);
    
    // Активные пакеты
    List<StarPackageEntity> findByIsEnabledTrueOrderBySortOrderAscCreatedAtAsc();
    
    Page<StarPackageEntity> findByIsEnabledTrueOrderBySortOrderAscCreatedAtAsc(Pageable pageable);
    
    // Доступные пакеты (с учетом времени действия)
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true AND " +
           "(p.validFrom IS NULL OR p.validFrom <= :now) AND " +
           "(p.validUntil IS NULL OR p.validUntil >= :now) " +
           "ORDER BY p.sortOrder ASC, p.createdAt ASC")
    List<StarPackageEntity> findAvailablePackages(@Param("now") LocalDateTime now);
    
    // Популярные пакеты
    List<StarPackageEntity> findByIsEnabledTrueAndIsPopularTrueOrderBySortOrderAsc();
    
    // Пакеты по типу
    List<StarPackageEntity> findByPackageTypeAndIsEnabledTrueOrderBySortOrderAsc(PackageType packageType);
    
    // Пакеты со скидкой
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true AND p.discountPercentage > 0 ORDER BY p.discountPercentage DESC")
    List<StarPackageEntity> findDiscountedPackages();
    
    // Пакеты в ценовом диапазоне
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true AND p.finalPrice BETWEEN :minPrice AND :maxPrice ORDER BY p.finalPrice ASC")
    List<StarPackageEntity> findPackagesInPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);
    
    // Топ продаваемые пакеты
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true ORDER BY p.totalSold DESC")
    List<StarPackageEntity> findTopSellingPackages(Pageable pageable);
    
    // Статистика продаж
    @Query("SELECT SUM(p.totalSold), SUM(p.revenueGenerated) FROM StarPackageEntity p")
    List<Object[]> getTotalSalesStatistics();
    
    @Query("SELECT p.packageType, SUM(p.totalSold), SUM(p.revenueGenerated) FROM StarPackageEntity p GROUP BY p.packageType ORDER BY SUM(p.totalSold) DESC")
    List<Object[]> getSalesByPackageType();
    
    // Пакеты с бонусными звездами
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true AND p.bonusStars > 0 ORDER BY p.bonusStars DESC")
    List<StarPackageEntity> findPackagesWithBonus();
    
    // Истекающие пакеты
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true AND p.validUntil IS NOT NULL AND p.validUntil BETWEEN :now AND :soon")
    List<StarPackageEntity> findExpiringPackages(@Param("now") LocalDateTime now, @Param("soon") LocalDateTime soon);
    
    // Поиск пакетов
    @Query("SELECT p FROM StarPackageEntity p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<StarPackageEntity> searchPackages(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    // Обновление статистики продаж
    @Modifying
    @Query("UPDATE StarPackageEntity p SET p.totalSold = p.totalSold + 1, p.revenueGenerated = p.revenueGenerated + :amount WHERE p.name = :packageName")
    int updateSalesStatistics(@Param("packageName") String packageName, @Param("amount") BigDecimal amount);
    
    // Пакеты без продаж
    @Query("SELECT p FROM StarPackageEntity p WHERE p.totalSold = 0 ORDER BY p.createdAt DESC")
    List<StarPackageEntity> findPackagesWithoutSales();
    
    // Средняя цена пакетов
    @Query("SELECT AVG(p.finalPrice) FROM StarPackageEntity p WHERE p.isEnabled = true")
    BigDecimal getAveragePackagePrice();
    
    // Пакеты по валюте
    List<StarPackageEntity> findByCurrencyAndIsEnabledTrueOrderBySortOrderAsc(String currency);
    
    // Самые дорогие/дешевые пакеты
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true ORDER BY p.finalPrice DESC")
    List<StarPackageEntity> findMostExpensivePackages(Pageable pageable);
    
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true ORDER BY p.finalPrice ASC")
    List<StarPackageEntity> findCheapestPackages(Pageable pageable);
    
    // Лучшее соотношение цена/звезда
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true ORDER BY (p.finalPrice / p.starCount) ASC")
    List<StarPackageEntity> findBestValuePackages(Pageable pageable);
    
    // Пакеты с ограничениями
    @Query("SELECT p FROM StarPackageEntity p WHERE p.minOrderCount IS NOT NULL OR p.maxPerUser IS NOT NULL")
    List<StarPackageEntity> findPackagesWithRestrictions();
    
    // Активные промо-пакеты
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true AND p.packageType = 'PROMOTIONAL' AND " +
           "(p.validUntil IS NULL OR p.validUntil >= :now) ORDER BY p.discountPercentage DESC")
    List<StarPackageEntity> findActivePromotionalPackages(@Param("now") LocalDateTime now);
    
    // Пакеты для VIP пользователей
    @Query("SELECT p FROM StarPackageEntity p WHERE p.isEnabled = true AND (p.packageType = 'VIP' OR p.minOrderCount IS NOT NULL) ORDER BY p.finalPrice DESC")
    List<StarPackageEntity> findVipPackages();
    
    // Недавно созданные пакеты
    @Query("SELECT p FROM StarPackageEntity p WHERE p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<StarPackageEntity> findRecentlyCreatedPackages(@Param("since") LocalDateTime since);
    
    // Пакеты по создателю
    List<StarPackageEntity> findByCreatedByOrderByCreatedAtDesc(String createdBy);
    
    // Конверсия пакетов (пакеты с продажами vs все пакеты)
    @Query("SELECT " +
           "SUM(CASE WHEN p.totalSold > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(p) " +
           "FROM StarPackageEntity p WHERE p.isEnabled = true")
    Double getPackageConversionRate();
    
    // Доходность по периодам
    @Query("SELECT DATE(p.updatedAt), SUM(p.revenueGenerated) FROM StarPackageEntity p WHERE p.updatedAt >= :fromDate GROUP BY DATE(p.updatedAt) ORDER BY DATE(p.updatedAt)")
    List<Object[]> getDailyRevenue(@Param("fromDate") LocalDateTime fromDate);
    
    // Пакеты требующие обновления цен
    @Query("SELECT p FROM StarPackageEntity p WHERE p.finalPrice != " +
           "(CASE WHEN p.discountPercentage > 0 THEN p.originalPrice - (p.originalPrice * p.discountPercentage / 100) ELSE p.originalPrice END)")
    List<StarPackageEntity> findPackagesWithInconsistentPricing();
    
    // Деактивация истекших пакетов
    @Modifying
    @Query("UPDATE StarPackageEntity p SET p.isEnabled = false WHERE p.validUntil IS NOT NULL AND p.validUntil < :now")
    int deactivateExpiredPackages(@Param("now") LocalDateTime now);
}
