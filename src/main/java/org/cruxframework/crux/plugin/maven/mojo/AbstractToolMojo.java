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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.cruxframework.crux.plugin.maven.ClasspathBuilder;
import org.cruxframework.crux.plugin.maven.ClasspathBuilderException;
import org.cruxframework.crux.plugin.maven.shell.ClassPathProcessor;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public abstract class AbstractToolMojo extends AbstractMojo
{
	@Component(role = ClasspathBuilder.class)
	protected ClasspathBuilder classpathBuilder;

	/**
	 * Extra JVM arguments that are passed to the Crux-Maven generated scripts (for compiler, shell, etc - typically use -Xmx512m here, or
	 * -XstartOnFirstThread, etc).
	 */
	@Parameter(property = "xsd.extraJvmArgs", defaultValue = "-Xmx512m")
	private String extraJvmArgs;

	/**
	 * Option to specify the jvm (or path to the java executable) to use with the forking scripts. For the default, the jvm will be the same
	 * as the one used to run Maven.
	 *
	 * @since 1.1
	 */
	@Parameter(property = "xsd.jvm")
	private String jvm;

	@Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
	private Map<String, Artifact> pluginArtifactMap;

	/**
	 * The maven project descriptor
	 */
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	/**
	 * Forked process execution timeOut. Usefull to avoid maven to hang in continuous integration server.
	 */
	@Parameter
	private int timeOut;

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

	/**
	 * @param timeOut the timeOut to set
	 */
	public void setTimeOut(int timeOut)
	{
		this.timeOut = timeOut;
	}

	protected JavaCommand createJavaCommand()
	{
		return new JavaCommand().setLog(getLog()).setJvm(getJvm()).setJvmArgs(getJvmArgs()).setTimeOut(timeOut)
		    .addClassPathProcessors(new ClassPathProcessor()
		    {
			    @Override
			    public void postProcessClassPath(List<File> files)
			    {
				    AbstractToolMojo.this.postProcessClassPath(files);
			    }
		    });
	}

	protected String getExtraJvmArgs()
	{
		return extraJvmArgs;
	}
	
	protected String getJvm()
	{
		return jvm;
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
	
	/**
	 * hook to post-process the dependency-based classpath
	 */
	protected void postProcessClassPath(Collection<File> classpath)
	{
		// Nothing to do in most case
	}

	private List<String> getJvmArgs()
	{
		List<String> extra = new ArrayList<String>();
		String userExtraJvmArgs = getExtraJvmArgs();
		if (userExtraJvmArgs != null)
		{
			try
			{
				return new ArrayList<String>(Arrays.asList(CommandLineUtils.translateCommandline(StringUtils
				    .removeDuplicateWhitespace(userExtraJvmArgs))));
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		return extra;
	}
}
