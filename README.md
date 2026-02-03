# Playit.gg Revamp

This is a recoded fork of the Minecraft Java Plugin for https://playit.gg. Download the official version [here](https://github.com/playit-cloud/playit-minecraft-plugin/releases/latest/download/playit-minecraft-plugin.jar).

Not sure how to use the playit plugin? Watch their [YouTube video](https://youtu.be/QQYRdgBL-4o).

## Compatibility

❌ = not planned
🚧 = planned
✔️ = implemented

### Server Types
| Server Type  | Playit works | Real IP works | Plugin Download                                                                                                                         |
|--------------| ------------ |---------------|-----------------------------------------------------------------------------------------------------------------------------------|
| [Spigot 1.21](https://getbukkit.org/download/spigot)  | ✔️ | ✔️ | [0.1.4](https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.1.4/playit-minecraft-plugin.jar) |
| [Spigot 1.16.5](https://getbukkit.org/download/spigot) | ✔️ | ✔️ | [0.1.4-mc1.16](https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.1.4/playit-minecraft-plugin-1.16.jar) |
| [Paper 1.19](https://papermc.io/)   | ✔️ | ✔️ | [0.1.4](https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.1.4/playit-minecraft-plugin.jar) |
| [Paper 1.8 - 1.18](https://papermc.io/legacy) | ✔️ | ❌ | [0.1.4](https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.1.4/playit-minecraft-plugin.jar) & [0.1.4-mc1.16](https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.1.4/playit-minecraft-plugin-1.16.jar)|
| [Magma 1.18](https://magmafoundation.org/) | ✔️ | ❌ | [0.1.4](https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.1.4/playit-minecraft-plugin.jar)
| [BungeeCord](https://www.spigotmc.org/wiki/bungeecord/) | ✔️ |   |

---

## ✨ Features

- 🚀 **Zero configuration** - Just install and follow the claim link
- 🌐 **No port forwarding** - Works behind any router or firewall
- 🎮 **Geyser support** - Auto-detects and creates Bedrock tunnels
- 🔒 **Real IP support** - See actual player IPs in logs and bans
- ⚡ **All server types** - Spigot, Paper, Purpur, Folia, and more

## 📥 Installation

1. **Download** the latest `playit-minecraft-plugin.jar`
2. **Drop** it into your server's `plugins/` folder
3. **Start** your server
4. **Visit** the claim link shown in console to activate your tunnel

That's it! Your server address will be displayed in the console.

## ⚙️ Commands

| Command | Description |
|---------|-------------|
| `/playit` | Show help and tunnel status |
| `/playit agent status` | View connection status |
| `/playit agent restart` | Restart the tunnel |
| `/playit agent reset` | Reset and reclaim tunnel |
| `/playit tunnel get-address` | Get your server address |
| `/playit account guest-login-link` | Get link to claim guest account |

## 🔧 Configuration

```yaml
# plugins/playit-gg/config.yml

# Your secret key (auto-generated, don't share!)
agent-secret: ""

# Connection timeout in seconds
mc-timeout-sec: 30

# Log level: DEBUG, INFO, WARN, ERROR
log-level: INFO

# Show ASCII banner on startup
show-banner: true
```

## 📋 Compatibility

| Server | Status |
|--------|--------|
| Spigot 1.13+ | ✅ |
| Paper 1.13+ | ✅ |
| Purpur | ✅ |
| Folia | ✅ |
| Magma | ✅ |

## ❓ FAQ

<details>
<summary><b>How do I find my server address?</b></summary>

Run `/playit tunnel get-address` or check your console after startup.
</details>

<details>
<summary><b>Can I use a custom domain?</b></summary>

Yes! Create a free account at [playit.gg](https://playit.gg) and configure a custom domain in the dashboard.
</details>

<details>
<summary><b>Is it free?</b></summary>

Yes! Playit.gg is free for personal use.
</details>

## 🔗 Links

- 🌐 [Playit.gg Website](https://playit.gg)
- 📖 [Documentation](https://playit.gg/docs)
- 💬 [Discord Community](https://discord.gg/playit)

---

<p align="center">
  <b>Playit.GG Revamped</b> - A community-maintained fork with enhanced features
</p>
