# Nanoscope

An extremely accurate Android method tracing tool.

![](images/nanoscope.gif?raw=true)

## Overview

Nanoscope is a method tracing tool optimized for extreme accuracy. It's instrumentation introduces only ~20 nanoseconds of overhead per method (on a Nexus 6P).  In order to achieve this performance, the interesting pieces of Nanoscope are implemented as a [fork of AOSP](https://github.com/uber/nanoscope-art). For this reason, **you'll need a device running the Nanoscope OS in order to make use of this tool**. The entrypoint for provisioning a device with the custom OS and starting/stopping tracing is the `nanoscope` command-line tool described below.

For more information on motivation and architecture, check out the [wiki](https://github.com/uber/nanoscope/wiki).

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
