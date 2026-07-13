# Chess Fantasy Israel ♟️

A Sorare-style fantasy collectible card game for Android (Java), built around
**every notable Israeli chess player** — all 30 of Israel's titled players
(23 Grandmasters, 4 International Masters, 2 Woman Grandmasters, 1 Woman IM),
each a real person with a **verified FIDE ID**.

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
- **Welcome gift** — a new account starts with **3 packs to open** (a Free, a
  Limited and a Rare pack), shown under "Your packs" on the Packs tab
- **Daily Spin** 🎡 — once every 24 hours, spin the wheel to win a free pack;
  it can land anywhere from Common up to a Unique (weighted toward the common
  tiers). The won pack opens right after the wheel stops.
- **Free Pack** (gray) — 3 Commons, every 4 hours
- **Limited / Rare / Super Rare / Unique packs** — priced in Pawns, colored by tier
- **Rewarded ads** — watch a (simulated) 15-second ad for a free reward pack:
  usually Commons, with a **15% chance of a Limited (Pro) card** (up to 10/day)
- **Daily reward** — the better your collection (sum of your top-5 card values,
  which scale with player ratings), the better the daily pack + Pawns

### 🏆 Real players, real games
All 30 roster players are **real Israeli titled players verified against the
live FIDE database** (July 2026) — every card carries the player's confirmed FIDE
ID and real current FIDE standard rating, plus real career achievements
(e.g. Gelfand's 2012 World Championship match, Sokolovsky's 2026 Israeli
Championship title, Klinova's 2024 World Women's Senior title). The roster spans
Israel's elite (Gelfand, Rodshtein, Nabaty, Sutovsky, Smirin, Roiz, Postny…)
through its rising IMs and its leading women players. Deceased players and those
who have transferred to another federation are deliberately excluded.

Real-world games drive the gameplay:
- **Form bonus** — FIDE ratings only move when players play real rated games.
  When your players gain rating in real life, the app pays out Pawns
  (delta × card rarity multiplier). Claim on the Home screen; baselines reset
  after each claim.
- **Real games & profiles** — tap any player on the Players tab for career
  highlights and one-tap links to their live FIDE profile
  (`ratings.fide.com/profile/{id}`) and their **real rated games in PGN**
  (`ratings.fide.com/view_games.phtml?id={id}`).

### 🔎 Live federation search (parse.bot API)
The Players tab has a **"Search the Israel federation online"** button. It sends
the name in the search box to a parse.bot scraper of the Israeli Chess
Federation database and lets you tick which real players to add to your game
(they persist and become mintable). Results are parsed defensively — the client
auto-detects each player's name, rating, FIDE id and title from the response, so
it adapts to the scraper's exact JSON shape.

**API key (never committed):** the key is injected at build time into
`BuildConfig.PARSE_API_KEY`. Provide it any of these ways — do **not** hard-code
it in source:

```bash
# 1. gradle property on the command line
./gradlew assembleDebug -PPARSE_API_KEY=your_key_here

# 2. or in local.properties (git-ignored) / ~/.gradle/gradle.properties
echo 'PARSE_API_KEY=your_key_here' >> local.properties

# 3. or an environment variable
export PARSE_API_KEY=your_key_here
```

If no key is set the app still runs — the online-search button simply reports
that the key isn't configured. Everything else (FIDE, chess.org.il, chess.com,
the full-list loader) works without it.

### 📡 Live ratings from multiple real chess data sources
The Players screen has a **"Refresh live ratings"** button that pulls from
**three real chess data sources** and merges them per player (tap any player to
see every source's rating and open their real profiles/games):

| Source | Endpoint | What it provides |
|---|---|---|
| **FIDE** (official, via Lichess FIDE DB mirror) | `GET lichess.org/api/fide/player/{fideId}` and `?q={name}` | FIDE standard / rapid / blitz — the primary rating |
| **Israeli Chess Federation** (chess.org.il) | `players/Player.aspx?Id={ilId}` (HTML, parsed) | National rating (מד כושר) |
| **chess.com** (official Published-Data API) | `GET api.chess.com/pub/player/{user}/stats` | chess.com blitz/rapid + the FIDE rating chess.com stores |

All are **keyless**. The design is a pluggable `RatingProvider` interface
(`api/FideProvider`, `api/IsraeliChessProvider`, `api/ChessComProvider`)
orchestrated by `api/RatingService` — adding another source (e.g. a
chess-results.com team feed) is one new class.

### 🇮🇱 Load *every* FIDE-rated Israeli player
The Players tab has a **"Load ALL FIDE-rated Israeli players"** button. It
streams FIDE's full monthly rating list
(`ratings.fide.com/download/standard_rating_list_xml.zip`), filters it to
federation **ISR**, and adds every rated Israeli player — thousands, from the
grandmasters down to club and junior players — to the playable pool
(`api/FideFullListLoader`).

Because the list is large, it is **streamed**: the zip is unzipped on the fly
and parsed one `<player>` block at a time (never fully held in memory), keeping
only ISR rated players, and the result is cached to app-private storage so it is
downloaded only when you ask and survives restarts. Downloaded players merge
with the curated roster (deduped by FIDE ID — curated entries keep their Hebrew
names, achievements and chess.org.il IDs), become mintable in packs, and get
their real FIDE rating straight from the list. A **search box** on the Players
tab keeps the now-huge list navigable.

Each source **fails independently and gracefully**: a player is matched by
`fideId`/`ilId`/`chessComUser` from `players.json`, and if a source is
unreachable or a field is missing the app just uses the next source, falling
back to the offline rating bundled in `players.json`. The app never breaks
without internet. The bundled roster ships with verified FIDE IDs for all 30
players and, for the 10 top players, verified chess.org.il IDs; set
`ilId`/`chessComUser` for the rest to light up those sources.

## Editing the roster

The roster lives in **`app/src/main/assets/players.json`** — all 30 real Israeli
titled players. Each entry:

```json
{ "id": "gelfand", "name": "Boris Gelfand", "hebrewName": "בוריס גלפנד",
  "title": "GM", "rating": 2635, "fideId": 2805677, "ilId": 4, "chessComUser": "",
  "achievements": "World Championship challenger 2012 · Candidates winner 2011" }
```

- Every bundled `fideId` is verified against the FIDE database (July 2026); the
  live multi-source refresh keeps ratings current after that.
- `fideId` may be `0` for a new player — the app resolves it by name search.
  `ilId` (chess.org.il) and `chessComUser` are optional per-player.
- Add/remove/rename players freely — the game picks the file up on next launch
  (cards of removed players keep working).

> ℹ️ Want to scope the game to a single club (e.g. Hapoel Petah Tikva) instead
> of the whole country? Just keep that club's players in this file. To pull an
> exact club lineup, use its page on the Israeli Chess Federation site, e.g.
> https://www.chess.org.il/clubs/Club.aspx?Id=30
>
> Want *every* FIDE-rated Israeli player (thousands, including club and junior
> players)? That's a different scale — a runtime loader over FIDE's full rating
> list filtered to federation ISR. Open an issue and it can be added.

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
├── api/                         # RatingService orchestrates FideProvider,
│                                # IsraeliChessProvider, ChessComProvider;
│                                # FideFullListLoader streams FIDE's full list
│                                # and filters it to federation ISR
└── ui/                          # fragments, adapters, pack opening,
                                 # rewarded ad, trade offer, card detail
```

Game state persists in `SharedPreferences` as JSON (Gson) — uninstall or clear
app data to restart from scratch.
