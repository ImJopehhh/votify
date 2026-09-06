# 🗳️ Votify

> **Modern, High-Performance Voting Management Plugin for Minecraft Servers**  
> Built with robust SQLite persistence, thread-safe asynchronous architecture, dynamic Vote Party events, gamified rewards, and modern Hex RGB visuals.

[![Release](https://img.shields.io/github/v/release/ImJopehhh/votify?color=emerald&style=flat-square)](https://github.com/ImJopehhh/votify/releases)
[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Spigot-5d68ec?style=flat-square)](https://papermc.io)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%20--%201.21.x%20%26%2026.x-brightgreen?style=flat-square)](https://www.minecraft.net)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/github/license/ImJopehhh/votify?style=flat-square)](LICENSE)

---

## 🌟 Overview

**Votify** is designed for server owners who demand reliability, aesthetics, and high performance from their voting ecosystem. Moving beyond fragile flatfile configurations, Votify introduces an **indexed SQLite database engine**, full **thread-safe execution**, and a frictionless voting experience for players through interactive GUIs, dynamic BossBars, and clickable web links.

---

## ✨ Key Features

### 🛡️ High-Performance Engine & Thread Safety
- **SQLite Database Architecture:** Say goodbye to corrupted flatfiles. Vote counts, streaks, metadata, and milestone history are securely stored in indexed SQLite tables (`votify_players`, `votify_metadata`, `votify_pending_rewards`).
- **Zero-Loss Auto-Migrator (`DataMigrator`):** Automatically migrates legacy `votedata.yml` records into SQLite without losing a single vote.
- **Asynchronous I/O & Thread Safety:** All heavy queries and vote hooks run off-thread, while inventory interactions, commands, titles, and particle effects strictly dispatch on the Bukkit main thread, ensuring a constant **20.0 TPS**.

### 🎨 Modern Visuals & Audio
- **Universal Hex RGB Colors (`ColorUtil`):** Supports modern `&#RRGGBB` hex color gradients across all messages, GUI layouts, lore, titles, and BossBars, with full backward compatibility for legacy `&` codes.
- **Dynamic Vote Party BossBar:** A persistent, real-time BossBar displaying vote party progress (`VOTE PARTY: X/Y votes (Z%)`) that adjusts automatically as votes arrive and when players join.
- **Spectacular Vote Party Celebrations:** Reaching vote party thresholds triggers server-wide multi-color fireworks, custom stereo audio soundscapes (`UI_TOAST_CHALLENGE_COMPLETE` + `ENTITY_PLAYER_LEVELUP`), full-screen titles, and synced reward distribution.

### 🎮 Gamified Rewards
- **🎲 Lucky Vote System:** Configurable chance-based rolls on incoming votes:
  - **Jackpot Tier (5%):** Massive cash bonus + broadcast announcement.
  - **Bonus Tier (15%):** Instant extra monetary or item reward.
- **🏆 Vote Milestones:** Long-term reward progression at vote thresholds (e.g., 10, 25, 50, 100 votes). Milestone claims are permanently tracked in the database to prevent duplicate exploit loops.
- **📈 Monthly Leaderboards & Streaks:** Automated monthly rankings and vote streak mechanics to encourage daily community engagement.

### 🌐 Seamless Player Experience
- **Modern `/vote` Hub:** Interactive inventory GUI featuring:
  - **Slot 11 (Beacon):** Vote Sites directory.
  - **Slot 13 (Player Head):** Personal statistics & current ranking.
  - **Slot 15 (Gold Ingot):** Monthly leaderboard podium.
- **Clickable Chat Links:** Clicking a vote site inside `/vote sites` automatically sends interactive BungeeCord chat components with a `[CLICK TO OPEN LINK]` action and hover tooltips.
- **Integrated Tab-Completion:** Full auto-completion for all player commands (`top`, `info`, `stats`, `sites`, `links`, `claim`, `help`).

---

## 💻 Commands & Permissions

### 👤 Player Commands (`/vote`)
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/vote` | `votify.vote` | Opens the main interactive voting menu. |
| `/vote sites` / `links` | `votify.vote` | Opens the vote link GUI and sends clickable URLs to chat. |
| `/vote top` | `votify.top` | Opens top voter GUI & prints chat summary. |
| `/vote stats` / `info` | `votify.stats` | Views personal vote statistics and current streak. |
| `/vote claim` | `votify.claim` | Claims available milestone rewards. |
| `/vote help` | `votify.vote` | Displays the help menu. |

### 🛡️ Admin Commands (`/votifyadmin`)
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/votifyadmin reload` | `votify.admin` | Reloads all configurations & resyncs BossBars. |
| `/votifyadmin party force` | `votify.admin` | Instantly starts a Vote Party event. |
| `/votifyadmin party add <amount>` | `votify.admin` | Adds votes directly toward the Vote Party goal. |
| `/votifyadmin party reset` | `votify.admin` | Resets the active Vote Party counter to 0. |
| `/votifyadmin setvotes <player> <amount> [type]` | `votify.admin` | Modifies player vote data (`total`, `monthly`, `weekly`). |
| `/votifyadmin resetplayer <player>` | `votify.admin` | Resets a player's vote records. |
| `/votifyadmin testvote <player> <service>` | `votify.admin` | Dispatches a simulated vote event for testing. |

---

## 🧩 PlaceholderAPI Support

Votify includes extensive PlaceholderAPI integration via `VotifyExpansion.java`:

| Placeholder | Description |
| :--- | :--- |
| `%votify_votes%` | Total lifetime votes of the player. |
| `%votify_monthly_votes%` | Total votes by the player in the current month. |
| `%votify_streak%` | Current active vote streak (in days). |
| `%votify_party_current%` | Current number of votes in the active Vote Party counter. |
| `%votify_party_required%` | Target number of votes required to trigger a Vote Party. |
| `%votify_party_percent%` | Active Vote Party completion percentage. |
| `%votify_top_name_<1-10>%` | Username of the top voter at the specified rank. |
| `%votify_top_votes_<1-10>%` | Vote count of the top voter at the specified rank. |

---

## ⚙️ Installation & Setup

1. **Prerequisites:**
   - Java 21 or higher.
   - Paper / Purpur / Spigot 1.16+ (Fully tested on 1.21.x and PaperMC Year.Drop schema).
   - [NuVotifier](https://github.com/NuVotifier/NuVotifier) installed and configured on your server.
   - *(Optional)* [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) and [Vault](https://www.spigotmc.org/resources/vault.34315/).
2. **Installation:**
   - Download the latest `votify-v1.1.0.jar` from [Releases](https://github.com/ImJopehhh/votify/releases).
   - Place the `.jar` into your server's `plugins/` directory.
   - Start or restart your server to generate configuration files and SQLite database tables.
   - *(If migrating from v1.0.x)* Keep `votedata.yml` in the folder; Votify will automatically import your existing data into SQLite on startup.
3. **Configuration:**
   - Edit `plugins/Votify/config.yml` to set your voting URLs, messages, and BossBar preferences.
   - Customize rewards, lucky vote chances, and milestones in `voterewards.yml`.
   - Run `/votifyadmin reload` to apply changes instantly without server downtime.

---

## 🛠️ Building from Source

```bash
# Clone the repository
git clone [https://github.com/ImJopehhh/votify.git](https://github.com/ImJopehhh/votify.git)

# Navigate into the project directory
cd votify

# Build with Maven
mvn clean package

```

Compiled binaries will be available inside the `target/` directory.

---

## 📄 License

This project is licensed under the [MIT License](https://www.google.com/search?q=LICENSE).
