# Dependency Scanning Tool - Gradle (Android License Plugin)

Gradle Plugin to check library licenses and generate license pages.

* `./gradlew generateLicenseTxt` to generate a license text file `licenses.txt`
* `./gradlew checkLicenses` to check licenses in dependencies
* `./gradlew updateLicenses` to update library information file `licenses.yml`

## Setup // not yet uploaded

This plugin requires JDK8 (1.8.0 or later).

```gradle
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath 'com.lge.android.license:dependency-scanning-tool:0.1.0'
    }
}

apply plugin: 'com.lge.android.licensetools'
```

See [example/build.gradle] for example.

## How To Use

### Generate `licenses.txt` by the `generateLicenseTxt` task

`./gradlew generateLicenseTxt` generates `app/licenses.txt`.
It generates a file 'licenses.txt' that lists the dependencies (including transitive dependencies) information.

Each OSS information (OSS name, OSS version, license name, download location, homepage) can be separated by 'tab' separator.
This tab-delimited text file can be converted for csv file.

### Example of licenses.txt

```
OSS Name	OSS Version	License Name	Download Location	Homepage
Android Support Library media compat	27.0.0	The Apache Software License, Version 2.0	https://mvnrepository.com/artifact/com.android.support/support-media-compat/27.0.0	http://developer.android.com/tools/extras/support-library.html
Android Support AnimatedVectorDrawable	27.0.0	The Apache Software License, Version 2.0	https://mvnrepository.com/artifact/com.android.support/animated-vector-drawable/27.0.0	http://developer.android.com/tools/extras/support-library.html
Android Support VectorDrawable	27.0.0	The Apache Software License, Version 2.0	https://mvnrepository.com/artifact/com.android.support/support-vector-drawable/27.0.0	http://developer.android.com/tools/extras/support-library.html
```

### Run the `checkLicenses` task

You will see the following messages by `./gradlew checkLicenses`:

```yaml
# Libraries not listed:
- artifact: com.android.support:support-v4:+
  name: #NAME#
  copyrightHolder: #AUTHOR#
  license: No license found
- artifact: com.android.support:animated-vector-drawable:+
  name: #NAME#
  copyrightHolder: #AUTHOR#
  license: No license found
- artifact: io.reactivex:rxjava:+
  name: #NAME#
  copyrightHolder: #AUTHOR#
  license: apache2
 ```

### Add library licenses to `app/licenses.yml`

Then, Create `app/licenses.yml`, and add libraries listed the above with required fields:

```yaml
- artifact: com.android.support:+:+
  name: Android Support Libraries
  copyrightHolder: The Android Open Source Project
  license: apache2
- artifact: io.reactivex:rxjava:+
  name: RxJava
  copyrightHolder: Netflix, Inc.
  license: apache2
```

You can use wildcards in artifact names and versions.
You'll know the Android support libraries are grouped in `com.android.support` so you use `com.android.support:+:+` here.

Instead of manually appending missing libraries to `licenses.yml`,
you can also run the `updateLicenses` task to update `licenses.yml` automatically.

Then, `./gradlew checkLicenses` will passes.


### Example

```yaml
- artifact: com.android.support:+:+
  name: Android Support Libraries
  copyrightHolder: The Android Open Source Project
  license: apache2
- artifact: org.abego.treelayout:org.abego.treelayout.core:+
  name: abego TreeLayout
  copyrightHolder: abego Software
  license: bsd_3_clauses
- artifact: io.reactivex:rxjava:+
  name: RxJava
  copyrightHolder: Netflix, Inc.
  license: apache2
- artifact: com.tunnelvisionlabs:antlr4-runtime:4.5
  name: ANTLR4
  authors:
    - Terence Parr
    - Sam Harwell
  license: bsd_3_clauses
- artifact: com.github.gfx.android.orma:+:+
  name: Android Orma
  notice: |
    Copyright (c) 2015 FUJI Goro (gfx)
    SQLite.g4 is: Copyright (c) 2014 by Bart Kiers
  license: apache_2
- artifact: io.reactivex:rxandroid:1.2.0
  name: RxAndroid
  copyrightHolder: The RxAndroid authors
  license: apache2
- artifact: license-tools-plugin:example-dep:+
  skip: true
- name: OpenCV
  copyrightHolder: OpenCV team
  license: bsd_3_clauses
  url: "https://opencv.org/"
  forceGenerate: true
```

Keep `CHANGES.md` up-to-date.

## Copyright and License
Copyright (c) 2019 LG Electronics.

Copyright (c) 2016 Cookpad Inc.

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
