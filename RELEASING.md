To deploy a release, run the following:

```
./gradlew deployRelease -Pincrement=[major|minor|patch]
```

The command does the following:

1. Builds `nanoscope` distribution zip file.
2. Uploads zip as GitHub release.
3. Increments version in homebrew-nanoscope [repo](https://github.com/uber/homebrew-nanoscope) and points to new release.