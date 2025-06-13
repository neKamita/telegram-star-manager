# ДИАГНОСТИЧЕСКИЕ ЛОГИ ДЛЯ ПОДТВЕРЖДЕНИЯ ПРОБЛЕМЫ ПРОИЗВОДИТЕЛЬНОСТИ

## Предлагаемые логи для AdminController.dashboard():

```java
@GetMapping
public String adminDashboard(Model model) {
    long startTime = System.currentTimeMillis();
    log.info("🔍 ДИАГНОСТИКА: Dashboard loading started");
    
    try {
        // Логируем каждый шаг
        long step1Start = System.currentTimeMillis();
        AdminDashboardCacheService.LightweightDashboardOverview lightweightOverview = adminDashboardCacheService.getLightweightDashboard();
        long step1Time = System.currentTimeMillis() - step1Start;
        log.info("🔍 ДИАГНОСТИКА: getLightweightDashboard() took {}ms", step1Time);
        
        long step2Start = System.currentTimeMillis();
        AdminDashboardService.PerformanceMetrics performance = adminDashboardCacheService.getPerformanceMetricsCached();
        long step2Time = System.currentTimeMillis() - step2Start;
        log.info("🔍 ДИАГНОСТИКА: getPerformanceMetricsCached() took {}ms", step2Time);
        
        long step3Start = System.currentTimeMillis();
        AdminDashboardCacheService.SimplifiedRecentActivity recentActivity = adminDashboardCacheService.getRecentActivityCached();
        long step3Time = System.currentTimeMillis() - step3Start;
        log.info("🔍 ДИАГНОСТИКА: getRecentActivityCached() took {}ms", step3Time);
        
        long step4Start = System.currentTimeMillis();
        AdminDashboardService.CombinedRecentActivity combinedActivity = adminDashboardService.getCombinedRecentActivity();
        long step4Time = System.currentTimeMillis() - step4Start;
        log.info("🔍 ДИАГНОСТИКА: getCombinedRecentActivity() took {}ms", step4Time);
        
        long step5Start = System.currentTimeMillis();
        AdminDashboardService.SystemHealth systemHealth = adminDashboardService.getSystemHealth();
        long step5Time = System.currentTimeMillis() - step5Start;
        log.info("🔍 ДИАГНОСТИКА: getSystemHealth() took {}ms", step5Time);
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🔍 ДИАГНОСТИКА: Total dashboard loading time: {}ms (step1: {}ms, step2: {}ms, step3: {}ms, step4: {}ms, step5: {}ms)", 
                totalTime, step1Time, step2Time, step3Time, step4Time, step5Time);
                
        return "admin/dashboard";
    } catch (Exception e) {
        long totalTime = System.currentTimeMillis() - startTime;
        log.error("🔍 ДИАГНОСТИКА: Dashboard loading failed after {}ms", totalTime, e);
        // ... остальной код
    }
}
```

## Предлагаемые логи для AdminOrdersController.ordersListPage():

```java
@GetMapping
public String ordersListPage(...) {
    long startTime = System.currentTimeMillis();
    log.info("🔍 ДИАГНОСТИКА: Orders page loading started");
    
    try {
        // ... проверки аутентификации
        
        long ordersStart = System.currentTimeMillis();
        Page<OrderEntity> orders = getFilteredOrders(status, search, startDate, endDate, pageable);
        long ordersTime = System.currentTimeMillis() - ordersStart;
        log.info("🔍 ДИАГНОСТИКА: getFilteredOrders() took {}ms, returned {} items", ordersTime, orders.getNumberOfElements());
        
        long statsStart = System.currentTimeMillis();
        OrderService.OrderStatistics orderStats = orderService.getOrderStatistics();
        long statsTime = System.currentTimeMillis() - statsStart;
        log.info("🔍 ДИАГНОСТИКА: getOrderStatistics() took {}ms", statsTime);
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🔍 ДИАГНОСТИКА: Total orders page loading time: {}ms (orders: {}ms, stats: {}ms)", 
                totalTime, ordersTime, statsTime);
                
        return "admin/orders";
    } catch (Exception e) {
        long totalTime = System.currentTimeMillis() - startTime;
        log.error("🔍 ДИАГНОСТИКА: Orders page loading failed after {}ms", totalTime, e);
        // ... остальной код
    }
}
```

## Предлагаемые логи для OrderService.getOrderStatistics():

