package com.edouardcourty.dd.velocity;

import com.edouardcourty.dd.common.messaging.Channels;
import com.edouardcourty.dd.velocity.handler.DimensionSwitchHandler;
import com.edouardcourty.dd.velocity.handler.EntityTransferHandler;
import com.edouardcourty.dd.velocity.handler.AdvancementTransferHandler;
import com.edouardcourty.dd.velocity.listener.PlayerSessionListener;
import com.edouardcourty.dd.velocity.store.LastServerStore;
import com.edouardcourty.dd.velocity.util.DebugLogger;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Plugin(id = "distributed-dimensions", name = "DistributedDimensionsProxy", version = "1.0.0")
public class DistributedDimensions {
    @Inject
    private ProxyServer server;

    @Inject
    private org.slf4j.Logger logger;

    @Inject
    @DataDirectory
    private Path dataDirectory;

    private ChannelIdentifier dimSwitchChannel;
    private ChannelIdentifier entityTransferChannel;
    private DimensionSwitchHandler dimensionSwitchHandler;
    private EntityTransferHandler entityTransferHandler;
    private AdvancementTransferHandler advancementTransferHandler;
    private DebugLogger debugLogger;

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        boolean debug = loadDebugConfig();
        Map<String, String> serverNames = loadServerNames();
        debugLogger = new DebugLogger(logger, debug);

        dimSwitchChannel = MinecraftChannelIdentifier.from(Channels.DIM_SWITCH.toString());
        entityTransferChannel = MinecraftChannelIdentifier.from(Channels.ENTITY_TRANSFER.toString());
        server.getChannelRegistrar().register(dimSwitchChannel);
        server.getChannelRegistrar().register(entityTransferChannel);
        server.getChannelRegistrar().register(MinecraftChannelIdentifier.from(Channels.ADVANCEMENT_TRANSFER.toString()));

        advancementTransferHandler = new AdvancementTransferHandler();
        dimensionSwitchHandler = new DimensionSwitchHandler(server, serverNames, advancementTransferHandler);
        entityTransferHandler = new EntityTransferHandler(server, serverNames);

        LastServerStore lastServerStore = new LastServerStore(dataDirectory, logger);
        server.getEventManager().register(this, new PlayerSessionListener(server, lastServerStore,
            entityTransferHandler, advancementTransferHandler));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        debugLogger.debug("PluginMessage received: channel=" + event.getIdentifier() + " source=" + event.getSource().getClass().getSimpleName());
        if (event.getIdentifier().equals(dimSwitchChannel)) {
            dimensionSwitchHandler.handle(event);
        } else if (event.getIdentifier().equals(entityTransferChannel)) {
            entityTransferHandler.handle(event);
        } else if (event.getIdentifier().equals(MinecraftChannelIdentifier.from(Channels.ADVANCEMENT_TRANSFER.toString()))) {
            advancementTransferHandler.handle(event);
        }
    }

    private boolean loadDebugConfig() {
        ensureConfig();
        try {
            String content = Files.readString(dataDirectory.resolve("config.toml"));
            return content.contains("debug = true");
        } catch (IOException e) {
            return false;
        }
    }

    private Map<String, String> loadServerNames() {
        ensureConfig();
        Map<String, String> names = new HashMap<>();
        // Defaults
        names.put("overworld", "overworld");
        names.put("nether", "nether");
        names.put("end", "end");
        try {
            String content = Files.readString(dataDirectory.resolve("config.toml"));
            boolean inServersSection = false;
            for (String line : content.lines().toList()) {
                String trimmed = line.trim();
                if (trimmed.equals("[servers]")) { inServersSection = true; continue; }
                if (trimmed.startsWith("[")) { inServersSection = false; continue; }
                if (inServersSection && trimmed.contains("=")) {
                    String[] parts = trimmed.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim().replace("\"", "");
                    names.put(key, value);
                }
            }
        } catch (IOException e) {
            logger.warn("[DistributedDimensions] Could not load server names from config: " + e.getMessage());
        }
        return names;
    }

    private void ensureConfig() {
        Path configFile = dataDirectory.resolve("config.toml");
        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getResourceAsStream("/config.toml")) {
                if (in != null) {
                    Files.createDirectories(dataDirectory);
                    Files.copy(in, configFile);
                }
            } catch (IOException ignored) {}
        }
    }
}
