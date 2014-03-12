package org.eluder.coveralls.maven.plugin.jacoco;

/*
 * #[license]
 * coveralls-maven-plugin
 * %%
 * Copyright (C) 2013 - 2014 Tapio Rautonen
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * %[license]
 */

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eluder.coveralls.maven.plugin.AbstractCoverallsMojo;
import org.eluder.coveralls.maven.plugin.CoverageParser;
import org.eluder.coveralls.maven.plugin.domain.SourceLoader;

import java.io.File;

@Mojo(name = "jacoco", threadSafe = false)
public class JaCoCoMojo extends AbstractCoverallsMojo {

    /**
     * File path to JaCoCo coverage file.
     */
    @Parameter(property = "coverageFile", defaultValue = "${project.reporting.outputDirectory}/jacoco/jacoco.xml")
    protected File coverageFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            getLog().info("Skipping Coverall for project with packaging type 'pom'");
            return;
        }
        super.execute();
    }

    @Override
    protected CoverageParser createCoverageParser(final SourceLoader sourceLoader) {
        return new JaCoCoParser(coverageFile, sourceLoader);
    }
}
