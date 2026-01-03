# Votify `Alpha`

**Votify** is a high-performance, customizable voting management plugin for Minecraft servers. Developed by **ImJopeh** at **Mapple Studio**, Votify streamlines the connection between your server and voting websites, ensuring your players are rewarded instantly and your community remains engaged.

> [!IMPORTANT]
> This project is currently in its **Alpha** stage. Features are subject to change, and we actively encourage bug reports and feature suggestions via our issue tracker.

---

## 🚀 Key Features

* **NuVotifier Integration**: Seamlessly listen to incoming votes from any website supported by NuVotifier.
* **Dynamic Leaderboards**: Built-in support for Top Voter leaderboards with full **PlaceholderAPI** integration.
* **Per-Website Rewards**: Incentivize specific voting sites by customizing unique rewards for each one.
* **Monthly Automation**: Hands-free management with automated monthly resets and Top Voter logging.
* **Discord Webhooks**: Keep your community informed with automated Top Voter announcements sent directly to your Discord server.

---

## 🛠 Installation

1. **Prerequisites**: Ensure you have [NuVotifier](https://www.spigotmc.org/resources/nuvotifier.13449/) installed and configured on your server.
2. **Download**: Grab the latest `.jar` from the [Releases](https://github.com/ImJopehhh/votify/releases) page.
3. **Deploy**: Place the file into your server's `/plugins/` directory.
4. **Launch**: Restart your server to generate the configuration files.
5. **Configure**: Edit `config.yml` to set up your rewards and Discord Webhook URL.

## 🧱 Dependencies

To ensure **Votify** functions correctly, please check the following requirements:

### 🔴 Required (Must Install)
* **[NuVotifier](https://www.spigotmc.org/resources/nuvotifier.13449/)**: Required to listen to vote notifications from server listing websites.

### 🟢 Optional (Highly Recommended)
* **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)**: Required if you want to display Top Voter statistics on leaderboards, scoreboards, or holograms.

---

## 📊 Placeholders

Integrate Votify data into your Scoreboards, TAB, or Holograms using **PlaceholderAPI**:

| Placeholder | Description |
| --- | --- |
| `%votify_topvoter_<month>_<number>_votes%` | Displays the vote count of the voter at rank X |
| `%votify_topvoter_<month>_<number>_name%` | Displays the name of the voter at rank X |
| `%votify_player_votes%` | Displays the current player's total votes |

---

## 📋 Configuration Preview

```yaml
# Votify Configuration

# The name of your server, used in some messages.
server-name: My Awesome Server

# Enable or disable debug messages in the console.
debug: false

# Login Delay (in seconds)
# Time to wait after a player joins before giving pending rewards.
# Increase this if you use an Authentication plugin (e.g., AuthMe) to prevent item loss.
login-delay: 5

# Discord Webhook Configuration
discord:
  enabled: false
  webhook-url: https://discord.com/api/webhooks/...
  top-voter-embed:
    title: 🏆 Monthly Top Voters 🏆
    description: Here are the top voters for the month of %month%!
    color: 16776960 # Yellow/Gold
    footer: Votify System

# System Data (Do not edit manually)
data:
  last-month: 1

# Messages
messages:
  prefix: '&8[&bVotify&8] &r'
  vote-received: '%prefix% &aThanks, &e%player%&a, for voting on &e%service%&a!'
  player-not-found: '%prefix% &cPlayer %player% not found.'
  no-permission: '%prefix% &cYou don''t have permission to use this command.'
  reload: '%prefix% &aConfiguration reloaded successfully.'
```

---

## 🤝 Support & Feedback

We are committed to making Votify the best voting solution. If you encounter issues or have suggestions:

* **Discord**: [Join Mapple Studio Discord](https://www.google.com/search?q=https://discord.gg/yourlink)
* **GitHub Issues**: [Open a ticket](https://www.google.com/search?q=https://github.com/your-repo/issues)

---

## 📄 License & Credits

* **Lead Developer**: ImJopeh / Arjuna Jovian
* **Organization**: Mapple Studio
* **Status**: Under active development.
