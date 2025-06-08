package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.StarPackageEntity;
import shit.back.model.StarPackage;
import shit.back.repository.StarPackageJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing star packages and package-related operations
 */
@Slf4j
@Service
@Transactional
public class StarPackageService {
    
    @Autowired
    private StarPackageJpaRepository packageRepository;
    
    /**
     * Create a new star package
     */
    public StarPackageEntity createPackage(StarPackage starPackage) {
        log.info("Creating new star package: {} with {} stars", 
                starPackage.getPackageId(), starPackage.getStars());
        
        StarPackageEntity entity = new StarPackageEntity(
                starPackage.getPackageId(),
                "⭐ " + starPackage.getStars() + " Stars",
                starPackage.getStars(),
                starPackage.getOriginalPrice(),
                starPackage.getDiscountPercent()
        );
        entity.setEmoji("⭐");
        entity.setSortOrder(starPackage.getStars());
        entity.setDescription("Package with " + starPackage.getStars() + " Telegram Stars");
        
        StarPackageEntity saved = packageRepository.save(entity);
        log.info("Star package created with ID: {}", saved.getId());
        return saved;
    }
    
    /**
     * Update star package
     */
    public Optional<StarPackageEntity> updatePackage(Long packageId, StarPackage starPackage) {
        log.info("Updating star package ID: {}", packageId);
        
        Optional<StarPackageEntity> packageOpt = packageRepository.findById(packageId);
        if (packageOpt.isPresent()) {
            StarPackageEntity entity = packageOpt.get();
            entity.setName(starPackage.getPackageId());
            entity.setStarCount(starPackage.getStars());
            entity.setOriginalPrice(starPackage.getOriginalPrice());
            entity.setDiscountPercentage(starPackage.getDiscountPercent());
            entity.updateFinalPrice(); // Пересчитываем финальную цену
            
            StarPackageEntity updated = packageRepository.save(entity);
            log.info("Star package {} updated", packageId);
            return Optional.of(updated);
        }
        
        log.warn("Star package {} not found for update", packageId);
        return Optional.empty();
    }
    
    /**
     * Get package by ID
     */
    @Transactional(readOnly = true)
    public Optional<StarPackageEntity> getPackageById(Long packageId) {
        return packageRepository.findById(packageId);
    }
    
    /**
     * Get package by name
     */
    @Transactional(readOnly = true)
    public Optional<StarPackageEntity> getPackageByName(String name) {
        return packageRepository.findByName(name);
    }
    
    /**
     * Get all active packages
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getActivePackages() {
        return packageRepository.findByIsEnabledTrueOrderBySortOrderAscCreatedAtAsc();
    }
    
    /**
     * Get available packages (considering time validity)
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getAvailablePackages() {
        return packageRepository.findAvailablePackages(LocalDateTime.now());
    }
    
    /**
     * Get all packages (active and inactive)
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getAllPackages() {
        return packageRepository.findAll();
    }
    
    /**
     * Get paginated packages
     */
    @Transactional(readOnly = true)
    public Page<StarPackageEntity> getPackages(Pageable pageable) {
        return packageRepository.findAll(pageable);
    }
    
    /**
     * Search packages
     */
    @Transactional(readOnly = true)
    public Page<StarPackageEntity> searchPackages(String searchTerm, Pageable pageable) {
        return packageRepository.searchPackages(searchTerm, pageable);
    }
    
    /**
     * Get packages in price range
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getPackagesByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return packageRepository.findPackagesInPriceRange(minPrice, maxPrice);
    }
    
    /**
     * Get popular packages
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getPopularPackages() {
        return packageRepository.findByIsEnabledTrueAndIsPopularTrueOrderBySortOrderAsc();
    }
    
    /**
     * Get discounted packages
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getDiscountedPackages() {
        return packageRepository.findDiscountedPackages();
    }
    
    /**
     * Get top selling packages
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getTopSellingPackages(int limit) {
        return packageRepository.findTopSellingPackages(PageRequest.of(0, limit));
    }
    
    /**
     * Get best value packages
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getBestValuePackages(int limit) {
        return packageRepository.findBestValuePackages(PageRequest.of(0, limit));
    }
    
    /**
     * Get packages with bonus stars
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getPackagesWithBonus() {
        return packageRepository.findPackagesWithBonus();
    }
    
    /**
     * Toggle package active status
     */
    public Optional<StarPackageEntity> togglePackageStatus(Long packageId) {
        log.info("Toggling status for package ID: {}", packageId);
        
        Optional<StarPackageEntity> packageOpt = packageRepository.findById(packageId);
        if (packageOpt.isPresent()) {
            StarPackageEntity entity = packageOpt.get();
            entity.setIsEnabled(!entity.getIsEnabled());
            
            StarPackageEntity updated = packageRepository.save(entity);
            log.info("Package {} status toggled to {}", packageId, updated.getIsEnabled());
            return Optional.of(updated);
        }
        
        log.warn("Package {} not found for status toggle", packageId);
        return Optional.empty();
    }
    
    /**
     * Mark package as popular
     */
    public Optional<StarPackageEntity> togglePopularStatus(Long packageId) {
        log.info("Toggling popular status for package ID: {}", packageId);
        
        Optional<StarPackageEntity> packageOpt = packageRepository.findById(packageId);
        if (packageOpt.isPresent()) {
            StarPackageEntity entity = packageOpt.get();
            entity.setIsPopular(!entity.getIsPopular());
            
            StarPackageEntity updated = packageRepository.save(entity);
            log.info("Package {} popular status toggled to {}", packageId, updated.getIsPopular());
            return Optional.of(updated);
        }
        
        log.warn("Package {} not found for popular status toggle", packageId);
        return Optional.empty();
    }
    
