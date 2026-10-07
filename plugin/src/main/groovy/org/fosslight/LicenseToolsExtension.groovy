package org.fosslight

public class LicenseToolsExtension {

    public static String NAME = "licenseTools"

    public File licensesYaml = new File("licenses.yml")

    public File outputHtml = new File("licenses.html")

    public File outputJson = new File("licenses.json")

    public File outputTxt = new File("android_dependency_output.txt")

    public String runtimeConfigurationName = 'releaseRuntimeClasspath'

    /**
     * Groups excluded from the generated dependency report.
     */
    public Set<String> ignoredGroups = new HashSet<>()

    /**
     * Retained for DSL compatibility. Project dependencies are not included in
     * the generated report, so this setting currently has no effect.
     *
     * @deprecated Project dependencies are excluded by the report's component filter.
     */
    @Deprecated
    public Set<String> ignoredProjects = new HashSet<>()
}
