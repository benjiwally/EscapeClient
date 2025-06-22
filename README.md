# Escape Mod

A comprehensive Fabric mod for Minecraft 1.21.1 that provides advanced automation for anarchy server survival.

## Features

### Core Modes
- **Escape Mode**: Automatically travels 56,000 blocks from spawn while gathering resources
- **Anarchy Assistant**: Analyzes your surroundings and provides strategic survival advice

### Advanced Systems
- **Auto-Crafting**: Automatically crafts tools, weapons, armor, and food based on available materials
- **Inventory Management**: Intelligently organizes inventory, drops junk items, and prioritizes valuable items
- **Anti-Grief Protection**: Detects traps, suspicious structures, and monitors nearby players
- **Combat System**: Automatically fights hostile mobs and optionally hostile players
- **Smart Navigation**: Advanced pathfinding that avoids dangers and finds safe routes
- **Resource Gathering**: Prioritizes diamond equipment and essential survival items
- **Progress Tracking**: Real-time status updates and comprehensive reporting

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your mods folder
3. Build this mod using `./gradlew build` (or `gradlew.bat build` on Windows)
4. Place the generated JAR file from `build/libs/` into your mods folder

## Usage

1. Join a Minecraft server (works best on anarchy servers)
2. Press **F8** to start/stop the escape bot
3. The bot will automatically:
   - Choose a random direction
   - Start walking towards the target (56k blocks away)
   - Search for food when hungry
   - Mine resources when found
   - Show progress updates

## Controls

- **F8**: Toggle escape mode on/off
- **F9**: Run anarchy assistant analysis
- **Chat Commands**: Type `% help` for full command list

### Chat Commands

- `%` - Toggle escape mode
- `% start` - Start escape mode
- `% stop` - Stop escape mode  
- `% status` - Show current status and progress
- `% assistant` - Run anarchy assistant analysis
- `% help` - Show all available commands

## How It Works

### Escape Mode Priority System
1. **Anti-Grief Protection**: Continuously scans for traps and threats
2. **Combat**: Fights hostile mobs and players if threatened
3. **Resource Mining**: Mines valuable ores when found
4. **Auto-Crafting**: Creates essential tools and equipment
5. **Food Management**: Maintains hunger levels above 15
6. **Movement**: Continues towards 56k block target using safe paths

### Anarchy Assistant Analysis
The assistant evaluates multiple factors:
- **Location Analysis**: Distance from spawn, biome, elevation, nearby structures
- **Inventory Assessment**: Tools, armor, food, and resource availability  
- **Threat Detection**: Nearby players, dangerous blocks, time of day
- **Strategic Recommendations**: Prioritized advice based on current situation

### Advanced Features
- **Smart Inventory**: Auto-sorts items by priority, drops junk, organizes hotbar
- **Trap Detection**: Identifies lava traps, TNT, pressure plates, and suspicious patterns
- **Player Monitoring**: Tracks nearby players and alerts to potential threats
- **Emergency Systems**: Auto-retreat when outnumbered or low health
- **Resource Optimization**: Prioritizes diamond > iron > stone > wood equipment

## Building

To build the mod yourself:

```bash
./gradlew build
```

The built JAR will be in `build/libs/escapemod-1.0.0.jar`

## Compatibility

- Minecraft 1.21.1
- Fabric Loader 0.16.5+
- Fabric API 0.102.0+
- Java 21+

## Disclaimer

This mod is designed for anarchy servers where automation is typically allowed. Always check server rules before using any automation mods. Use at your own risk.

## License

MIT License - see LICENSE file for details.
