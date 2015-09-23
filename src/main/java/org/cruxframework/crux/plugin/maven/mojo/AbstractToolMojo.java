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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
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

import com.thoughtworks.qdox.JavaDocBuilder;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public abstract class AbstractToolMojo extends AbstractMojo
{
	@Component(role = ClasspathBuilder.class)
	protected ClasspathBuilder classpathBuilder;

	@Parameter(property = "project.build.sourceEncoding", defaultValue="${project.build.sourceEncoding}")
    private String encoding;

	@Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
	private Map<String, Artifact> pluginArtifactMap;

	/**
	 * The maven project descriptor
	 */
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;	
	
	public JavaDocBuilder createJavaDocBuilder() throws MojoExecutionException
	{
		JavaDocBuilder builder = new JavaDocBuilder();
		builder.setEncoding(encoding);
		builder.getClassLibrary().addClassLoader(getProjectClassLoader(true));
		for (String sourceRoot : getProject().getCompileSourceRoots())
		{
			builder.getClassLibrary().addSourceFolder(new File(sourceRoot));
		}
		return builder;
	}

	/**
	 * Build the classpath for the specified scope
	 *
	 * @param scope Artifact.SCOPE_COMPILE or Artifact.SCOPE_TEST
	 * @return a collection of dependencies as Files for the specified scope.
	 * @throws MojoExecutionException if classPath building failed
	 */
	public Collection<File> getClasspath(String scope, boolean addSources) throws MojoExecutionException
	{
		try
		{
			Collection<File> files = classpathBuilder.buildClasspathList(getProject(), scope, getProjectArtifacts(), isGenerator(), addSources);

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
	
	public MavenProject getProject()
	{
		return project;
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

	public ClassLoader getProjectClassLoader(boolean addSources) throws MojoExecutionException
	{
		return new URLClassLoader(getClassPathURLs(addSources), ClassLoader.getSystemClassLoader());
	}
	
	public URL[] getClassPathURLs(boolean addSources) throws MojoExecutionException
	{
		Collection<File> classpath = getClasspath(Artifact.SCOPE_COMPILE, addSources);
		URL[] urls = new URL[classpath.size()];
		try
		{
			int i = 0;
			for (File classpathFile : classpath)
			{
				urls[i] = classpathFile.toURI().toURL();
				i++;
			}
		}
		catch (MalformedURLException e)
		{
			throw new MojoExecutionException(e.getMessage(), e);
		}
		return urls;
	}

	/**
	 * Whether to use processed resources and compiled classes ({@code false}), or raw resources ({@code true }).
	 */
	public boolean isGenerator()
	{
		return false;
	}
}
