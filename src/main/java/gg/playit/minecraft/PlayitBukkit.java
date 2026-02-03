package gg.playit.minecraft;

import gg.playit.api.ApiClient;
import gg.playit.api.ApiError;
import gg.playit.api.models.Notice;
import gg.playit.minecraft.logger.LogLevel;
import gg.playit.minecraft.logger.MessageManager;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

import java.io.IOException;
import java.util.List;

public final class PlayitBukkit extends JavaPlugin implements Listener {
    public static final String CFG_AGENT_SECRET_KEY = "agent-secret";
    public static final String CFG_CONNECTION_TIMEOUT_SECONDS = "mc-timeout-sec";
    public static final String CFG_LOG_LEVEL = "log-level";
    public static final String CFG_SHOW_BANNER = "show-banner";

    final EventLoopGroup eventGroup = new NioEventLoopGroup();

    private final Object managerSync = new Object();
    private volatile PlayitManager playitManager;

    Server server;

    private boolean isGeyserPresent = false;
    private int geyserPort = 19132;

    @Override
    public void onEnable() {
        server = Bukkit.getServer();

        // Initialize config with defaults
        getConfig().addDefault(CFG_AGENT_SECRET_KEY, "");
        getConfig().addDefault(CFG_CONNECTION_TIMEOUT_SECONDS, 30);
        getConfig().addDefault(CFG_LOG_LEVEL, "INFO");
        getConfig().addDefault(CFG_SHOW_BANNER, true);
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        // Initialize MessageManager
        MessageManager.init(getLogger());
        MessageManager msg = MessageManager.get();
        
        // Configure logging
        String logLevelStr = getConfig().getString(CFG_LOG_LEVEL, "INFO");
        msg.setLogLevel(LogLevel.fromString(logLevelStr));
        msg.setShowBanner(getConfig().getBoolean(CFG_SHOW_BANNER, true));

        // Show startup banner
        msg.showStartupBanner();

        // Detect Geyser plugin
        PluginManager pm = Bukkit.getServer().getPluginManager();
        Plugin geyser = pm.getPlugin("Geyser-Spigot");
        if (geyser != null && geyser.isEnabled()) {
            isGeyserPresent = true;
            // Try to read the port from Geyser config
            try {
                File geyserConfig = new File("plugins/Geyser-Spigot/config.yml");
                if (geyserConfig.exists()) {
                    Yaml yaml = new Yaml();
                    try (FileInputStream fis = new FileInputStream(geyserConfig)) {
                        Map<String, Object> config = yaml.load(fis);
                        if (config != null && config.containsKey("bedrock")) {
                            Object bedrockSection = config.get("bedrock");
                            if (bedrockSection instanceof Map) {
                                Object portObj = ((Map<?, ?>) bedrockSection).get("port");
                                if (portObj instanceof Number) {
                                    geyserPort = ((Number) portObj).intValue();
                                } else if (portObj instanceof String) {
                                    try {
                                        geyserPort = Integer.parseInt((String) portObj);
                                    } catch (NumberFormatException ignore) {}
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                msg.debug("Failed to read Geyser config: " + e.getMessage());
            }
            msg.info("Geyser detected on port " + geyserPort);
        }

        var command = getCommand("playit");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        } else {
            msg.error("Failed to register /playit command");
        }

        var secretKey = getConfig().getString(CFG_AGENT_SECRET_KEY);
        resetConnection(secretKey);

        try {
            pm.registerEvents(this, this);
        } catch (Exception e) {
            msg.debug("Failed to register events: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var manager = playitManager;

        if (player.isOp() && manager != null) {
            if (manager.isGuest()) {
                player.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.RED + "Running with guest account - run /playit account guest-login-link to claim");
            } else if (!manager.emailVerified()) {
                player.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.YELLOW + "Please verify your email on playit.gg");
            }

            Notice notice = manager.getNotice();
            if (notice != null) {
                player.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.WHITE + notice.message);
                player.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.AQUA + notice.url);
            }
        }
    }

    public void broadcast(String message) {
        MessageManager.get().showSimpleMessage(message);
    }

    public void showTunnelAddress(String address) {
        MessageManager.get().showStatusBox("Connected", address);
    }

    public void showClaimUrl(String claimCode) {
        MessageManager.get().showClaimBox(claimCode);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You need to be an operator to use this command.");
            return true;
        }

        MessageManager msg = MessageManager.get();

        if (args.length > 0 && args[0].equals("agent")) {
            if (args.length > 1 && args[1].equals("status")) {
                var manager = playitManager;

                if (manager == null) {
                    String currentSecret = getConfig().getString(CFG_AGENT_SECRET_KEY);
                    if (currentSecret == null || currentSecret.length() == 0) {
                        sender.sendMessage(ChatColor.YELLOW + "Playit is not configured. Run /playit to see setup instructions.");
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.RED + "Offline");
                    }
                } else {
                    String status = switch (manager.state()) {
                        case PlayitKeysSetup.STATE_INIT -> "Initializing...";
                        case PlayitKeysSetup.STATE_MISSING_SECRET -> "Waiting for claim";
                        case PlayitKeysSetup.STATE_CHECKING_SECRET -> "Verifying secret";
                        case PlayitKeysSetup.STATE_CREATING_TUNNEL -> "Creating tunnel";
                        case PlayitKeysSetup.STATE_ERROR -> "Error";

                        case PlayitManager.STATE_CONNECTING -> "Connecting...";
                        case PlayitManager.STATE_ONLINE -> "Connected";
                        case PlayitManager.STATE_OFFLINE -> "Offline";
                        case PlayitManager.STATE_ERROR_WAITING -> "Reconnecting...";
                        case PlayitManager.STATE_INVALID_AUTH -> "Invalid secret key";

                        case PlayitManager.STATE_SHUTDOWN -> "Shutdown";
                        default -> "Unknown";
                    };

                    ChatColor statusColor = switch (manager.state()) {
                        case PlayitManager.STATE_ONLINE -> ChatColor.GREEN;
                        case PlayitManager.STATE_CONNECTING, PlayitManager.STATE_ERROR_WAITING -> ChatColor.YELLOW;
                        default -> ChatColor.RED;
                    };

                    sender.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.WHITE + "Status: " + statusColor + status);
                    
                    var address = manager.getAddress();
                    if (address != null) {
                        sender.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.WHITE + "Address: " + ChatColor.AQUA + address);
                    }
                }

                return true;
            }

