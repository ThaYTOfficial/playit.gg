package gg.playit.minecraft.logger;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

import java.util.logging.Logger;

/**
 * Centralized messaging system for beautiful console output.
 */
public class MessageManager {
    private static final String PREFIX = "§3§lPlayit§r §8»§r";
    private static MessageManager instance;
    
    private final Logger logger;
    private final ConsoleCommandSender console;
    private LogLevel minLevel = LogLevel.INFO;
    private boolean showBanner = true;
    
    // Track what we've shown to prevent spam
    private boolean claimBoxShown = false;
    private String lastClaimCode = null;

    private static final String SEPARATOR = "§8─────────────────────────────────────────";
    
    private static final String[] BANNER = {
        "",
        "§b    ____  __            _ __     gg",
        "§b   / __ \\/ /___ ___  __(_) /_    ",
        "§b  / /_/ / / __ `/ / / / / __/    ",
        "§b / ____/ / /_/ / /_/ / / /_      ",
        "§b/_/   /_/\\__,_/\\__, /_/\\__/      ",
        "§b              /____/   §7v2.0.0 Revamped  ",
        "§7§lby ThaYTOfficial",
        ""
    };

    private MessageManager(Logger logger) {
        this.logger = logger;
        this.console = Bukkit.getConsoleSender();
    }

    public static void init(Logger logger) {
        instance = new MessageManager(logger);
    }

    public static MessageManager get() {
        if (instance == null) {
            throw new IllegalStateException("MessageManager not initialized!");
        }
        return instance;
    }

    public void setLogLevel(LogLevel level) {
        this.minLevel = level;
    }

    public void setShowBanner(boolean show) {
        this.showBanner = show;
    }
    
    public void resetClaimState() {
        this.claimBoxShown = false;
        this.lastClaimCode = null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Logging methods
    // ─────────────────────────────────────────────────────────────────

    public void debug(String message) {
        if (minLevel == LogLevel.DEBUG) {
            sendConsole("§8[Debug] " + message);
        }
    }

    public void info(String message) {
        if (LogLevel.INFO.shouldLog(minLevel)) {
            sendConsole(PREFIX + " §f" + message);
        }
    }

    public void warn(String message) {
        if (LogLevel.WARN.shouldLog(minLevel)) {
            sendConsole(PREFIX + " §e⚠ " + message);
        }
    }

    public void error(String message) {
        if (LogLevel.ERROR.shouldLog(minLevel)) {
            sendConsole(PREFIX + " §c✖ " + message);
        }
    }

    public void error(String message, Throwable e) {
        if (LogLevel.ERROR.shouldLog(minLevel)) {
            sendConsole(PREFIX + " §c✖ " + message);
            if (minLevel == LogLevel.DEBUG) {
                sendConsole("§8    " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // UI Elements
    // ─────────────────────────────────────────────────────────────────

    public void showStartupBanner() {
        if (!showBanner) return;
        
        for (String line : BANNER) {
            sendConsole(line);
        }
    }

    public void showTunnelActive(String address) {
        sendConsole("");
        sendConsole(SEPARATOR);
        sendConsole("§a  ✓ §fTunnel Active");
        sendConsole("§7    Address: §b" + address);
        sendConsole(SEPARATOR);
        sendConsole("");
    }

    public void showStatusBox(String status, String address) {
        showTunnelActive(address);
    }

    /**
     * Shows the claim setup message - but only once per claim code.
     * Returns true if it was shown, false if already shown.
     */
    public boolean showClaimBox(String claimCode) {
        // Don't show if we already showed this claim code
        if (claimBoxShown && claimCode.equals(lastClaimCode)) {
            return false;
        }
        
        claimBoxShown = true;
        lastClaimCode = claimCode;
        
        String url = "https://playit.gg/mc/" + claimCode;
        
        sendConsole("");
        sendConsole(SEPARATOR);
        sendConsole("§e  ⚡ §fSetup Required");
        sendConsole("§7    Visit to claim your tunnel:");
        sendConsole("§b    " + url);
        sendConsole(SEPARATOR);
        sendConsole("");
        
        return true;
    }

    public void showWarningBox(String title, String message) {
        sendConsole("");
        sendConsole(SEPARATOR);
        sendConsole("§6  ⚠ §f" + title);
        sendConsole("§7    " + message);
        sendConsole(SEPARATOR);
        sendConsole("");
    }
    
    public void showSuccessBox(String title, String... lines) {
        sendConsole("");
        sendConsole(SEPARATOR);
        sendConsole("§a  ✓ §f" + title);
        for (String line : lines) {
            sendConsole("§7    " + line);
        }
        sendConsole(SEPARATOR);
        sendConsole("");
    }

    public void showSimpleMessage(String message) {
        sendConsole(PREFIX + " " + message);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private void sendConsole(String message) {
        console.sendMessage(message);
    }
}
