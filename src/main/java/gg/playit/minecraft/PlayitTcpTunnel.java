package gg.playit.minecraft;

import gg.playit.minecraft.logger.MessageManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.bukkit.Server;

import java.net.InetSocketAddress;

public class PlayitTcpTunnel {
    private final InetSocketAddress trueIp;
    private final EventLoopGroup group;
    private final String connectionKey;
    private final PlayitConnectionTracker tracker;
    private final InetSocketAddress minecraftServerAddress;
    private final InetSocketAddress tunnelClaimAddress;
    private final byte[] tunnelClaimToken;
    private final Server server;

    private final int connectionTimeoutSeconds;

    public PlayitTcpTunnel(
            InetSocketAddress trueIp,
            EventLoopGroup group,
            PlayitConnectionTracker tracker,
            String connectionKey,
            InetSocketAddress minecraftServerAddress,
            InetSocketAddress tunnelClaimAddress,
            byte[] tunnelClaimToken,
            Server server,
            int connectionTimeoutSeconds
    ) {
        this.trueIp = trueIp;
        this.group = group;
        this.tracker = tracker;
        this.connectionKey = connectionKey;
        this.minecraftServerAddress = minecraftServerAddress;
        this.tunnelClaimAddress = tunnelClaimAddress;
        this.tunnelClaimToken = tunnelClaimToken;
        this.server = server;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    private Channel minecraftChannel;
    private Channel tunnelChannel;

    public void start() {
        MessageManager msg = MessageManager.get();
        
        Bootstrap clientBootstrap = new Bootstrap();
        clientBootstrap.group(group);
        clientBootstrap.channel(NioSocketChannel.class);
        clientBootstrap.remoteAddress(this.tunnelClaimAddress);

        clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(SocketChannel socketChannel) {
                tunnelChannel = socketChannel;
                socketChannel.pipeline().addLast(new TunnelConnectionHandler());
            }
        });