            if (args.length > 1 && args[1].equals("restart")) {
                resetConnection(null);
                sender.sendMessage(ChatColor.GREEN + "Restarting connection...");
                return true;
            }

            if (args.length > 1 && args[1].equals("reset")) {
                getConfig().set(CFG_AGENT_SECRET_KEY, "");
                saveConfig();
                resetConnection(null);
                sender.sendMessage(ChatColor.GREEN + "Configuration reset. Visit the claim URL to reconfigure.");
                return true;
            }

            if (args.length > 1 && args[1].equals("shutdown")) {
                synchronized (managerSync) {
                    if (playitManager != null) {
                        playitManager.shutdown();
                        playitManager = null;
                    }
                }
                sender.sendMessage(ChatColor.YELLOW + "Playit connection shutdown.");
                return true;
            }

            if (args.length > 2 && args[1].equals("set-secret")) {
                String secretKey = args[2];
                if (secretKey.length() < 32) {
                    sender.sendMessage(ChatColor.RED + "Invalid secret key.");
                    return true;
                }
                resetConnection(secretKey);
                sender.sendMessage(ChatColor.GREEN + "Secret key updated, reconnecting...");
                return true;
            }

            return false;
        }

        if (args.length > 0 && args[0].equals("prop")) {
            if (args.length > 1 && args[1].equals("get")) {
                int current = 30;
                var p = playitManager;
                if (p != null) {
                    current = playitManager.connectionTimeoutSeconds;
                }

                int settings = getConfig().getInt(CFG_CONNECTION_TIMEOUT_SECONDS, 30);
                sender.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.WHITE + CFG_CONNECTION_TIMEOUT_SECONDS + ": " + ChatColor.AQUA + settings + ChatColor.GRAY + " (active: " + current + ")");
                
                String logLevel = getConfig().getString(CFG_LOG_LEVEL, "INFO");
                sender.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.WHITE + CFG_LOG_LEVEL + ": " + ChatColor.AQUA + logLevel);
                return true;
            }

            if (args.length > 3 && args[1].equals("set")) {
                if (args[2].equals(CFG_CONNECTION_TIMEOUT_SECONDS)) {
                    try {
                        var value = Integer.parseInt(args[3]);
                        getConfig().set(CFG_CONNECTION_TIMEOUT_SECONDS, value);
                        saveConfig();
                        sender.sendMessage(ChatColor.GREEN + "Set " + CFG_CONNECTION_TIMEOUT_SECONDS + " to " + value + ". Run /playit agent restart to apply.");
                    } catch (Exception ignore) {
                        sender.sendMessage(ChatColor.RED + "Invalid number.");
                    }
                    return true;
                }
                
                if (args[2].equals(CFG_LOG_LEVEL)) {
                    LogLevel level = LogLevel.fromString(args[3]);
                    getConfig().set(CFG_LOG_LEVEL, level.name());
                    saveConfig();
                    msg.setLogLevel(level);
                    sender.sendMessage(ChatColor.GREEN + "Log level set to " + level.name());
                    return true;
                }
            }

            return false;
        }

        if (args.length > 0 && args[0].equals("tunnel")) {
            if (args.length > 1 && args[1].equals("get-address")) {
                var m = playitManager;
                if (m != null) {
                    var a = m.getAddress();
                    if (a != null) {
                        sender.sendMessage(ChatColor.GOLD + "[Playit] " + ChatColor.WHITE + "Tunnel: " + ChatColor.GREEN + a);
                        return true;
                    }
                }
                sender.sendMessage(ChatColor.YELLOW + "Tunnel is still being set up...");
                return true;
            }
        }

        if (args.length > 0 && args[0].equals("account")) {
            if (args.length > 1 && args[1].equals("guest-login-link")) {
                var secret = getConfig().getString(CFG_AGENT_SECRET_KEY);
                if (secret == null || secret.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No secret key configured.");
                    return true;
                }

                sender.sendMessage(ChatColor.YELLOW + "Generating login link...");

                new Thread(() -> {
                    try {
                        var api = new ApiClient(secret);
                        var session = api.createGuestWebSessionKey();
                        var url = "https://playit.gg/login/guest-account/" + session;
                        
                        Bukkit.getScheduler().runTask(this, () -> {
                            sender.sendMessage(ChatColor.GREEN + "Login URL generated!");
                            sender.sendMessage(ChatColor.AQUA + url);
                        });
                    } catch (ApiError e) {
                        Bukkit.getScheduler().runTask(this, () -> 
                            sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage())
                        );
                    } catch (IOException e) {
                        Bukkit.getScheduler().runTask(this, () -> 
                            sender.sendMessage(ChatColor.RED + "Failed to generate link.")
                        );
                    }
                }).start();

                return true;
            }
        }

        // Default: show help
        sender.sendMessage(ChatColor.GOLD + "=== Playit.gg Commands ===");
        sender.sendMessage(ChatColor.WHITE + "/playit agent status" + ChatColor.GRAY + " - View connection status");
        sender.sendMessage(ChatColor.WHITE + "/playit agent restart" + ChatColor.GRAY + " - Restart connection");
        sender.sendMessage(ChatColor.WHITE + "/playit agent reset" + ChatColor.GRAY + " - Reset configuration");
        sender.sendMessage(ChatColor.WHITE + "/playit tunnel get-address" + ChatColor.GRAY + " - Get tunnel address");
        sender.sendMessage(ChatColor.WHITE + "/playit account guest-login-link" + ChatColor.GRAY + " - Get account claim URL");
        return true;
    }

    private void resetConnection(String secretKey) {
        if (secretKey != null) {
            getConfig().set(CFG_AGENT_SECRET_KEY, secretKey);
            saveConfig();
        }

        synchronized (managerSync) {
            if (playitManager != null) {
                playitManager.shutdown();
            }

            playitManager = new PlayitManager(this, isGeyserPresent, geyserPort);
            try {
                int waitSeconds = getConfig().getInt(CFG_CONNECTION_TIMEOUT_SECONDS);
                if (waitSeconds != 0) {
                    playitManager.connectionTimeoutSeconds = waitSeconds;
                }
            } catch (Exception ignore) {
            }

            new Thread(playitManager).start();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) {
            return null;
        }

        int argCount = args.length;
        if (argCount != 0 && args[argCount - 1].length() == 0) {
            argCount -= 1;
        }

        if (argCount == 0) {
            return List.of("agent", "tunnel", "prop", "account");
        }

        if (args[0].equals("account")) {
            if (argCount == 1) {
                return List.of("guest-login-link");
            }
        }

        if (args[0].equals("agent")) {
            if (argCount == 1) {
                return List.of("set-secret", "shutdown", "status", "restart", "reset");
            }
        }

        if (args[0].equals("prop")) {
            if (argCount == 1) {
                return List.of("set", "get");
            }

            if (argCount == 2) {
                if (!args[1].equals("set") && !args[1].equals("get")) {
                    return null;
                }
                return List.of(CFG_CONNECTION_TIMEOUT_SECONDS, CFG_LOG_LEVEL);
            }
            
            if (argCount == 3 && args[1].equals("set") && args[2].equals(CFG_LOG_LEVEL)) {
                return List.of("DEBUG", "INFO", "WARN", "ERROR");
            }
        }

        if (args[0].equals("tunnel")) {
            if (argCount == 1) {
                return List.of("get-address");
            }
        }

        return null;
    }

    @Override
    public void onDisable() {
        if (playitManager != null) {
            playitManager.shutdown();
            playitManager = null;
        }
        MessageManager.get().info("Plugin disabled");
    }
}
