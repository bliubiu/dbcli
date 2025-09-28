package com.dbcli.exception;

/**
 * 异常处理结果
 * 包含异常处理的策略和配置信息
 */
public class ExceptionHandlingResult {
    private final Exception exception;
    private final EnhancedExceptionHandler.ExceptionCategory category;
    private final String context;
    private final boolean shouldRetry;
    private final long retryDelay;
    private final int maxRetries;
    private final boolean recoverable;
    private final boolean requiresCircuitBreaker;
    private final boolean requiresManualIntervention;
    private final boolean criticalError;
    private final boolean requiresResourceCleanup;
    
    private ExceptionHandlingResult(Builder builder) {
        this.exception = builder.exception;
        this.category = builder.category;
        this.context = builder.context;
        this.shouldRetry = builder.shouldRetry;
        this.retryDelay = builder.retryDelay;
        this.maxRetries = builder.maxRetries;
        this.recoverable = builder.recoverable;
        this.requiresCircuitBreaker = builder.requiresCircuitBreaker;
        this.requiresManualIntervention = builder.requiresManualIntervention;
        this.criticalError = builder.criticalError;
        this.requiresResourceCleanup = builder.requiresResourceCleanup;
    }
    
    // Getters
    public Exception getException() { return exception; }
    public EnhancedExceptionHandler.ExceptionCategory getCategory() { return category; }
    public String getContext() { return context; }
    public boolean shouldRetry() { return shouldRetry; }
    public long getRetryDelay() { return retryDelay; }
    public int getMaxRetries() { return maxRetries; }
    public boolean isRecoverable() { return recoverable; }
    public boolean requiresCircuitBreaker() { return requiresCircuitBreaker; }
    public boolean requiresManualIntervention() { return requiresManualIntervention; }
    public boolean isCriticalError() { return criticalError; }
    public boolean requiresResourceCleanup() { return requiresResourceCleanup; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Exception exception;
        private EnhancedExceptionHandler.ExceptionCategory category;
        private String context;
        private boolean shouldRetry = false;
        private long retryDelay = 0;
        private int maxRetries = 0;
        private boolean recoverable = false;
        private boolean requiresCircuitBreaker = false;
        private boolean requiresManualIntervention = false;
        private boolean criticalError = false;
        private boolean requiresResourceCleanup = false;
        
        public Builder exception(Exception exception) {
            this.exception = exception;
            return this;
        }
        
        public Builder category(EnhancedExceptionHandler.ExceptionCategory category) {
            this.category = category;
            return this;
        }
        
        public Builder context(String context) {
            this.context = context;
            return this;
        }
        
        public Builder shouldRetry(boolean shouldRetry) {
            this.shouldRetry = shouldRetry;
            return this;
        }
        
        public Builder retryDelay(long retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder recoverable(boolean recoverable) {
            this.recoverable = recoverable;
            return this;
        }
        
        public Builder requiresCircuitBreaker(boolean requiresCircuitBreaker) {
            this.requiresCircuitBreaker = requiresCircuitBreaker;
            return this;
        }
        
        public Builder requiresManualIntervention(boolean requiresManualIntervention) {
            this.requiresManualIntervention = requiresManualIntervention;
            return this;
        }
        
        public Builder criticalError(boolean criticalError) {
            this.criticalError = criticalError;
            return this;
        }
        
        public Builder requiresResourceCleanup(boolean requiresResourceCleanup) {
            this.requiresResourceCleanup = requiresResourceCleanup;
            return this;
        }
        
        public ExceptionHandlingResult build() {
            return new ExceptionHandlingResult(this);
        }
    }
}