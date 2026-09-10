# Pokemon Illustrated Guide

An Android homework project that builds a Pokemon illustrated guide with data from the first 151 Pokemon in [PokeAPI](https://pokeapi.co/).

## Features

- Browse Pokemon grouped by type, with each group ordered by Pokedex ID.
- View Pokemon details, including artwork, types, description, and pre-evolution navigation.
- Capture and release Pokemon from a personal Pocket; duplicate captures are supported and ordered by capture time.
- Persist data locally with Room and resume incomplete downloads after interruption or network recovery.

## Environment requirements

### Running the app

- Android 8.0 (API level 26) or later.
- Network access is required for the initial Pokemon data download; the downloaded data and Pocket records are then stored locally.

### Building from source

- A recent Android Studio release compatible with Android Gradle Plugin 9.3.2.
- Android SDK Platform 37 installed (`compileSdk 37`).
- JDK 17 or later. The project includes the Gradle wrapper, so Gradle 9.5 is downloaded automatically when needed.

## Notes

See [NOTES.md](NOTES.md) for assignment decisions, AI assistance disclosure, verification evidence, known limitations, and future improvements.
