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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/**
 * TestTask tests.
 */
class TestTaskTest {

    // Configured with setupTask()
    private Project proj
    private String j2objcHome
    private J2objcConfig j2objcConfig
    private TestTask j2objcTest

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    void setUp() {
        // Mac OS X is the only OS that can run this task
        Utils.setFakeOSMacOSX()
    }

    @After
    void tearDown() {
        Utils.setFakeOSNone()
    }

    @Test
    void testGetTestNames_Simple() {

        // These are nonsense paths for files that don't exist
        proj = ProjectBuilder.builder().build()
        FileCollection srcFiles = proj.files([
                "${proj.rootDir}/src/test/java/com/example/parent/ParentClass.java",
                "${proj.rootDir}/src/test/java/com/example/parent/subdir/SubdirClass.java",
                "${proj.rootDir}/src/test/java/com/example/other/OtherClass.java",
                "${proj.rootDir}/gen/test/java/com/example/generated/OtherClass.java"])
        Properties noPackagePrefixes = new Properties()

        List<String> testNames = TestTask.getTestNames(proj, srcFiles, noPackagePrefixes)

        List<String> expectedTestNames = [
                "com.example.parent.ParentClass",
                "com.example.parent.subdir.SubdirClass",
                "com.example.other.OtherClass",
                "com.example.generated.OtherClass"]

        assert expectedTestNames == testNames
    }

    @Test
    void testGetTestNames_PackagePrefixes() {
        Properties packagePrefixes = new Properties()
        packagePrefixes.setProperty('com.example.parent', 'ParentPrefix')
        packagePrefixes.setProperty('com.example.parent.subdir', 'SubDirPrefix')
        packagePrefixes.setProperty('com.example.other', 'OtherPrefix')
        packagePrefixes.setProperty('com.example.wildcard.*', 'WildcardPrefix')

        // These are nonsense paths for files that don't exist
        proj = ProjectBuilder.builder().build()
        FileCollection srcFiles = proj.files([
                "${proj.rootDir}/src/test/java/com/example/parent/ParentOne.java",
                "${proj.rootDir}/src/test/java/com/example/parent/ParentTwo.java",
                "${proj.rootDir}/src/test/java/com/example/parent/subdir/Subdir.java",
                "${proj.rootDir}/src/test/java/com/example/other/Other.java",
                "${proj.rootDir}/src/test/java/com/example/wildcard/Wildcard.java",
                "${proj.rootDir}/src/test/java/com/example/wildcard/subdir/SubDirWildcard.java",
                "${proj.rootDir}/src/test/java/com/example/noprefix/NoPrefix.java"])


        List<String> testNames = TestTask.getTestNames(proj, srcFiles, packagePrefixes)

        List<String> expectedTestNames = [
                "ParentPrefixParentOne",
                "ParentPrefixParentTwo",
                "SubDirPrefixSubdir",
                "OtherPrefixOther",
                "WildcardPrefixWildcard",
                "WildcardPrefixSubDirWildcard",
                // No package prefix in this case
                "com.example.noprefix.NoPrefix"]

        assert expectedTestNames == testNames
    }

    @Test
    // Adapted from J2ObjC's PackagePrefixesTest.testWildcardToRegex()
    // https://github.com/google/j2objc/blob/master/translator/src/test/java/com/google/devtools/j2objc/util/PackagePrefixesTest.java#L97
    void testWildcardToRegex() throws IOException {
        // Verify normal package name only matches itself.
        String regex = TestTask.wildcardToRegex("com.example.dir");
        assert '^com\\.example\\.dir$' == regex
        assert 'com.example.dir'.matches(regex)
        assert ! 'com example dir'.matches(regex) // Would match if wildcard wasn't converted.
        assert ! 'com.example.dir.annotations'.matches(regex)

        regex = TestTask.wildcardToRegex("foo.bar.*");
        assert '^(foo\\.bar|foo\\.bar\\..*)$' == regex
        assert 'foo.bar'.matches(regex)
        assert 'foo.bar.mumble'.matches(regex)
        assert 'foo.bar'.matches(regex)

        regex = TestTask.wildcardToRegex("foo.\\*.bar");
        assert '^foo\\..*\\.bar$' == regex
        assert 'foo.some.bar'.matches(regex)
        assert 'foo..bar'.matches(regex)
        assert ! 'foobar'.matches(regex)
    }

    private void setupTask() {
        (proj, j2objcHome, j2objcConfig) = TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                applyJavaPlugin: true,
                createJ2objcConfig: true,
                createReportsDir: true,
        ))

        j2objcTest = (TestTask) proj.tasks.create(name: 'j2objcTest', type: TestTask) {
            testBinaryFile = proj.file(proj.file('build/binaries/testJ2objcExecutable/debug/testJ2objc'))
            buildType = 'Debug'
        }
    }

    @Test
    void testTaskAction_Windows() {
        Utils.setFakeOSWindows()
        setupTask()

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Mac OS X is required for j2objcTest task')

        j2objcTest.test()
    }

    @Test
    // Name 'taskAction' as method name 'test' is ambiguous
    void testTaskAction_ZeroTestUnexpected() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)

        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/Debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (0 test)',  // NOTE: 'test' is singular for stdout
                '',  // stderr
                null)

        expectedException.expect(InvalidUserDataException.class)
        // Error:
        expectedException.expectMessage('Unit tests are strongly encouraged with J2ObjC')
        // Workaround:
        expectedException.expectMessage('testMinExpectedTests 0')

        j2objcTest.test()
    }

    @Test
    void testTaskAction_ZeroTestExpected() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        j2objcConfig.testMinExpectedTests = 0

        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/Debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (0 test)',  // NOTE: 'test' is singular for stdout
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    @Test
    void testTaskAction_OneTest() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/Debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (1 test)',  // NOTE: 'test' is singular for stdout
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    @Test
    void testTaskAction_MultipleTests() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/Debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'IGNORE\nOK (2 tests)\nIGNORE',  // stdout
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    @Test(expected=InvalidUserDataException.class)
    void testTaskAction_CantParseOutput() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/Debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (2 testXXXX)',  // NOTE: invalid stdout fails matchRegexOutputs
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    private void demandCopyForJ2objcTest(MockProjectExec mockProjectExec) {
        // Delete test directory
        mockProjectExec.demandDeleteAndReturn(
                proj.file('build/j2objcTest/Debug').absolutePath)
        // Copy main resources, test resources and test binary to test directory
        mockProjectExec.demandMkDirAndReturn(
                proj.file('build/j2objcTest/Debug').absolutePath)
        mockProjectExec.demandCopyAndReturn(
                proj.file('build/j2objcTest/Debug').absolutePath,
                proj.file('src/main/resources').absolutePath,
                proj.file('src/test/resources').absolutePath)
        mockProjectExec.demandCopyAndReturn(
                proj.file('build/j2objcTest/Debug').absolutePath,
                proj.file('build/binaries/testJ2objcExecutable/debug/testJ2objc').absolutePath)
    }

    // TODO: test_Simple() - with some real unit tests

    // TODO: test_Complex() - preferably using real project in src/test/resources
}
