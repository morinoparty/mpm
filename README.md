# mpm - Minecraft Plugin Manager

## About

mpm is a comprehensive plugin management system for Minecraft servers and Velocity proxies. It simplifies the process of installing, updating, and managing plugins from multiple platforms including SpigotMC, Modrinth, Hangar, and GitHub.

## Tech Stack

- Language: Kotlin
- Server Software: PaperMC, Velocity
- Dependency Injection: Koin
- HTTP Client: Ktor
- Serialization: kotlinx-serialization(json, yaml)
- Build Tool: Gradle

## Features

- **Multi-Platform Support**: Download plugins from SpigotMC, Modrinth, Hangar, and GitHub
- **Paper and Velocity**: Runs on Paper servers (`/mpm`) and Velocity proxies (`/mpmv`), sharing the same workflow and file formats
- **Repository-Based Workflow**: Manage plugins using JSON configuration files
- **Automatic Version Detection**: Auto-detect compatible versions for your server
- **File Pattern Matching**: Filter and select specific plugin files using patterns
- **Command-Line Interface**: Intuitive commands for plugin management
- **Dependency Injection**: Clean architecture using Koin DI framework

## Velocity

The Velocity build uses the `/mpmv` root command so it does not shadow `/mpm` on backend servers.
Velocity has no OP, so grant permissions with a permission plugin such as LuckPerms (e.g. `/lpv group admin permission set mpm.command true`).
See the [Velocity guide](https://mpm.plugin.morino.party/docs/usage/velocity) for details.

## Build & Development

Build the plugin:

```bash
task build
```

Start a local Paper server or Velocity proxy with the plugin:

```bash
task run
task run:velocity
```

Run tests:

```bash
task test
```

Check code style and quality:

```bash
task check
```

Format code with ktlint:

```bash
task format
```

## Documentation

For detailed documentation, please visit the [documentation site](https://mpm.plugin.morino.party/).

## Project Structure

- `api/` - API interfaces and configuration classes
- `common/` - Platform-independent logic shared by Paper and Velocity
- `paper/` - PaperMC plugin implementation
- `velocity/` - Velocity plugin implementation
- `docs/` - Documentation site (Fumadocs)

## License

Written in 2024-2025 by Nikomaru &emsp; No Rights Reserved.

To the extent possible under law, Nikomaru has waived all copyright and related or neighboring rights to mpm. This work is published from: Japan.

You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
