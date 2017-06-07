/*
 * Copyright (c) 2015 the authors of j2objc-gradle (see AUTHORS file)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.j2objccontrib.j2objcgradle.tasks

import com.github.j2objccontrib.j2objcgradle.J2objcConfig
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.StaticLibraryBinary
import org.gradle.platform.base.BinaryContainer

/**
 * Uses 'lipo' binary to combine multiple architecture flavors of a library into a
 * single 'fat' library.
 */
@CompileStatic
class PackLibrariesTask extends DefaultTask {

    // Generated ObjC binaries
    @InputFiles
    ConfigurableFileCollection getLibrariesFiles() {
        BinaryContainer bins = (BinaryContainer) project.property('binaries')

        def libs = bins.
                findAll({it instanceof NativeLibraryBinary}).
                findAll({it instanceof StaticLibraryBinary}).
                collect({(NativeLibraryBinary) it}).
                findAll({it.buildType.name.equalsIgnoreCase(buildType)}).
                findAll({it.targetPlatform.architecture.name in getActiveArchs()}).
                collect {((StaticLibraryBinary)it).staticLibraryFile}

        return project.files(libs)
    }

    // Debug or Release
    @Input
    String buildType

    @Input
    List<String> getActiveArchs() { return J2objcConfig.from(project).activeArchs }


    @OutputDirectory
    File getOutputLibDirFile() {
        return project.file("${project.buildDir}/packedBinaries/${project.name}-j2objcStaticLibrary/ios$buildType")
    }


    @TaskAction
    void packLibraries() {
        Utils.requireMacOSX('j2objcPackLibraries task')
        assert buildType in ['Debug', 'Release']

        Utils.projectDelete(project, getOutputLibDirFile())
        getOutputLibDirFile().mkdirs()

        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        try {
            Utils.projectExec(project, stdout, stderr, null, {
                executable 'xcrun'
                args 'lipo'

                args '-create'
                args '-output', project.file("${outputLibDirFile}/lib${project.name}-j2objc.a").absolutePath

                getLibrariesFiles().each { File libFile ->
                    args libFile.absolutePath
                }

                setErrorOutput stdout
                setStandardOutput stderr
            })

        } catch (Exception exception) {  // NOSONAR
            // TODO: match on common failures and provide useful help
            throw exception
        }
    }
}
