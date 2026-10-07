package org.fosslight

import groovy.json.JsonBuilder
import groovy.xml.XmlSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.xml.sax.helpers.DefaultHandler
import org.yaml.snakeyaml.Yaml

class LicenseToolsPlugin implements Plugin<Project> {

    private static final List<String> LICENSE_TXT_HEADER_FIELDS = [
        '"ID"',
        'Source Name or Path',
        'OSS Name',
        'OSS Version',
        'License',
        'Download Location',
        'Homepage',
        'Copyright Text',
        'License Text',
        'Exclude',
        'Comment'
    ]
    private static final List<String> LICENSE_TXT_TEMPLATE_FIELDS = [
        '-',
        '[Name of the Source File or Path]',
        '[Name of the OSS used in the Source Code]',
        '[Version Number of the OSS]',
        '[License of the OSS. Use SPDX Identifier : https://spdx.org/licenses/]',
        '[Download URL or a specific location within a VCS for the OSS]',
        '[Web site that serves as the OSS\'s home page]',
        '[The copyright holders of the OSS]',
        '[License Text of the License. This field can be skipped if the License is in SPDX.]',
        '[If this OSS is not included in the final version, Exclude]'
    ]

    final yaml = new Yaml()

    final DependencySet librariesYaml = new DependencySet() // based on libraries.yml
    final DependencySet dependencyLicenses = new DependencySet() // based on license plugin's dependency-license.xml

    @Override
    void apply(Project project) {
        project.extensions.add(LicenseToolsExtension.NAME, LicenseToolsExtension)

        def checkLicenses = project.task('checkLicenses').doLast {
            initialize(project)

            def result = collectLicenseCheckResult(project)
            if (result.notDocumented.empty && result.notInDependencies.empty && result.licensesNotMatched.empty) {
                project.logger.info("checkLicenses: ok")
                return
            }

            logLicenseCheckResult(project, result)
            throw new GradleException("checkLicenses: missing libraries in ${extension(project).licensesYaml}")
        }

        checkLicenses.configure {
            group = "Verification"
            description = 'Check whether dependency licenses are listed in licenses.yml'
        }

        def updateLicenses = project.task('updateLicenses').doLast {
            initialize(project)

            def notDocumented = dependencyLicenses.notListedIn(librariesYaml)
            LicenseToolsExtension ext = extension(project)

            notDocumented.each { libraryInfo ->
                def text = generateLibraryInfoText(libraryInfo)
                project.file(ext.licensesYaml).append("\n${text}")
            }
        }

        def generateLicenseTxt = project.task('generateLicenseTxt').doLast {
            initialize_NoncheckExist(project)
            writeLicenseTxt(project)
        }
        def generateLicensePage = project.task('generateLicensePage').doLast {
            initialize(project)
            generateLicensePage(project)
        }
        generateLicensePage.dependsOn('checkLicenses')

        def generateLicenseJson = project.task('generateLicenseJson').doLast {
            initialize(project)
            generateLicenseJson(project)
        }
        generateLicenseJson.dependsOn('checkLicenses')

        project.tasks.findByName("check").dependsOn('checkLicenses')
    }

    private static LicenseToolsExtension extension(Project project) {
        project.extensions.findByType(LicenseToolsExtension)
    }

    private Map<String, List<LibraryInfo>> collectLicenseCheckResult(Project project) {
        def notDocumented = dependencyLicenses.notListedIn(librariesYaml)
        def notInDependencies = librariesYaml.notListedIn(dependencyLicenses)
        def licensesNotMatched = dependencyLicenses.licensesNotMatched(librariesYaml)

        return [
            notDocumented: notDocumented,
            notInDependencies: notInDependencies,
            licensesNotMatched: licensesNotMatched
        ]
    }

    private void logLicenseCheckResult(Project project, Map<String, List<LibraryInfo>> result) {
        LicenseToolsExtension ext = extension(project)

        if (!result.notDocumented.empty) {
            project.logger.warn("# Libraries not listed in ${ext.licensesYaml}:")
            result.notDocumented.each { libraryInfo ->
                project.logger.warn(generateLibraryInfoText(libraryInfo))
            }
        }

        if (!result.notInDependencies.empty) {
            project.logger.warn("# Libraries listed in ${ext.licensesYaml} but not in dependencies:")
            result.notInDependencies.each { libraryInfo ->
                project.logger.warn("- artifact: ${libraryInfo.artifactId}\n")
            }
        }

        if (!result.licensesNotMatched.empty) {
            project.logger.warn("# Licenses not matched with pom.xml in dependencies:")
            result.licensesNotMatched.each { libraryInfo ->
                project.logger.warn("- artifact: ${libraryInfo.artifactId}\n  license: ${libraryInfo.license}")
            }
        }
    }

