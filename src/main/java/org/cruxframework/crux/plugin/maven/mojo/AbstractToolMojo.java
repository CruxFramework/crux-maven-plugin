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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.cruxframework.crux.plugin.maven.ClasspathBuilder;
import org.cruxframework.crux.plugin.maven.ClasspathBuilderException;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.library.ClassLibraryBuilder;
import com.thoughtworks.qdox.library.SortedClassLibraryBuilder;

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

	/**
	 * Location on filesystem where Crux will write generated resource files.
	 */
	@Parameter(property = "gen.sources.dir", defaultValue = "${project.build.directory}/generated-sources/crux")
	private File generatedSourcesDir;

	@Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
	private Map<String, Artifact> pluginArtifactMap;

	@Parameter( defaultValue="${project.build.outputDirectory}")
	private File outputDirectory;	
	
	/**
	 * The maven project descriptor
	 */
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;	
	
	public JavaProjectBuilder createJavaProjectBuilder() throws MojoExecutionException
	{
		ClassLibraryBuilder libraryBuilder = new SortedClassLibraryBuilder(); //or OrderedClassLibraryBuilder() 
		libraryBuilder.appendClassLoader(getProjectClassLoader(true));
		JavaProjectBuilder builder = new JavaProjectBuilder( libraryBuilder );
		builder.setEncoding(encoding);
		for (String sourceRoot : getProject().getCompileSourceRoots())
		{
			libraryBuilder.appendSourceFolder(new File(sourceRoot));
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
	
	public String getEncoding()
	{
		return encoding;
	}

	/**
	 * Retrieve the folder where generated sources will be placed.
	 * @return generated dource dir
	 */
	public File getGeneratedSourcesDir()
	{
		return generatedSourcesDir;
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

	public Set<File> getAllFiles(String includes, String excludes, boolean includeResources) throws IOException
	{
		Set<File> sourceDirs = new HashSet<File>();
		final java.util.List<String> sourceRoots = getProject().getCompileSourceRoots();
		if (sourceRoots != null)
		{
			for (String s : sourceRoots)
			{
				sourceDirs.add(new File(s));
			}
		}
		
		if(includeResources)
		{
			List<Resource> resources = getProject().getResources();
			if (resources != null)
			{
				for (Resource resource : resources)
				{
					sourceDirs.add(new File(resource.getDirectory()));
				}
			}
		}

		Set<File> files = new HashSet<File>();
		for (File sourceRoot : sourceDirs)
		{
			if (getLog().isDebugEnabled())
			{
				getLog().debug("Scanning source folder: " + sourceRoot.getCanonicalPath());
			}
			@SuppressWarnings("unchecked")
			List<File> dirFiles = FileUtils.getFiles(sourceRoot, includes, excludes);
			if (dirFiles != null)
			{
				files.addAll(dirFiles);
			}
		}
		return files;
	}
	
	/**
	 * Whether to use processed resources and compiled classes ({@code false}), or raw resources ({@code true }).
	 */
	public boolean isGenerator()
	{
		return false;
	}
	
	protected File setupGenerateDirectory()
	{
		if (!getGeneratedSourcesDir().exists())
		{
			getLog().debug("Creating target directory " + getGeneratedSourcesDir().getAbsolutePath());
			getGeneratedSourcesDir().mkdirs();
		}
		getLog().debug("Add compile source root " + getGeneratedSourcesDir().getAbsolutePath());

		getProject().addCompileSourceRoot(getGeneratedSourcesDir().getAbsolutePath());
		return getGeneratedSourcesDir();
	}
	
	protected File getOutputDirectory()
	{
		return outputDirectory;
	}
	
	public boolean isUptodate(File target, File source)
	{
		return target != null && target.exists() && source != null && 
			   source.exists() && target.lastModified() > source.lastModified();
	}	
}