```java
@Transactional(readOnly = true)
public OrderStatistics getOrderStatistics() {
    long startTime = System.currentTimeMillis();
    log.info("🔍 ДИАГНОСТИКА: OrderStatistics calculation started");
    
    long totalOrdersStart = System.currentTimeMillis();
    long totalOrders = getTotalOrdersCount();
    long totalOrdersTime = System.currentTimeMillis() - totalOrdersStart;
    log.info("🔍 ДИАГНОСТИКА: getTotalOrdersCount() took {}ms, result: {}", totalOrdersTime, totalOrders);
    
    long completedOrdersStart = System.currentTimeMillis();
    long completedOrders = getCompletedOrdersCount();
    long completedOrdersTime = System.currentTimeMillis() - completedOrdersStart;
    log.info("🔍 ДИАГНОСТИКА: getCompletedOrdersCount() took {}ms, result: {}", completedOrdersTime, completedOrders);
    
    long pendingOrdersStart = System.currentTimeMillis();
    long pendingOrders = getPendingOrdersCount();
    long pendingOrdersTime = System.currentTimeMillis() - pendingOrdersStart;
    log.info("🔍 ДИАГНОСТИКА: getPendingOrdersCount() took {}ms, result: {}", pendingOrdersTime, pendingOrders);
    
    long totalRevenueStart = System.currentTimeMillis();
    BigDecimal totalRevenue = getTotalRevenue();
    long totalRevenueTime = System.currentTimeMillis() - totalRevenueStart;
    log.info("🔍 ДИАГНОСТИКА: getTotalRevenue() took {}ms, result: {}", totalRevenueTime, totalRevenue);
    
    long todayRevenueStart = System.currentTimeMillis();
    BigDecimal todayRevenue = getTodayRevenue();
    long todayRevenueTime = System.currentTimeMillis() - todayRevenueStart;
    log.info("🔍 ДИАГНОСТИКА: getTodayRevenue() took {}ms, result: {}", todayRevenueTime, todayRevenue);
    
    long monthRevenueStart = System.currentTimeMillis();
    BigDecimal monthRevenue = getMonthRevenue();
    long monthRevenueTime = System.currentTimeMillis() - monthRevenueStart;
    log.info("🔍 ДИАГНОСТИКА: getMonthRevenue() took {}ms, result: {}", monthRevenueTime, monthRevenue);
    
    long avgOrderValueStart = System.currentTimeMillis();
    BigDecimal averageOrderValue = getAverageOrderValue();
    long avgOrderValueTime = System.currentTimeMillis() - avgOrderValueStart;
    log.info("🔍 ДИАГНОСТИКА: getAverageOrderValue() took {}ms, result: {}", avgOrderValueTime, averageOrderValue);
    
    long conversionRateStart = System.currentTimeMillis();
    Double conversionRate = getOrderConversionRate();
    long conversionRateTime = System.currentTimeMillis() - conversionRateStart;
    log.info("🔍 ДИАГНОСТИКА: getOrderConversionRate() took {}ms, result: {}", conversionRateTime, conversionRate);
    
    long totalTime = System.currentTimeMillis() - startTime;
    log.info("🔍 ДИАГНОСТИКА: Total OrderStatistics calculation time: {}ms (totalOrders: {}ms, completedOrders: {}ms, pendingOrders: {}ms, totalRevenue: {}ms, todayRevenue: {}ms, monthRevenue: {}ms, avgOrderValue: {}ms, conversionRate: {}ms)", 
            totalTime, totalOrdersTime, completedOrdersTime, pendingOrdersTime, totalRevenueTime, todayRevenueTime, monthRevenueTime, avgOrderValueTime, conversionRateTime);
    
    return OrderStatistics.builder()
            .totalOrders(totalOrders)
            .completedOrders(completedOrders)
            .pendingOrders(pendingOrders)
            .failedOrders(getFailedOrdersCount())
            .totalRevenue(totalRevenue)
            .todayRevenue(todayRevenue)
            .monthRevenue(monthRevenue)
            .averageOrderValue(averageOrderValue)
            .conversionRate(conversionRate)
            .build();
}
```

## Для сравнения - логи для Activity-logs (быстрая страница):

```java
@GetMapping("/activity-logs")
public String activityLogsPage(...) {
    long startTime = System.currentTimeMillis();
    log.info("🔍 ДИАГНОСТИКА: Activity-logs page loading started");
    
    try {
        long activitiesStart = System.currentTimeMillis();
        Page<UserActivityLogEntity> activities = activityLogService.getActivitiesWithFilters(...);
        long activitiesTime = System.currentTimeMillis() - activitiesStart;
        log.info("🔍 ДИАГНОСТИКА: getActivitiesWithFilters() took {}ms, returned {} items", activitiesTime, activities.getNumberOfElements());
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("🔍 ДИАГНОСТИКА: Total activity-logs page loading time: {}ms", totalTime);
        
        return "admin/activity-logs";
    } catch (Exception e) {
        long totalTime = System.currentTimeMillis() - startTime;
        log.error("🔍 ДИАГНОСТИКА: Activity-logs page loading failed after {}ms", totalTime, e);
        // ... остальной код
    }
}