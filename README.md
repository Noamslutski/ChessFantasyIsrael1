# Chess Fantasy Israel ♟️

A Sorare-style fantasy collectible card game for Android (Java), built around the
chess players of **Hapoel Petah Tikva Chess** (מועדון השחמט הפועל פתח תקווה).

No login, no backend — you're in the game the moment the app opens.

## Features

### 🃏 Card rarities & season minting caps
Every card is a serial-numbered mint of a real chess player for the current season:

| Tier | Color | Minted per player per season |
|---|---|---|
| Common | Gray | Unlimited (free packs) |
| Limited | Yellow | up to 5,000 — entry-level collectible |
| Rare | Red | up to 100 |
| Super Rare | Blue | up to 10 |
| Unique | Black | **1** — the ultimate collectible |

Serial numbers are printed on each card (e.g. `#7/100`). When a season's supply
of a tier is minted out, no more can exist until next season.

### 🪙 Pawns currency
The in-game currency is **Pawns** (a golden chess pawn). Earn them from daily
rewards, ads and selling cards; spend them on packs, auction bids, buy-nows and
trades.

### 🛒 Player-to-player market
- **Auctions** — timed auctions with live bidding (escrowed bids, refunds when outbid)
- **Buy now** — direct sales at a fixed price
- **Counter-offers** — for any listing you can propose a trade instead: any
  combination of your cards + Pawns. The other collector can accept, reject or
  **counter back** (e.g. "your cards plus 300 more Pawns"). You can also list
  and auction your own cards from the card detail screen.

The market is simulated locally by bot collectors (no server needed) — they
list cards, outbid you, buy your listings at fair prices and answer your trade
offers based on real card value.

### 🎁 Packs & rewards
- **Free Pack** (gray) — 3 Commons, every 4 hours
- **Limited / Rare / Super Rare / Unique packs** — priced in Pawns, colored by tier
- **Rewarded ads** — watch a (simulated) 15-second ad for a free reward pack:
  usually Commons, with a **15% chance of a Limited (Pro) card** (up to 10/day)
- **Daily reward** — the better your collection (sum of your top-5 card values,
  which scale with player ratings), the better the daily pack + Pawns

### 🏆 Real players, real games
All 18 roster players are **real Israeli chess professionals verified against the
live FIDE database** (July 2026) — every card carries the player's confirmed FIDE
ID and real current FIDE standard rating, plus real career achievements
(e.g. Gelfand's 2012 World Championship match, Sokolovsky's 2026 Israeli
Championship title).

Real-world games drive the gameplay:
- **Form bonus** — FIDE ratings only move when players play real rated games.
  When your players gain rating in real life, the app pays out Pawns
  (delta × card rarity multiplier). Claim on the Home screen; baselines reset
  after each claim.
- **Real games & profiles** — tap any player on the Players tab for career
  highlights and one-tap links to their live FIDE profile
  (`ratings.fide.com/profile/{id}`) and their **real rated games in PGN**
  (`ratings.fide.com/view_games.phtml?id={id}`).

### 📡 Live FIDE ratings API
The Players screen shows the club roster with FIDE ratings and a
**"Refresh live ratings"** button. Ratings are fetched from the public
**Lichess FIDE database API** (no API key needed):

- `GET https://lichess.org/api/fide/player/{fideId}` — direct lookup
- `GET https://lichess.org/api/fide/player?q={name}` — name search (Israeli
  federation preferred), resolved ids are cached

If the network is down or the API is unreachable the app silently keeps the
offline ratings bundled in `players.json` — the app never breaks offline.

## Editing the club roster

The roster lives in **`app/src/main/assets/players.json`**. Each entry:

```json
{ "id": "gelfand", "name": "Boris Gelfand", "hebrewName": "בוריס גלפנד",
  "title": "GM", "rating": 2635, "fideId": 2805677,
  "achievements": "World Championship challenger 2012 · Candidates winner 2011" }
```

- All bundled `fideId`s and ratings are verified against the FIDE database
  (July 2026); the live API keeps ratings current after that.
- `fideId` may be set to `0` for a new player — the app resolves it by name
  search automatically.
- Add/remove/rename players freely — the game picks the file up on next launch
  (cards of removed players keep working).

> ℹ️ The bundled roster is verified real Israeli chess professionals. To mirror
> the exact current Hapoel Petah Tikva club lineup, adjust this file using the
> club page on the Israeli Chess Federation site:
> https://www.chess.org.il/clubs/Club.aspx?Id=30

## Building & running

1. Open the project folder in **Android Studio** (Hedgehog or newer).
2. Let Gradle sync (AGP 8.5.2, Gradle 8.9, compileSdk 34, minSdk 26).
3. Run ▶ on an emulator or device (Android 8.0+).

Or from the command line: `./gradlew assembleDebug`

## Project layout

```
app/src/main/java/com/chessfantasy/israel/
├── MainActivity.java            # bottom-nav shell (no login)
├── ChessFantasyApp.java         # app entry, initializes the game
├── model/                       # Rarity, Player, Card, PackType, Auction,
│                                # SaleListing, TradeOffer, GameState
├── data/
│   ├── GameRepository.java      # game logic: minting caps, packs, market
│   │                            # simulation, trades, rewards, persistence
│   └── PlayerCatalog.java       # loads players.json
├── api/FideRatingApi.java       # live FIDE ratings (Lichess public API)
└── ui/                          # fragments, adapters, pack opening,
                                 # rewarded ad, trade offer, card detail
```

Game state persists in `SharedPreferences` as JSON (Gson) — uninstall or clear
app data to restart from scratch.
