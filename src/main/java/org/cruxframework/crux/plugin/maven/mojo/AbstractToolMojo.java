/*
 * Copyright 2015 cruxframework.org.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.cruxframework.crux.plugin.maven.mojo;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.cruxframework.crux.plugin.maven.ClasspathBuilder;
import org.cruxframework.crux.plugin.maven.ClasspathBuilderException;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public abstract class AbstractToolMojo extends AbstractMojo
{
	@Component(role = ClasspathBuilder.class)
	protected ClasspathBuilder classpathBuilder;


	@Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
	private Map<String, Artifact> pluginArtifactMap;

	/**
	 * The maven project descriptor
	 */
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	/**
	 * Build the classpath for the specified scope
	 *
	 * @param scope Artifact.SCOPE_COMPILE or Artifact.SCOPE_TEST
	 * @return a collection of dependencies as Files for the specified scope.
	 * @throws MojoExecutionException if classPath building failed
	 */
	public Collection<File> getClasspath(String scope) throws MojoExecutionException
	{
		try
		{
			Collection<File> files = classpathBuilder.buildClasspathList(getProject(), scope, getProjectArtifacts(), isGenerator());

			if (getLog().isDebugEnabled())
			{
				getLog().debug("Execution classpath :");
				for (File f : files)
				{
					getLog().debug("   " + f.getAbsolutePath());
				}
			}
			return files;
		}
		catch (ClasspathBuilderException e)
		{
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	public Set<Artifact> getProjectArtifacts()
    {
		if (getLog().isDebugEnabled())
		{
			getLog().debug("Project Artifacts:");
			for (Artifact a : project.getArtifacts())
			{
				getLog().debug("   " + a.getArtifactId());
			}
		}
		
        return project.getArtifacts();
    }

	protected MavenProject getProject()
	{
		return project;
	}

	/**
     * Whether to use processed resources and compiled classes ({@code false}), or raw resources ({@code true }).
     */
	protected boolean isGenerator()
    {
	    return false;
    }
}
