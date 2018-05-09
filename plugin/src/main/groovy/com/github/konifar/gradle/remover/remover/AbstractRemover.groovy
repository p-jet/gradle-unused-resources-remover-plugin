package com.github.konifar.gradle.remover.remover

import com.github.konifar.gradle.remover.util.ColoredLogger
import org.gradle.api.Project
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting

abstract class AbstractRemover {

    /**
     * directory/file name to find files like drawable, dimen, string
     */
    final String fileType

    /**
     * Resource name to check its existence like @`string`/app_name, $.`string`/app_name
     */
    final String resourceName

    /**
     * Search pattern
     * ex) theme should specified to Type.STYLE
     */
    final SearchPattern.Type type

    final List<String> moduleSrcDirs = []

    String scanTargetFileTexts = ""

    // Extension settings
    List<String> excludeNames = []
    boolean dryRun = false

    AbstractRemover(String fileType, String resourceName, SearchPattern.Type type) {
        this.fileType = fileType
        this.resourceName = resourceName
        this.type = type
    }

    abstract void removeEach(File resDirFile)

    /**
     * @param target is file name or attribute name
     * @return pattern string to grep src
     */
    @VisibleForTesting
    GString createSearchPattern(String target) {
        return SearchPattern.create(resourceName, target, type)
    }

    void remove(Project project, UnusedResourcesRemoverExtension extension) {
        this.dryRun = extension.dryRun
        this.excludeNames = extension.excludeNames

        moduleSrcDirs.clear()
        moduleSrcDirs.addAll(
                project.rootProject.allprojects
                        .findAll { it.name != project.rootProject.name }
                        .collect { "${it.projectDir.path}/src" }
        )
        scanTargetFileTexts = ""
        moduleSrcDirs.
                collect { new File(it) }.
                findAll { it.exists() }.
                each { srcDirFile ->
                    srcDirFile.eachDirRecurse { dir ->
                        dir.eachFileMatch(~/(.*\.xml)|(.*\.kt)|(.*\.java)/) { f ->
                            scanTargetFileTexts += f.text.replaceAll('\n', '').replaceAll(' ', '')
                        }
                    }
                }

        ColoredLogger.log "[${fileType}] ======== Start ${fileType} checking ========"

        moduleSrcDirs.each {
            String moduleSrcName = it - "${project.rootProject.projectDir.path}/" - "/src"
            ColoredLogger.log "[${fileType}] ${moduleSrcName}"

            File resDirFile = new File("${it}/main/res")
            if (resDirFile.exists()) {
                removeEach(resDirFile)
            }
        }
    }

    @VisibleForTesting
    static boolean isPatternMatched(String fileText, GString pattern) {
        return fileText =~ pattern
    }

    boolean checkTargetTextMatches(String targetText) {
        def pattern = createSearchPattern(targetText)
        return isPatternMatched(scanTargetFileTexts, pattern)
    }

    boolean isMatchedExcludeNames(String filePath) {
        return excludeNames.count {
            return filePath.contains(it)
        } > 0
    }

    @Override
    String toString() {
        return "fileType: ${fileType}, resourceName: ${resourceName}, type: ${type}"
    }
}
