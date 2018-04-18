# Nanoscope

An extremely accurate Android method tracing tool.

![](images/nanoscope.gif?raw=true)

## Installation

**Install the `nanoscope` command**
```bash
$ brew tap uber/nanoscope git@github.com:uber/homebrew-nanoscope.git
$ brew install nanoscope
```

**Flash ADB-connected phone with the Nanoscope ROM**

*IMPORTANT: This will only work with a Nexus 6P - do not attempt to flash any other device*

```bash
$ nanoscope flash
```

## Usage

**Start tracing on ADB-connected device**
```bash
$ nanoscope start
Tracing... (Press ENTER to stop)
```

## Upgrade

**Client**
```
$ brew update && brew upgrade nanoscope
```

**ROM**
```
$ brew update && brew upgrade nanoscope
$ nanoscope flash
```
