package com.lge.android.licensetools

import groovy.json.JsonBuilder
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.xml.sax.helpers.DefaultHandler
import org.yaml.snakeyaml.Yaml

class LicenseToolsPlugin implements Plugin<Project> {

    final yaml = new Yaml()

    final DependencySet librariesYaml = new DependencySet() // based on libraries.yml
    final DependencySet dependencyLicenses = new DependencySet() // based on license plugin's dependency-license.xml

    @Override
    void apply(Project project) {
        project.extensions.add(LicenseToolsExtension.NAME, LicenseToolsExtension)

        def checkLicenses = project.task('checkLicenses').doLast {
            initialize(project)

            def notDocumented = dependencyLicenses.notListedIn(librariesYaml)
            def notInDependencies = librariesYaml.notListedIn(dependencyLicenses)
            def licensesNotMatched = dependencyLicenses.licensesNotMatched(librariesYaml)

            if (notDocumented.empty && notInDependencies.empty && licensesNotMatched.empty) {
                project.logger.info("checkLicenses: ok")
                return
            }

            LicenseToolsExtension ext = project.extensions.findByType(LicenseToolsExtension)

            if (notDocumented.size() > 0) {
                project.logger.warn("# Libraries not listed in ${ext.licensesYaml}:")
                notDocumented.each { libraryInfo ->
                    def text = generateLibraryInfoText(libraryInfo)
                    project.logger.warn(text)
                }
            }

            if (notInDependencies.size() > 0) {
                project.logger.warn("# Libraries listed in ${ext.licensesYaml} but not in dependencies:")
                notInDependencies.each { libraryInfo ->
                    project.logger.warn("- artifact: ${libraryInfo.artifactId}\n")
                }
            }
            if (licensesNotMatched.size() > 0) {
                project.logger.warn("# Licenses not matched with pom.xml in dependencies:")
                licensesNotMatched.each { libraryInfo ->
                    project.logger.warn("- artifact: ${libraryInfo.artifactId}\n  license: ${libraryInfo.license}")
                }
            }
            throw new GradleException("checkLicenses: missing libraries in ${ext.licensesYaml}")
        }

        checkLicenses.configure {
            group = "Verification"
            description = 'Check whether dependency licenses are listed in licenses.yml'
        }

        def updateLicenses = project.task('updateLicenses').doLast {
            initialize(project)

            def notDocumented = dependencyLicenses.notListedIn(librariesYaml)
            LicenseToolsExtension ext = project.extensions.findByType(LicenseToolsExtension)

            notDocumented.each { libraryInfo ->
                def text = generateLibraryInfoText(libraryInfo)
                project.file(ext.licensesYaml).append("\n${text}")
            }
        }

        def generateLicenseTxt = project.task('generateLicenseTxt').doLast {
            initialize_NoncheckExist(project)
            def notDocumented = dependencyLicenses.notListedIn(librariesYaml)
            int idx = 1

            LicenseToolsExtension ext = project.extensions.findByType(LicenseToolsExtension)
            project.file(ext.outputTxt).write("\"ID\"\tSource Name or Path\tOSS Name\tOSS Version\tLicense\tDownload Location\tHomepage\tCopyright Text\tLicense Text\tExclude\tComment\n")
            project.file(ext.outputTxt).append("-\t[Name of the Source File or Path]\t[Name of the OSS used in the Source Code]\t[Version Number of the OSS]\t[License of the OSS. Use SPDX Identifier : https://spdx.org/licenses/]\t[Download URL or a specific location within a VCS for the OSS]\t[Web site that serves as the OSS's home page]\t[The copyright holders of the OSS]\t[License Text of the License. This field can be skipped if the License is in SPDX.]\t[If this OSS is not included in the final version, Exclude]\t")
            notDocumented.each { libraryInfo ->
                def text = generateLibraryInfoTextWithVersion(libraryInfo,idx++)
                project.file(ext.outputTxt).append("\n${text}")
            }

            def outPath = project.file(ext.outputTxt).getAbsolutePath()

            project.logger.warn("Generated 'android_dependency_output.txt' file in ${outPath}")
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

    void initialize(Project project) {
        LicenseToolsExtension ext = project.extensions.findByType(LicenseToolsExtension)
        loadLibrariesYaml(project.file(ext.licensesYaml))
        loadDependencyLicenses(project, ext.ignoredGroups, ext.ignoredProjects)
    }
    void initialize_NoncheckExist(Project project) {
        LicenseToolsExtension ext = project.extensions.findByType(LicenseToolsExtension)
        loadDependencyLicenses(project, ext.ignoredGroups, ext.ignoredProjects)
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

    void loadDependencyLicenses(Project project, Set<String> ignoredGroups, Set<String> ignoredProjects) {
        resolveProjectDependencies(project, ignoredProjects).each { d ->
            if (d.moduleVersion.id.version == "unspecified") {
                return
            }
            if (ignoredGroups.contains(d.moduleVersion.id.group)) {
                return
            }

            def dependencyDesc = "$d.moduleVersion.id.group:$d.moduleVersion.id.name:$d.moduleVersion.id.version"

            def libraryInfo = new LibraryInfo()
            try {
                libraryInfo.artifactId = ArtifactId.parse(dependencyDesc)
                libraryInfo.filename = d.file
                dependencyLicenses.add(libraryInfo)
            } catch (IllegalArgumentException e) {
                project.logger.info("Unsupport dependency: $dependencyDesc")
                return
            }

            Dependency pomDependency = project.dependencies.create("$dependencyDesc@pom")
            Configuration pomConfiguration = project.configurations.detachedConfiguration(pomDependency)

            pomConfiguration.resolve().each {
                project.logger.info("POM: ${it}")
            }

            File pStream
            try {
                pStream = pomConfiguration.resolve().asList().first()
            } catch (Exception e) {
                project.logger.warn("Unable to retrieve license for $dependencyDesc")
                return
            }

            XmlSlurper slurper = new XmlSlurper(true, false)
            slurper.setErrorHandler(new DefaultHandler())
            GPathResult xml = slurper.parse(pStream)

            libraryInfo.libraryName = xml.name.text()
            libraryInfo.url = xml.url.text()

            xml.licenses.license.each {
                if (!libraryInfo.license) {
                    // takes the first license
                    libraryInfo.license = it.name.text().trim()
                    libraryInfo.licenseUrl = it.url.text().trim()
                }
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
            if (libraryInfo.skip) {
                project.logger.info("generateLicensePage: skip ${libraryInfo.name}")
                return
            }

            // merge dependencyLicenses's libraryInfo into librariesYaml's
            def o = dependencyLicenses.find(libraryInfo.artifactId)
            if (o) {
                libraryInfo.license = libraryInfo.license ?: o.license
                libraryInfo.filename = o.filename
                libraryInfo.artifactId = o.artifactId
                libraryInfo.url = libraryInfo.url ?: o.url
            }
            try {
                content.append(Templates.buildLicenseHtml(libraryInfo));
            } catch (NotEnoughInformationException e) {
                noLicenseLibraries.add(e.libraryInfo)
            }
        }

        assertEmptyLibraries(noLicenseLibraries)

        def assetsDir = project.file("src/main/assets")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }

        project.logger.info("render ${assetsDir}/${ext.outputHtml}")
        project.file("${assetsDir}/${ext.outputHtml}").write(Templates.wrapWithLayout(content))
    }

    static String generateLibraryInfoTextWithVersion(LibraryInfo libraryInfo,int idx) {
        def text = new StringBuffer()

        String ID_Str = idx.toString()
        text.append("${ID_Str}\t") // ID

        text.append("build.gralde\t") // Source path

        text.append("${libraryInfo.name}\t") // OSS Name

        if (libraryInfo.artifactId.version) {
            text.append("${libraryInfo.artifactId.version}\t") // OSS Version
        } else {
            text.append("N/A\t")
        }
        text.append("${libraryInfo.license}\t") // License Name

        text.append("https://mvnrepository.com/artifact/${libraryInfo.artifactId.withSlash()}\t") // Download Location

        if (libraryInfo.url) {
            text.append("${libraryInfo.url}\t") // Homepage Url
        } else {
            text.append("https://mvnrepository.com/artifact/${libraryInfo.artifactId.withSlash()}\t")
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
            if (libraryInfo.skip) {
                project.logger.info("generateLicensePage: skip ${libraryInfo.name}")
                return
            }

            // merge dependencyLicenses's libraryInfo into librariesYaml's
            def o = dependencyLicenses.find(libraryInfo.artifactId)
            if (o) {
                libraryInfo.license = libraryInfo.license ?: o.license
                // libraryInfo.filename = o.filename
                libraryInfo.artifactId = o.artifactId
                libraryInfo.url = libraryInfo.url ?: o.url
            }
            try {
                Templates.assertLicenseAndStatement(libraryInfo)
                librariesArray << libraryInfo
            } catch (NotEnoughInformationException e) {
                noLicenseLibraries.add(e.libraryInfo)
            }
        }

        assertEmptyLibraries(noLicenseLibraries)

        def assetsDir = project.file("src/main/assets")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }

        json {
            libraries librariesArray.collect {
                l ->
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
                        // I don't why artifactId won't serialize, and this is the only way
                        // I've found -- vishna
                        artifactId: [
                                name: l.artifactId.name,
                                group: l.artifactId.group,
                                version: l.artifactId.version,
                        ]
                    ]
            }
        }

        project.logger.info("render ${assetsDir}/${ext.outputJson}")
        project.file("${assetsDir}/${ext.outputJson}").write(json.toString())
    }

    static void assertEmptyLibraries(ArrayList<LibraryInfo> noLicenseLibraries) {
        if (noLicenseLibraries.empty) return;
        StringBuilder message = new StringBuilder();
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

    // originated from https://github.com/hierynomus/license-gradle-plugin DependencyResolver.groovy
    Set<ResolvedArtifact> resolveProjectDependencies(Project project, Set<String> ignoredProjects) {
        def subprojects = project.rootProject.subprojects.findAll { Project p -> !ignoredProjects.contains(p.name) }
                .groupBy { Project p -> "$p.group:$p.name:$p.version" }

        List<ResolvedArtifact> runtimeDependencies = []

        project.rootProject.subprojects.findAll { Project p -> !ignoredProjects.contains(p.name) }.each { Project subproject ->
            runtimeDependencies << subproject.configurations.all.findAll { Configuration c ->
                // compile|implementation|api, release(Compile|Implementation|Api), releaseProduction(Compile|Implementation|Api), and so on.
                c.name.matches(/^(?!releaseUnitTest)(?:release\w*)?([cC]ompile|[cC]ompileOnly|[iI]mplementation|[aA]pi)$/)
            }.collect {
                Configuration copyConfiguration = it.copyRecursive()
                copyConfiguration.setCanBeResolved(true)
                copyConfiguration.resolvedConfiguration.lenientConfiguration.artifacts
            }.flatten() as List<ResolvedArtifact>
        }

        runtimeDependencies = runtimeDependencies.flatten()
        runtimeDependencies.removeAll([null])

        def seen = new HashSet<String>()
        def dependenciesToHandle = new HashSet<ResolvedArtifact>()
        runtimeDependencies.each { ResolvedArtifact d ->
            String dependencyDesc = "$d.moduleVersion.id.group:$d.moduleVersion.id.name:$d.moduleVersion.id.version"
            if (!seen.contains(dependencyDesc)) {
                dependenciesToHandle.add(d)

                Project subproject = subprojects[dependencyDesc]?.first()
                if (subproject) {
                    dependenciesToHandle.addAll(resolveProjectDependencies(subproject))
                }
            }
        }
        return dependenciesToHandle
    }
}
