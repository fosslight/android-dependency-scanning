package com.lge.android.licensetools

public class LicenseToolsExtension {

    public static String NAME = "licenseTools"

    public File licensesYaml = new File("licenses.yml")

    public File outputHtml = new File("licenses.html")

    public File outputJson = new File("licenses.json")

    public File outputTxt = new File("android_dependency_output.txt")
    public Set<String> ignoredGroups = new HashSet<>()

    public Set<String> ignoredProjects = new HashSet<>()
}
