# Android Dependency Scanning
<img src="https://img.shields.io/badge/license-Apache--2.0-green" alt="FOSSLight android dependency scanning is released under the Apache-2.0." />

Android Dependency Scanning is a Gradle plugin that analyzes Android project dependencies and generates an OSS report.

## Features

- Runs on Android Gradle projects.
- Collects direct and transitive dependencies.
- Collects OSS names, versions, licenses, download locations, and copyright information.
- Excludes test and `compileOnly` dependencies.
- Supports Gradle 9 or later by analyzing resolved artifacts from a Release Runtime Configuration.
- Supports selecting a project-specific Runtime Configuration.

## Version Compatibility

### v1.0.0

v1.0.0 analyzes dependencies by copying declarable configurations, such as `implementation` and `api`, and resolving the copied configurations.

This version is intended for existing projects using Gradle 8 or earlier.

### v2.0.x

Starting with v2.0.0, the plugin analyzes resolved artifacts from the Runtime Configuration used for the actual Release build.

```text
Release Runtime Configuration
→ Resolved artifacts
→ Duplicate removal based on group:name:version
→ OSS report generation
```

This approach supports Gradle 9 or later and reflects the dependency versions selected for the actual Release build.

v2.0.1 additionally supports specifying a project-specific Runtime Configuration. If no Runtime Configuration is specified, `releaseRuntimeClasspath` is used by default.

## 🎉 How to Setup

The required JDK version depends on the Gradle and Android Gradle Plugin versions used by the project.

1. Add Maven Central and the plugin classpath to the project-level `build.gradle` file.

```groovy
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        // Android Dependency Scanning Plugin
        classpath 'org.fosslight:android-dependency-scanning:2.0.1'
    }
}
```

2. Apply the plugin in the `build.gradle` file of the Android application module.

```groovy
apply plugin: 'org.fosslight'

licenseTools {
    runtimeConfigurationName = 'releaseRuntimeClasspath'
}
```

`runtimeConfigurationName` specifies the Runtime Configuration to analyze. The default value is `releaseRuntimeClasspath`.

## 🚀 How To Use

Run the following command in the directory containing the Gradle Wrapper.

Linux or macOS:
```bash
./gradlew generateLicenseTxt
```
Windows:
```bat
gradlew.bat generateLicenseTxt
```

When running the Gradle plugin directly, specify the Runtime Configuration using `licenseTools.runtimeConfigurationName` in the module's `build.gradle` file.

When using FOSSLight Dependency Scanner, specify the Runtime Configuration using the `--runtime-config` option.

```bash
fosslight_dependency --runtime-config {runtime-configuration}
```

If `--runtime-config` is not specified, `releaseRuntimeClasspath` is used by default.

FOSSLight Dependency Scanner automatically selects the appropriate Android Dependency Scanning plugin according to the project's Gradle version. Therefore, users generally do not need to select the plugin version manually.

### Check Available Runtime Configurations

To check the resolvable configurations available in the application module, run:

```bash
./gradlew :app:resolvableConfigurations
```

Replace `app` with the actual application module name if the project uses a different module name.

## 📁 Result
- Output file name: `android_dependency_output.txt`
- The output file is created in the directory of the module to which the plugin is applied.
- Each OSS entry is separated by a tab.
- The tab-delimited output file can be converted to a CSV file.

```text
ID	Source Name or Path	OSS Name	OSS Version	License	Download Location	Homepage	Copyright Text	License Text	Exclude	Comment
-	[Name of the Source File or Path]	[Name of the OSS used in the Source Code]	[Version Number of the OSS]	[License of the OSS. Use an SPDX Identifier: https://spdx.org/licenses/]	[Download URL or a specific location within a VCS for the OSS]	[Website that serves as the OSS homepage]	[Copyright holders of the OSS]	[License text. This field can be skipped if the license is registered in SPDX.]	[Whether the OSS is excluded from the final version]	[Comment]
1	build.gradle	android.arch.core:common	1.1.1	The Apache Software License Version 2.0	https://mvnrepository.com/artifact/android.arch.core/common/1.1.1	https://developer.android.com/topic/libraries/architecture/index.html		http://www.apache.org/licenses/LICENSE-2.0.txt
2	build.gradle	com.google.code.findbugs:jsr305	3.0.1	The Apache Software License Version 2.0	https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305/3.0.1	http://findbugs.sourceforge.net/		http://www.apache.org/licenses/LICENSE-2.0.txt
```

## 📄 License
Copyright (c) 2019 LG Electronics  
Copyright (c) 2016 Cookpad Inc.  
Android dependency scanning is released under [Apache-2.0](LICENSE.md).  
This project is based on the [cookpad/license-tools-plugin](https://github.com/cookpad/license-tools-plugin).
