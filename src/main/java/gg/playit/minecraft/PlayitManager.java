package gg.playit.minecraft;

import gg.playit.api.ApiClient;
import gg.playit.api.models.Notice;
import gg.playit.control.PlayitControlChannel;
import gg.playit.messages.ControlFeedReader;
import gg.playit.minecraft.logger.MessageManager;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayitManager implements Runnable {
    private final AtomicInteger state = new AtomicInteger(STATE_INIT);
    private final PlayitConnectionTracker tracker = new PlayitConnectionTracker();

    private final PlayitBukkit plugin;
    private final boolean isGeyserPresent;
    private final int geyserPort;

    public PlayitManager(PlayitBukkit plugin, boolean isGeyserPresent, int geyserPort) {
        this.plugin = plugin;
        this.isGeyserPresent = isGeyserPresent;
        this.geyserPort = geyserPort;

        var secret = plugin.getConfig().getString(PlayitBukkit.CFG_AGENT_SECRET_KEY);
        if (secret != null && secret.length() < 32) {
            secret = null;
        }

        setup = new PlayitKeysSetup(secret, state, isGeyserPresent, geyserPort);
    }

    private final PlayitKeysSetup setup;
    private volatile PlayitKeysSetup.PlayitKeys keys;

    public boolean isGuest() {
        return keys != null && keys.isGuest;
    }

    public boolean emailVerified() {
        return keys == null || keys.isEmailVerified;
    }

    public String getAddress() {
        if (keys == null) {
            return null;
        }
        return keys.tunnelAddress;
    }

    public Notice getNotice() {
        var k = keys;
        if (k == null) {
            return null;
        }
        return k.notice;
    }

    public volatile int connectionTimeoutSeconds = 30;
    public static final int STATE_INIT = -1;
    public static final int STATE_OFFLINE = 10;
    public static final int STATE_CONNECTING = 11;
    public static final int STATE_ONLINE = 12;
    public static final int STATE_ERROR_WAITING = 13;
    public static final int STATE_SHUTDOWN = 0;
    public static final int STATE_INVALID_AUTH = 15;

    public void shutdown() {
        state.compareAndSet(STATE_ONLINE, STATE_SHUTDOWN);
    }

    public int state() {
        return state.get();
    }

    @Override
    public void run() {
        MessageManager msg = MessageManager.get();
        
        /* make sure we don't run two instances */
        if (!state.compareAndSet(STATE_INIT, PlayitKeysSetup.STATE_INIT)) {
            return;
        }

        while (state.get() != STATE_SHUTDOWN) {
            try {
                keys = setup.progress();

                if (keys != null) {
                    msg.debug("Keys and tunnel setup complete");
                    break;
                }
            } catch (IOException e) {
                msg.error("Setup error", e);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignore) {
                }

                continue;
            }

            if (state.get() == PlayitKeysSetup.STATE_MISSING_SECRET) {
                var code = setup.getClaimCode();
                if (code != null) {
                    // Show claim box in console
                    plugin.showClaimUrl(code);
                    
                    // Notify online ops
                    for (var player : plugin.server.getOnlinePlayers()) {
                        if (player.isOp()) {
                            player.sendMessage("§6[Playit] §eVisit §bhttps://playit.gg/mc/" + code + " §eto setup");
                        }
                    }
                }

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignore) {
                }
            }
        }

        if (keys == null) {
            msg.debug("Shutdown before tunnel connection started");
            return;
        }

        plugin.getConfig().set(PlayitBukkit.CFG_AGENT_SECRET_KEY, keys.secretKey);
        plugin.saveConfig();

        if (keys.isGuest) {
            msg.showWarningBox("Guest Account", "Running with a guest account. Use /playit account guest-login-link to claim.");

            var api = new ApiClient(keys.secretKey);

            try {
                var key = api.createGuestWebSessionKey();
                var url = "https://playit.gg/login/guest-account/" + key;
                msg.showSuccessBox("Guest Account Login", "Claim your account:", url);

                if (state.get() == STATE_SHUTDOWN) {
                    return;
                }

                for (var player : plugin.server.getOnlinePlayers()) {
                    if (player.isOp()) {
                        player.sendMessage("§6[Playit] §eClaim your account: §b" + url);
                    }
                }
            } catch (IOException e) {
                msg.error("Failed to generate web session key", e);
            }
        } else if (!keys.isEmailVerified) {
            msg.warn("Email not verified on playit.gg account");
        }

        // Show the tunnel address
        plugin.showTunnelAddress(keys.tunnelAddress);

        if (state.get() == STATE_SHUTDOWN) {
            return;
        }

        state.set(STATE_CONNECTING);

        int reconnectAttempts = 0;
        long lastReconnect = 0;

        while (state.get() == STATE_CONNECTING) {
            try (PlayitControlChannel channel = PlayitControlChannel.setup(keys.secretKey)) {
                state.compareAndSet(STATE_CONNECTING, STATE_ONLINE);
                reconnectAttempts = 0;
                msg.showSimpleMessage("§aTunnel connection established");

                while (state.get() == STATE_ONLINE) {
                    var messageOpt = channel.update();
                    if (messageOpt.isPresent()) {
                        var feedMessage = messageOpt.get();

                        if (feedMessage instanceof ControlFeedReader.NewClient newClient) {
                            msg.debug("New client connection from " + newClient.peerAddr);

                            var key = newClient.peerAddr + "-" + newClient.connectAddr;
                            if (tracker.addConnection(key)) {
                                msg.debug("Starting TCP tunnel for client");

                                new PlayitTcpTunnel(
                                        new InetSocketAddress(InetAddress.getByAddress(newClient.peerAddr.ipBytes), Short.toUnsignedInt(newClient.peerAddr.portNumber)),
                                        plugin.eventGroup,
                                        tracker,
                                        key,
                                        new InetSocketAddress(Bukkit.getIp(), Bukkit.getPort()),
                                        new InetSocketAddress(InetAddress.getByAddress(newClient.claimAddress.ipBytes), Short.toUnsignedInt(newClient.claimAddress.portNumber)),
                                        newClient.claimToken,
                                        plugin.server,
                                        connectionTimeoutSeconds
                                ).start();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                state.compareAndSet(STATE_ONLINE, STATE_ERROR_WAITING);
                
                if (e.getMessage().contains("invalid authentication")) {
                    msg.error("Invalid secret key - please reconfigure");
                    state.set(STATE_INVALID_AUTH);
                    break;
                }

                // Rate limit reconnection attempts
                reconnectAttempts++;
                long now = System.currentTimeMillis();
                long timeSinceLastReconnect = now - lastReconnect;
                
                int delay = Math.min(5000 * reconnectAttempts, 60000); // Max 1 minute
                if (timeSinceLastReconnect < delay) {
                    delay = (int)(delay - timeSinceLastReconnect);
                }
                
                if (reconnectAttempts <= 3) {
                    msg.debug("Connection lost, reconnecting in " + (delay/1000) + "s...");
                } else if (reconnectAttempts == 4) {
                    msg.warn("Connection unstable, will keep trying...");
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignore) {
                }
                
                lastReconnect = System.currentTimeMillis();
            } finally {
                if (state.compareAndSet(STATE_SHUTDOWN, STATE_OFFLINE)) {
                    msg.debug("Control channel shutdown");
                } else if (state.compareAndSet(STATE_ERROR_WAITING, STATE_CONNECTING)) {
                    msg.debug("Attempting reconnection...");
                } else if (state.compareAndSet(STATE_ONLINE, STATE_CONNECTING)) {
                    msg.debug("Unexpected disconnect, reconnecting...");
                }
            }
        }
    }
}
