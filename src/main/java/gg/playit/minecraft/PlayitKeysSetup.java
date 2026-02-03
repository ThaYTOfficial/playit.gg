package gg.playit.minecraft;

import gg.playit.api.ApiClient;
import gg.playit.api.ApiError;
import gg.playit.api.actions.CreateTunnel;
import gg.playit.api.models.AccountTunnel;
import gg.playit.api.models.Notice;
import gg.playit.api.models.PortType;
import gg.playit.api.models.TunnelType;
import gg.playit.minecraft.logger.MessageManager;
import gg.playit.minecraft.utils.Hex;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayitKeysSetup {
    public final AtomicInteger state;
    public static final int STATE_INIT = 1;
    public static final int STATE_MISSING_SECRET = 2;
    public static final int STATE_CHECKING_SECRET = 3;
    public static final int STATE_CREATING_TUNNEL = 4;
    public static final int STATE_ERROR = 5;
    public static final int STATE_SHUTDOWN = 0;
    private final ApiClient openClient = new ApiClient(null);
    private final boolean isGeyserPresent;
    private final int geyserPort;

    public PlayitKeysSetup(String secretKey, AtomicInteger state, boolean isGeyserPresent, int geyserPort) {
        keys.secretKey = secretKey;
        this.state = state;
        this.isGeyserPresent = isGeyserPresent;
        this.geyserPort = geyserPort;
    }

    private final PlayitKeys keys = new PlayitKeys();
    private String claimCode;

    public int getState() {
        return state.get();
    }

    public void shutdown() {
        this.state.set(STATE_SHUTDOWN);
    }

    public String getClaimCode() {
        return claimCode;
    }

    public PlayitKeys progress() throws IOException {
        MessageManager msg = MessageManager.get();
        
        switch (state.get()) {
            case STATE_INIT -> {
                if (keys.secretKey == null) {
                    state.compareAndSet(STATE_INIT, STATE_MISSING_SECRET);
                    return null;
                }

                state.compareAndSet(STATE_INIT, STATE_CHECKING_SECRET);
                msg.debug("Secret key found, verifying...");
                return null;
            }
            case STATE_MISSING_SECRET -> {
                if (claimCode == null) {
                    byte[] array = new byte[8];
                    new Random().nextBytes(array);
                    claimCode = Hex.encodeHexString(array);
                    msg.debug("Generated claim code: " + claimCode);
                }

                msg.debug("Attempting to exchange claim code...");
                keys.secretKey = openClient.exchangeClaimForSecret(claimCode);

                if (keys.secretKey == null) {
                    // Claim not yet completed - this is expected, handled elsewhere
                } else {
                    msg.debug("Claim successful, verifying secret...");
                    state.compareAndSet(STATE_MISSING_SECRET, STATE_CHECKING_SECRET);
                }

                return null;
            }
            case STATE_CHECKING_SECRET -> {
                msg.debug("Checking secret validity...");

                var api = new ApiClient(keys.secretKey);
                try {
                    var status = api.getStatus();

                    keys.isGuest = status.isGuest;
                    keys.isEmailVerified = status.emailVerified;
                    keys.agentId = status.agentId;
                    keys.notice = status.notice;

                    msg.debug("Secret verified successfully");
                    state.compareAndSet(STATE_CHECKING_SECRET, STATE_CREATING_TUNNEL);
                    return null;
                } catch (ApiError e) {
                    if (e.statusCode == 401 || e.statusCode == 400) {
                        if (claimCode == null) {
                            msg.debug("Secret invalid, resetting...");
                            state.compareAndSet(STATE_CHECKING_SECRET, STATE_MISSING_SECRET);
                        } else {
                            msg.error("Secret failed verification after claim");
                            state.compareAndSet(STATE_CHECKING_SECRET, STATE_ERROR);
                        }

                        return null;
                    }

                    throw e;
                }
            }
            case STATE_CREATING_TUNNEL -> {
                var api = new ApiClient(keys.secretKey);

                var tunnels = api.listTunnels();
                keys.tunnelAddress = null;

                boolean haveJava = false;
                boolean haveBedrock = !isGeyserPresent;

                for (AccountTunnel tunnel : tunnels.tunnels) {
                    if (tunnel.tunnelType == TunnelType.MinecraftJava) {
                        keys.tunnelAddress = tunnel.displayAddress;
                        haveJava = true;
                        msg.debug("Found Java tunnel: " + keys.tunnelAddress);
                    }
                    if (isGeyserPresent && tunnel.tunnelType == TunnelType.MinecraftBedrock) {
                        haveBedrock = true;
                        msg.debug("Found Bedrock tunnel");
                    }
                }

                // Always create Java tunnel if not found
                if (!haveJava) {
                    msg.debug("Creating Minecraft Java tunnel...");

                    var create = new CreateTunnel();
                    create.localIp = "127.0.0.1";
                    create.portCount = 1;
                    create.portType = PortType.TCP;
                    create.tunnelType = TunnelType.MinecraftJava;
                    create.agentId = keys.agentId;

                    try {
                        api.createTunnel(create);
                    } catch (ApiError e) {
                        // "tunnel already exists" is expected, not an error
                        if (e.statusCode == 400 && e.getMessage() != null && 
                            e.getMessage().contains("tunnel already exists")) {
                            msg.debug("Tunnel already exists, checking again...");
                        } else {
                            throw e;
                        }
                    }
                    return null; // Wait for tunnel to appear
                }

                // If Geyser is present, ensure a Bedrock UDP tunnel exists
                if (isGeyserPresent && !haveBedrock) {
                    msg.debug("Creating Minecraft Bedrock tunnel on port " + geyserPort + "...");
                    var create = new CreateTunnel();
                    create.localIp = "127.0.0.1";
                    create.localPort = geyserPort;
                    create.portCount = 1;
                    create.portType = PortType.UDP;
                    create.tunnelType = TunnelType.MinecraftBedrock;
                    create.agentId = keys.agentId;
                    
                    try {
                        api.createTunnel(create);
                    } catch (ApiError e) {
                        // "tunnel already exists" is expected, not an error
                        if (e.statusCode == 400 && e.getMessage() != null && 
                            e.getMessage().contains("tunnel already exists")) {
                            msg.debug("Bedrock tunnel already exists, checking again...");
                        } else {
                            throw e;
                        }
                    }
                    return null; // Wait for tunnel to appear
                }

                // If we have both required tunnels, setup is done
                if (haveJava && haveBedrock) {
                    return keys;
                }

                return null;
            }
            default -> {
                return null;
            }
        }
    }

    public static class PlayitKeys {
        public String secretKey;
        public String agentId;
        public String tunnelAddress;
        public boolean isGuest;
        public boolean isEmailVerified;
        public Notice notice;
    }
}