    private void writeLicenseTxt(Project project) {
        def notDocumented = dependencyLicenses.notListedIn(librariesYaml)
        int idx = 1

        LicenseToolsExtension ext = extension(project)
        def outputFile = project.file(ext.outputTxt)
        outputFile.write(LICENSE_TXT_HEADER_FIELDS.join('\t') + '\n')
        outputFile.append(LICENSE_TXT_TEMPLATE_FIELDS.join('\t') + '\t')
        notDocumented.each { libraryInfo ->
            outputFile.append("\n${generateLibraryInfoTextWithVersion(libraryInfo, idx++)}")
        }

        project.logger.warn("Generated 'android_dependency_output.txt' outputs file in ${outputFile.absolutePath}")
    }

    private static void mergeLibraryMetadata(LibraryInfo target, LibraryInfo candidate) {
        if (!target) {
            return
        }
        target.license = target.license ?: candidate.license
        target.filename = candidate.filename ?: target.filename
        target.artifactId = candidate.artifactId ?: target.artifactId
        target.url = target.url ?: candidate.url
    }

    void initialize(Project project) {
        LicenseToolsExtension ext = extension(project)
        loadLibrariesYaml(project.file(ext.licensesYaml))
        loadDependencyLicenses(
                project,
                ext.ignoredGroups,
                ext.runtimeConfigurationName
        )
    }
    void initialize_NoncheckExist(Project project) {
        LicenseToolsExtension ext = extension(project)
        loadDependencyLicenses(
                project,
                ext.ignoredGroups,
                ext.runtimeConfigurationName
        )
    }

    void loadLibrariesYaml(File licensesYaml) {
        if (!licensesYaml.exists()) {
            return
        }

        def libraries = loadYaml(licensesYaml)
        for (lib in libraries) {
            def libraryInfo = LibraryInfo.fromYaml(lib)
            librariesYaml.add(libraryInfo)
        }
    }


    void loadDependencyLicenses(
            Project project,
            Set<String> ignoredGroups,
            String runtimeConfigurationName = 'releaseRuntimeClasspath'
    ) {
        resolveProjectDependencies(project, runtimeConfigurationName).each { ResolvedArtifactResult artifact ->

            def componentId =
                    artifact.id.componentIdentifier

            if (!(componentId instanceof
                    ModuleComponentIdentifier)) {
                return
            }

            String group = componentId.group
            String module = componentId.module
            String version = componentId.version

            if (!group ||
                    !module ||
                    !version ||
                    version == 'unspecified') {
                return
            }

            if (ignoredGroups.contains(group)) {
                return
            }

            String dependencyDesc =
                    "${group}:${module}:${version}"

            LibraryInfo libraryInfo = new LibraryInfo()

            try {
                libraryInfo.artifactId =
                        ArtifactId.parse(dependencyDesc)

                /*
                * Resolved JAR or AAR file.
                */
                libraryInfo.filename = artifact.file
            } catch (IllegalArgumentException e) {
                project.logger.info(
                        "Unsupported dependency: ${dependencyDesc}"
                )
                return
            }

            Dependency pomDependency =
                    project.dependencies.create(
                            "${dependencyDesc}@pom"
                    )

            Configuration pomConfiguration =
                    project.configurations.detachedConfiguration(
                            pomDependency
                    )

            File pomFile

            try {
                Set<File> pomFiles = pomConfiguration.resolve()

                if (pomFiles.isEmpty()) {
                    project.logger.warn(
                            "Unable to retrieve POM for ${dependencyDesc}"
                    )
                } else {
                    pomFile = pomFiles.first()
                }
            } catch (Exception e) {
                project.logger.warn(
                        "Unable to retrieve license for " +
                                "${dependencyDesc}: ${e.message}"
                )
            }

            /*
            * Keep the dependency in the result even if its POM or license
            * metadata cannot be retrieved. Artifact resolution already
            * succeeded, so omitting it here would create an incomplete list.
            */
            dependencyLicenses.add(libraryInfo)

            if (pomFile == null) {
                return
            }

            try {
                XmlSlurper slurper =
                        new XmlSlurper(true, false)

                slurper.setErrorHandler(
                        new DefaultHandler()
                )

                def xml = slurper.parse(pomFile)

                libraryInfo.libraryName =
                        xml.name.text().trim()

                libraryInfo.url =
                        xml.url.text().trim()

                xml.licenses.license.each { license ->
                    if (!libraryInfo.license) {
                        libraryInfo.license =
                                license.name.text().trim()

                        libraryInfo.licenseUrl =
                                license.url.text().trim()
                    }
                }
            } catch (Exception e) {
                project.logger.warn(
                        "Unable to parse POM license metadata for " +
                                "${dependencyDesc}: ${e.message}"
                )
            }
        }
    }



