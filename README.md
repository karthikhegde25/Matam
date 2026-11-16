<p align="center">
  <img src="docs/logo.png" alt="Matam logo" width="140" />
</p>

<h1 align="center">Matam</h1>

<p align="center">
  <b>Offline-first election management for Android</b><br/>
  Run fair, fast and transparent elections for classrooms, clubs, societies and communities, with no internet needed.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Version" src="https://img.shields.io/badge/version-1.0-blue" />
  <img alt="License" src="https://img.shields.io/badge/license-GPL%20v3-blue" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Offline" src="https://img.shields.io/badge/works-offline-success" />
  <img alt="Bluetooth" src="https://img.shields.io/badge/two--phone-Bluetooth%20voting-0082FC?logo=bluetooth&logoColor=white" />
  <img alt="Open source" src="https://img.shields.io/badge/open%20source-yes-brightgreen" />
</p>

<p align="center">
  <a href="https://github.com/karthikhegde25/Matam/releases">
    <img src="docs/get-it-on-github.png" alt="Get it on GitHub" width="220" />
  </a>
</p>

---

## Features

- **Elections** with parties, candidates, photos or emojis, categories, dates and instructions
- **Two ballot styles:** a modern card ballot and an EVM-like ballot unit
- **Single-phone voting** or **two-phone Bluetooth voting** (controller and voting machine)
- **Landscape voting mode** for wide, easy-to-read ballots
- **Password per election** to edit, resume, complete, view results and delete
- **Results in 5 layouts**, with winner, margins and percentages
- **Export and backup:** PDF, CSV and JSON, with full backup and restore
- **History, search, audit log** and light/dark themes
- **Fully offline:** no account, no server, no ads

## Two-phone Bluetooth voting

1. On both phones open **Bluetooth voting** from Home.
2. Phone A: **Use this phone as Controller** (start this one first).
3. Phone B: **Use this phone as Voting machine**, then tap Phone A in the device list.
4. On the controller choose the election and enter its password. The election is sent to the voting machine.
5. Approve each voter on the controller. Votes are confirmed back to the controller and nothing is lost if the link drops.

Bluetooth pairing is not voter authentication. This mode is meant for supervised, non-binding votes.

## Build

Open the folder in Android Studio (JDK 17), let Gradle sync, and run on a device (minSdk 26).

Release APKs are built and signed with GitHub Actions using your keystore stored as repository secrets.

## Tech stack

Kotlin, Jetpack Compose (Material 3), MVVM, Room, Navigation Compose, DataStore, Bluetooth Classic (RFCOMM).

## License

Matam is free and open-source software released under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html).

Developed by **Karthik Hegde** · [github.com/karthikhegde25/Matam](https://github.com/karthikhegde25/Matam)
