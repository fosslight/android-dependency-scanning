# Android Dependency Scanning Tool

It is a gradle plugin to analyze the android dependencies and generate the report.

## Features
- Run based on Android studio.
- It prints the license names of downloaded libraries based on dependencies in build.gradle.
- It doesn't print the dependencies of test type and 'compileOnly' type.
- It can also print the license of the libraries referenced by the libraries defined in dependencies.


## 🎉 How to Setup

This plugin requires JDK8 (1.8.0 or later).

1. Add the repositories and classpath in the 'build.gradle' file of the project.
```
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        // Android dependency scanning Plugin
        classpath 'com.lge.android.licensetools:android-dependency-scanning:0.4.0'
    }
}
```
2. In the 'settings.gradle' file located in the top directory of project, check the module name included in the final build.  
(In case of multi module projects, License scanning is performed for all the modules included in setting.gradle even if only one module specified in setting.gradle is executed after step 3.)  

3. Add the below line in build.gradle file of {module name} directory to apply the plugin.
```
apply plugin: 'com.lge.android.licensetools'
```


## 🚀 How To Use

Please run the below command in the terminal.
```
./gradlew generateLicenseTxt
```


## 📁 Result
- Output file name : android_dependency_output.txt
- Description
  - It can be found in 'module name (default:app)' directory where this plugin is applied.
  - Each OSS information can be separated by 'tab' separator. This tab-delimited text file can be converted for csv file.

```
ID	Source Name or Path	OSS Name	OSS Version	License	Download Location	Homepage	Copyright Text	License Text	Exclude	Comment
-	[Name of the Source File or Path]	[Name of the OSS used in the Source Code]	[Version Number of the OSS]	[License of the OSS. Use SPDX Identifier : https://spdx.org/licenses/]	[Download URL or a specific location within a VCS for the OSS]	[Web site that serves as the OSS's home page]	[The copyright holders of the OSS]	[License Text of the License. This field can be skipped if the License is in SPDX.]	[If this OSS is not included in the final version, Exclude]	
1	build.gradle	android.arch.core:common	1.1.1	The Apache Software License Version 2.0	https://mvnrepository.com/artifact/android.arch.core/common/1.1.1	https://developer.android.com/topic/libraries/architecture/index.html		http://www.apache.org/licenses/LICENSE-2.0.txt
2	build.gradle	com.google.code.findbugs:jsr305	3.0.1	The Apache Software License Version 2.0	https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305/3.0.1	http://findbugs.sourceforge.net/		http://www.apache.org/licenses/LICENSE-2.0.txt
```


## 📄 License
Copyright (c) 2019 LG Electronics.  
Copyright (c) 2016 Cookpad Inc.  
Android dependency scanning is released under [Apache-2.0](LICENSE.md).
