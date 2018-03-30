# Nanoscope

An extremely accurate Android method tracing tool.

![](nanoscope.gif?raw=true)

## Installation

**Install the `nanoscope` command**
```bash
$ brew tap uber/nanoscope git@github.com:uber/homebrew-nanoscope.git
$ brew install nanoscope
```

**Flash ADB-connected phone with the Nanoscope ROM**
```bash
$ nanoscope flash
```

## Usage

**Start tracing on ADB-connected device**
```bash
$ nanoscope start
Tracing... (Press ENTER to stop)
```

**Open existing trace file**

```bash
# Supports Nanoscope and Chrome trace files
$ nanoscope open <filename>
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
