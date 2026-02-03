package gg.playit.minecraft.logger;

/**
 * Log levels for controlling console output verbosity.
 */
public enum LogLevel {
    DEBUG(0, "§7[DEBUG]§r"),
    INFO(1, "§b[INFO]§r"),
    WARN(2, "§e[WARN]§r"),
    ERROR(3, "§c[ERROR]§r");

    private final int priority;
    private final String prefix;

    LogLevel(int priority, String prefix) {
        this.priority = priority;
        this.prefix = prefix;
    }

    public int getPriority() {
        return priority;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Check if this level should be shown given the minimum level.
     */
    public boolean shouldLog(LogLevel minLevel) {
        return this.priority >= minLevel.priority;
    }

    /**
     * Parse log level from string, defaults to INFO.
     */
    public static LogLevel fromString(String value) {
        if (value == null) return INFO;
        try {
            return LogLevel.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