    /**
     * Delete package
     */
    public boolean deletePackage(Long packageId) {
        log.info("Deleting package ID: {}", packageId);
        
        if (packageRepository.existsById(packageId)) {
            packageRepository.deleteById(packageId);
            log.info("Package {} deleted", packageId);
            return true;
        }
        
        log.warn("Package {} not found for deletion", packageId);
        return false;
    }
    
    /**
     * Record a sale for package
     */
    @Transactional
    public void recordSale(String packageName, BigDecimal amount) {
        int updated = packageRepository.updateSalesStatistics(packageName, amount);
        if (updated > 0) {
            log.info("Sale recorded for package {}: {}", packageName, amount);
        } else {
            log.warn("Package {} not found for sale recording", packageName);
        }
    }
    
    /**
     * Deactivate expired packages
     */
    @Transactional
    public int deactivateExpiredPackages() {
        int deactivated = packageRepository.deactivateExpiredPackages(LocalDateTime.now());
        if (deactivated > 0) {
            log.info("Deactivated {} expired packages", deactivated);
        }
        return deactivated;
    }
    
    // Statistics methods
    
    /**
     * Get total packages count
     */
    @Transactional(readOnly = true)
    public long getTotalPackagesCount() {
        return packageRepository.count();
    }
    
    /**
     * Get active packages count
     */
    @Transactional(readOnly = true)
    public long getActivePackagesCount() {
        return packageRepository.findByIsEnabledTrueOrderBySortOrderAscCreatedAtAsc().size();
    }
    
    /**
     * Get packages without sales
     */
    @Transactional(readOnly = true)
    public List<StarPackageEntity> getPackagesWithoutSales() {
        return packageRepository.findPackagesWithoutSales();
    }
    
    /**
     * Get total sales statistics
     */
    @Transactional(readOnly = true)
    public SalesStatistics getTotalSalesStatistics() {
        List<Object[]> results = packageRepository.getTotalSalesStatistics();
        if (!results.isEmpty()) {
            Object[] row = results.get(0);
            return SalesStatistics.builder()
                    .totalSold(row[0] != null ? ((Number) row[0]).longValue() : 0L)
                    .totalRevenue(row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO)
                    .build();
        }
        return SalesStatistics.builder()
                .totalSold(0L)
                .totalRevenue(BigDecimal.ZERO)
                .build();
    }
    
    /**
     * Get sales by package type
     */
    @Transactional(readOnly = true)
    public List<PackageTypeSales> getSalesByPackageType() {
        List<Object[]> results = packageRepository.getSalesByPackageType();
        
        return results.stream()
                .map(row -> PackageTypeSales.builder()
                        .packageType((StarPackageEntity.PackageType) row[0])
                        .totalSold(((Number) row[1]).longValue())
                        .totalRevenue((BigDecimal) row[2])
                        .build())
                .toList();
    }
    
    /**
     * Get average package price
     */
    @Transactional(readOnly = true)
    public BigDecimal getAveragePackagePrice() {
        BigDecimal avg = packageRepository.getAveragePackagePrice();
        return avg != null ? avg : BigDecimal.ZERO;
    }
    
    /**
     * Get package conversion rate
     */
    @Transactional(readOnly = true)
    public Double getPackageConversionRate() {
        Double rate = packageRepository.getPackageConversionRate();
        return rate != null ? rate : 0.0;
    }
    
    /**
     * Get package statistics
     */
    @Transactional(readOnly = true)
    public PackageStatistics getPackageStatistics() {
        SalesStatistics sales = getTotalSalesStatistics();
        
        return PackageStatistics.builder()
                .totalPackages(getTotalPackagesCount())
                .activePackages(getActivePackagesCount())
                .inactivePackages(getTotalPackagesCount() - getActivePackagesCount())
                .averagePrice(getAveragePackagePrice())
                .totalSold(sales.getTotalSold())
                .totalRevenue(sales.getTotalRevenue())
                .conversionRate(getPackageConversionRate())
                .build();
    }
    
    /**
     * Initialize default packages
     */
    @Transactional
    public void initializeDefaultPackages() {
        if (packageRepository.count() == 0) {
            log.info("Initializing default star packages");
            
            // Создаем стандартные пакеты
            createDefaultPackage(100, new BigDecimal("1.99"), 0);
            createDefaultPackage(500, new BigDecimal("9.99"), 5);
            createDefaultPackage(1000, new BigDecimal("19.99"), 10);
            createDefaultPackage(2500, new BigDecimal("49.99"), 15);
            createDefaultPackage(5000, new BigDecimal("99.99"), 20);
            createDefaultPackage(10000, new BigDecimal("199.99"), 25);
            
            log.info("Default star packages initialized");
        }
    }
    
    private void createDefaultPackage(int stars, BigDecimal price, int discount) {
        StarPackage pkg = StarPackage.createPackage(stars, price, discount);
        createPackage(pkg);
    }
    
    // Data transfer objects
    
    @lombok.Data
    @lombok.Builder
    public static class PackageStatistics {
        private long totalPackages;
        private long activePackages;
        private long inactivePackages;
        private BigDecimal averagePrice;
        private Long totalSold;
        private BigDecimal totalRevenue;
        private Double conversionRate;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SalesStatistics {
        private Long totalSold;
        private BigDecimal totalRevenue;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PackageTypeSales {
        private StarPackageEntity.PackageType packageType;
        private Long totalSold;
        private BigDecimal totalRevenue;
    }
}
