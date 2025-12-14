# mpm - Minecraft Plugin Manager

## About

mpm is a comprehensive plugin management system for Minecraft servers. It simplifies the process of installing, updating, and managing plugins from multiple platforms including SpigotMC, Modrinth, Hangar, and GitHub.

## Tech Stack

- Language: Kotlin
- Server Software: PaperMC
- Dependency Injection: Koin
- HTTP Client: Ktor
- Serialization: kotlinx-serialization(json, yaml)
- Build Tool: Gradle

## Features

- **Multi-Platform Support**: Download plugins from SpigotMC, Modrinth, Hangar, and GitHub
- **Repository-Based Workflow**: Manage plugins using JSON configuration files
- **Automatic Version Detection**: Auto-detect compatible versions for your server
- **File Pattern Matching**: Filter and select specific plugin files using patterns
- **Command-Line Interface**: Intuitive commands for plugin management
- **Dependency Injection**: Clean architecture using Koin DI framework

## Build & Development

Build the plugin:

```bash
task build
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
- `paper/` - PaperMC plugin implementation
- `docs/` - Documentation site (Docusaurus)

## License

Written in 2024-2025 by Nikomaru &emsp; No Rights Reserved.

To the extent possible under law, Nikomaru has waived all copyright and related or neighboring rights to mpm. This work is published from: Japan.

You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