    Map<String, ?> loadYaml(File yamlFile) {
        return yaml.load(yamlFile.text) as Map<String, ?> ?: [:]
    }

    void generateLicensePage(Project project) {
        def ext = project.extensions.getByType(LicenseToolsExtension)

        def noLicenseLibraries = new ArrayList<LibraryInfo>()
        def content = new StringBuilder()

        librariesYaml.each { libraryInfo ->
            if (shouldSkipLibrary(project, libraryInfo, 'generateLicensePage')) {
                return
            }

            def dependencyInfo = dependencyLicenses.find(libraryInfo.artifactId)
            if (dependencyInfo) {
                mergeLibraryMetadata(libraryInfo, dependencyInfo)
            }
            try {
                content.append(Templates.buildLicenseHtml(libraryInfo));
            } catch (NotEnoughInformationException e) {
                noLicenseLibraries.add(e.libraryInfo)
            }
        }

        assertEmptyLibraries(noLicenseLibraries)
        writeAssetFile(project, ext.outputHtml, Templates.wrapWithLayout(content))
    }

    static String generateLibraryInfoTextWithVersion(LibraryInfo libraryInfo,int idx) {
        def text = new StringBuffer()

        String idText = idx.toString()
        text.append("${idText}\t") // ID

        text.append("build.gradle\t") // Source path

        text.append("${libraryInfo.artifactId.group}:${libraryInfo.artifactId.name}\t") // OSS Name (groupId:artifactId)

        if (libraryInfo.artifactId.version) {
            text.append("${libraryInfo.artifactId.version}\t") // OSS Version
        } else {
            text.append("N/A\t")
        }

        String originalLicense = libraryInfo.license ?: ''
        String normalizedLicense = originalLicense.replace(',', '')
        text.append("${normalizedLicense}\t") // License Name

        text.append("https://mvnrepository.com/artifact/${libraryInfo.artifactId.withSlash()}\t") // Download Location

        if (libraryInfo.url) {
            text.append("${libraryInfo.url}\t") // Homepage Url
        } else {
            text.append("https://mvnrepository.com/artifact/${libraryInfo.artifactId.group}/${libraryInfo.artifactId.name}\t")
        }

        if (libraryInfo.copyrightHolder) {
            text.append("${libraryInfo.copyrightHolder}\t") // Copyright
        } else {
            text.append("\t")
        }

        if (libraryInfo.licenseUrl) {
            text.append("${libraryInfo.licenseUrl}\t") // Homepage Url
        } else {
            text.append("\t")
        }

        return text.toString().trim()
    }
    static String generateLibraryInfoText(LibraryInfo libraryInfo) {
        def text = new StringBuffer()
        text.append("- artifact: ${libraryInfo.artifactId.withWildcardVersion()}\n")
        text.append("  name: ${libraryInfo.name ?: "#NAME#"}\n")
        text.append("  copyrightHolder: ${libraryInfo.copyrightHolder ?: "#COPYRIGHT_HOLDER#"}\n")
        text.append("  license: ${libraryInfo.license ?: "#LICENSE#"}\n")
        if (libraryInfo.licenseUrl) {
            text.append("  licenseUrl: ${libraryInfo.licenseUrl ?: "#LICENSEURL#"}\n")
        }
        if (libraryInfo.url) {
            text.append("  url: ${libraryInfo.url ?: "#URL#"}\n")
        }
        return text.toString().trim()
    }

    void generateLicenseJson(Project project) {
        def ext = project.extensions.getByType(LicenseToolsExtension)
        def noLicenseLibraries = new ArrayList<LibraryInfo>()

        def json = new JsonBuilder()
        def librariesArray = []

        librariesYaml.each { libraryInfo ->
            if (shouldSkipLibrary(project, libraryInfo, 'generateLicenseJson')) {
                return
            }

            def dependencyInfo = dependencyLicenses.find(libraryInfo.artifactId)
            if (dependencyInfo) {
                mergeLibraryMetadata(libraryInfo, dependencyInfo)
            }
            try {
                Templates.assertLicenseAndStatement(libraryInfo)
                librariesArray << libraryInfo
            } catch (NotEnoughInformationException e) {
                noLicenseLibraries.add(e.libraryInfo)
            }
        }

        assertEmptyLibraries(noLicenseLibraries)

        json {
            libraries librariesArray.collect { l ->
                return [
                    notice: l.notice,
                    copyrightHolder: l.copyrightHolder,
                    copyrightStatement: l.copyrightStatement,
                    license: l.license,
                    licenseUrl: l.licenseUrl,
                    normalizedLicense: l.normalizedLicense,
                    year: l.year,
                    url: l.url,
                    libraryName: l.libraryName,
                    artifactId: [
                        name: l.artifactId.name,
                        group: l.artifactId.group,
                        version: l.artifactId.version,
                    ]
                ]
            }
        }

        writeAssetFile(project, ext.outputJson, json.toString())
    }

    private static boolean shouldSkipLibrary(Project project, LibraryInfo libraryInfo, String taskName) {
        if (!libraryInfo.skip) {
            return false
        }

        project.logger.info("${taskName}: skip ${libraryInfo.name}")
        return true
    }

    private static void writeAssetFile(Project project, String outputFileName, String content) {
        def assetsDir = project.file("src/main/assets")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }

        project.logger.info("render ${assetsDir}/${outputFileName}")
        project.file("${assetsDir}/${outputFileName}").write(content)
    }

    static void assertEmptyLibraries(ArrayList<LibraryInfo> noLicenseLibraries) {
        if (noLicenseLibraries.empty) return
        StringBuilder message = new StringBuilder()
        message.append("Not enough information for:\n")
        message.append("---\n")
        noLicenseLibraries.each { libraryInfo ->
            message.append("- artifact: ${libraryInfo.artifactId}\n")
            message.append("  name: ${libraryInfo.name}\n")
            if (!libraryInfo.license) {
                message.append("  license: #LICENSE#\n")
            }
            if (!libraryInfo.copyrightStatement) {
                message.append("  copyrightHolder: #AUTHOR# (or authors: [...])\n")
                message.append("  year: #YEAR# (optional)\n")
            }
        }
        throw new RuntimeException(message.toString())
    }

    private Configuration resolveRuntimeConfiguration(
            Project project,
            String runtimeConfigurationName = 'releaseRuntimeClasspath'
    ) {
        String targetName = runtimeConfigurationName
        if (targetName == null || targetName.trim().isEmpty()) {
            targetName = 'releaseRuntimeClasspath'
        }

        Configuration configuration =
                project.configurations.findByName(targetName)

        if (configuration == null) {
            throw new GradleException(
                    "Configuration not found: ${project.path}:${targetName}"
            )
        }

        if (!configuration.canBeResolved) {
            throw new GradleException(
                    "Configuration cannot be resolved: ${project.path}:${configuration.name}"
            )
        }

        return configuration
    }

    Set<ResolvedArtifactResult> resolveProjectDependencies(
            Project project,
            String runtimeConfigurationName = 'releaseRuntimeClasspath'
    ) {
        Map<String, ResolvedArtifactResult> uniqueDependencies =
                new LinkedHashMap<>()

        Configuration configuration =
                resolveRuntimeConfiguration(project, runtimeConfigurationName)

        project.logger.lifecycle(
                "Resolving configuration: " +
                        "${project.path}:${configuration.name}"
        )

        def artifactView =
                configuration.incoming.artifactView { view ->
                    view.lenient = true

                    view.componentFilter { componentId ->
                        componentId instanceof ModuleComponentIdentifier
                    }
                }

        Set<ResolvedArtifactResult> artifacts =
                artifactView.artifacts.artifacts

        artifacts.each { ResolvedArtifactResult artifact ->
            def componentId =
                    artifact.id.componentIdentifier

            if (!(componentId instanceof ModuleComponentIdentifier)) {
                return
            }

            String group = componentId.group
            String module = componentId.module
            String version = componentId.version

            if (!group ||
                    !module ||
                    !version ||
                    version == 'unspecified') {
                return
            }

            String dependencyDesc =
                    "${group}:${module}:${version}"

            uniqueDependencies.putIfAbsent(
                    dependencyDesc,
                    artifact
            )
        }

        return new LinkedHashSet<ResolvedArtifactResult>(
                uniqueDependencies.values()
        )
    }
}
