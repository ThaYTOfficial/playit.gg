<p align="center">
  <img src="https://playit.gg/static/playit-logo.svg" alt="Playit.gg" width="200"/>
</p>

<h1 align="center">Playit.GG Revamped</h1>

<p align="center">
  <strong>A complete overhaul of the Playit.gg Minecraft plugin</strong><br/>
  <em>Make your server public without port forwarding</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-2.0.0-blue?style=flat-square" alt="Version"/>
  <img src="https://img.shields.io/badge/Minecraft-1.13--1.21+-green?style=flat-square" alt="Minecraft"/>
  <img src="https://img.shields.io/badge/Folia-Supported-purple?style=flat-square" alt="Folia"/>
</p>

---

## 🆕 What's New in v2.0

This is a **major update** from v1.5, completely rewritten for better performance and user experience:

- ✨ **New logging system** - Configurable log levels, no more console spam
- 🎨 **Beautiful console UI** - Clean formatted messages instead of raw logs  
- ⚡ **Performance optimized** - Lock-free connection tracking, rate-limited reconnects
- 🔧 **Folia support** - Works with all modern server types
- 🛠️ **Better error handling** - Graceful recovery from API errors
- 📝 **Improved commands** - Cleaner `/playit` help and status

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

## ❤️ Credits

- **[Playit.gg Team](https://playit.gg)** - For the original plugin and the amazing tunneling platform.
- **[itasli](https://github.com/itasli)** - For the "Revamped" v2.0 overhaul and maintenance.

---

<p align="center">
  <b>Playit.GG Revamped</b> - A community-maintained fork with enhanced features
</p>