        msg.debug("Connecting to tunnel claim: " + tunnelClaimAddress);
        clientBootstrap.connect().addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                msg.debug("Failed to connect to tunnel claim");
                disconnected();
                return;
            }

            msg.debug("Sending claim token");

            future.channel().writeAndFlush(Unpooled.wrappedBuffer(tunnelClaimToken)).addListener(f -> {
                if (!f.isSuccess()) {
                    msg.debug("Failed to send claim token");
                }
            });
        });
    }

    private void disconnected() {
        this.tracker.removeConnection(connectionKey);
    }

    @ChannelHandler.Sharable
    private class TunnelConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {
        TunnelConnectionHandler() {
            super(false);
        }

        private int confirmBytesRemaining = 8;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
            MessageManager msg = MessageManager.get();
            
            if (confirmBytesRemaining > 0) {
                if (byteBuf.readableBytes() < confirmBytesRemaining) {
                    confirmBytesRemaining -= byteBuf.readableBytes();
                    byteBuf.readBytes(byteBuf.readableBytes());
                    byteBuf.release();
                    ctx.read();
                    return;
                }

                byteBuf.readBytes(confirmBytesRemaining);
                confirmBytesRemaining = 0;

                msg.debug("Tunnel connection established");

                if (addChannelToMinecraftServer()) {
                    msg.debug("Using direct channel injection");
                    return;
                }

                var minecraftClient = new Bootstrap();
                minecraftClient.group(group);
                minecraftClient.option(ChannelOption.TCP_NODELAY, true);
                minecraftClient.channel(NioSocketChannel.class);
                minecraftClient.remoteAddress(minecraftServerAddress);

                minecraftClient.handler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel socketChannel) {
                        minecraftChannel = socketChannel;
                        socketChannel.pipeline().addLast(new MinecraftConnectionHandler());
                    }
                });

                msg.debug("Connecting to local MC server");
                minecraftClient.connect().addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        msg.debug("Failed to connect to local MC server");
                        ctx.disconnect();
                        disconnected();
                        return;
                    }

                    msg.debug("Connected to local MC server");

                    if (byteBuf.readableBytes() == 0) {
                        byteBuf.release();
                        ctx.read();
                    } else {
                        future.channel().writeAndFlush(byteBuf).addListener(f -> {
                            if (!f.isSuccess()) {
                                msg.debug("Failed to forward data to MC server");
                                future.channel().disconnect();
                                ctx.disconnect();
                                disconnected();
                                return;
                            }

                            ctx.read();
                        });
                    }
                });

                return;
            }

            /* proxy data */
            minecraftChannel.writeAndFlush(byteBuf).addListener(f -> {
                if (!f.isSuccess()) {
                    msg.debug("Data forwarding failed");
                    minecraftChannel.disconnect();
                    tunnelChannel.disconnect();
                    disconnected();
                    return;
                }

                ctx.read();
            });
        }

        private boolean addChannelToMinecraftServer() {
            MessageManager msg = MessageManager.get();
            ReflectionHelper reflect = new ReflectionHelper();

            Object minecraftServer = reflect.getMinecraftServer(server);
            if (minecraftServer == null) {
                msg.debug("Reflection: MC server not found");
                return false;
            }

            Object serverConnection = reflect.serverConnectionFromMCServer(minecraftServer);
            if (serverConnection == null) {
                msg.debug("Reflection: ServerConnection not found");
                return false;
            }

            Object legacyPingHandler = reflect.newLegacyPingHandler(serverConnection);
            if (legacyPingHandler == null) {
                msg.debug("Reflection: LegacyPingHandler unavailable");
                return false;
            }

            Object packetSplitter = reflect.newPacketSplitter();
            if (packetSplitter == null) {
                msg.debug("Reflection: PacketSplitter unavailable");
                return false;
            }

            Object packetDecoder = reflect.newServerBoundPacketDecoder();
            if (packetDecoder == null) {
                msg.debug("Reflection: PacketDecoder unavailable");
                return false;
            }

            Object packetPrepender = reflect.newPacketPrepender();
            if (packetPrepender == null) {
                msg.debug("Reflection: PacketPrepender unavailable");
                return false;
            }

            Object packetEncoder = reflect.newClientBoundPacketEncoder();
            if (packetEncoder == null) {
                msg.debug("Reflection: PacketEncoder unavailable");
                return false;
            }

            Integer rateLimitNullable = reflect.getRateLimitFromMCServer(minecraftServer);
            if (rateLimitNullable == null) {
                rateLimitNullable = 0;
            }

            int rateLimit = rateLimitNullable;

            Object networkManager;
            if (rateLimit > 0) {
                networkManager = reflect.newNetworkManagerServer(rateLimit);
            } else {
                networkManager = reflect.newServerNetworkManager();
            }

            if (networkManager == null) {
                msg.debug("Reflection: NetworkManager unavailable");
                return false;
            }

            Object handshakeListener = reflect.newHandshakeListener(minecraftServer, networkManager);
            if (handshakeListener == null) {
                msg.debug("Reflection: HandshakeListener unavailable");
                return false;
            }

            if (!reflect.networkManagerSetListener(networkManager, handshakeListener)) {
                msg.debug("Reflection: Failed to set listener");
                return false;
            }

            if (!reflect.setRemoteAddress(tunnelChannel, trueIp)) {
                msg.debug("Could not set remote address for real IP");
            }

            var channel = tunnelChannel.pipeline().removeLast();
            tunnelChannel.pipeline()
                    .addLast("timeout", new ReadTimeoutHandler(connectionTimeoutSeconds))
                    .addLast("legacy_query", (ChannelHandler) legacyPingHandler)
                    .addLast("splitter", (ChannelHandler) packetSplitter)
                    .addLast("decoder", (ChannelHandler) packetDecoder)
                    .addLast("prepender", (ChannelHandler) packetPrepender)
                    .addLast("encoder", (ChannelHandler) packetEncoder)
                    .addLast("packet_handler", (ChannelHandler) networkManager);

            if (!reflect.addToServerConnections(serverConnection, networkManager)) {
                msg.debug("Reflection: Failed to add to connections");

                tunnelChannel.pipeline().remove("timeout");
                tunnelChannel.pipeline().remove("legacy_query");
                tunnelChannel.pipeline().remove("splitter");
                tunnelChannel.pipeline().remove("decoder");
                tunnelChannel.pipeline().remove("prepender");
                tunnelChannel.pipeline().remove("encoder");
                tunnelChannel.pipeline().remove("packet_handler");

                tunnelChannel.pipeline().addLast(channel);

                return false;
            }

            tunnelChannel.pipeline().fireChannelActive();
            return true;
        }
    }

    private class MinecraftConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {
        MinecraftConnectionHandler() {
            super(false);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            tunnelChannel.writeAndFlush(msg).addListener(f -> {
                if (!f.isSuccess()) {
                    MessageManager.get().debug("Tunnel write failed");
                    minecraftChannel.disconnect();
                    tunnelChannel.disconnect();
                    return;
                }

                ctx.read();
            });
        }
    }
}
