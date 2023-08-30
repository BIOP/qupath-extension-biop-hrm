# QuPath HRM extension

Welcome to the HRM extension for [QuPath](http://qupath.github.io)!

This adds support for sending images within a QuPath project to HRM server and retrieve them back to QuPath.

The extension is intended for QuPath v0.4.x (at the time of writing).
It is not compatible with earlier QuPath versions.

## Installing

*Downloads*

- To install the HRM extension, download the latest `qupath-extension-biop-hrm-[version].jar` file from [releases](https://github.com/BIOP/qupath-extension-biop-hrm/releases/latest) drag the .jar onto the main QuPath window.

- If you haven't installed any extensions before, you'll be prompted to select a QuPath user directory.
The extension will then be copied to a location inside that directory.

- The extension needs to have `qupath-extension-biop-omero` installed. Follow the [installation steps](https://github.com/BIOP/qupath-extension-biop-omero#readme) provided for this extension. **BE CAREFUL : Only versions v0.3.2 or higher are compatible with this extension.**

*Update*
- You might then need to restart QuPath (but not your computer).


## Building

You can build the extension using OpenJDK 17 or later with

```bash
gradlew clean build
```

The output will be under `build/libs`.
You can drag the jar file on top of QuPath to install the extension.

## Documentation
You can find all the documentation on how to use this extension on our [wiki page](https://wiki-biop.epfl.ch/en/ipa/qupath/hrm).
