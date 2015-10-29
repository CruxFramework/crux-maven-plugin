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
package org.cruxframework.crux.plugin.maven.mojo.resources;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.Scanner;
import org.cruxframework.crux.plugin.maven.mojo.AbstractResourcesMojo;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public abstract class AbstractScannableResourcesHandler extends AbstractResourcesHandler
{
	private boolean fullSourcesSearch;
	private boolean includeResources;

	protected AbstractScannableResourcesHandler(AbstractResourcesMojo resourcesMojo, boolean fullSourcesSearch, boolean includeResources)
	{
		super(resourcesMojo);
		this.fullSourcesSearch = fullSourcesSearch;
		this.includeResources = includeResources;
	}

	public void generateMapping() throws MojoExecutionException
	{
		boolean shouldProcessIncrementally = true;
		boolean CheckFileExists = getCheckFile().exists();
		if (!CheckFileExists)
		{
			shouldProcessIncrementally = generateFullMappingFile();
		}

		if (!shouldProcessIncrementally)
		{
			return;
		}
		boolean hasChanges = false;
		Set<String> sources = new HashSet<String>();
		
		List<String> sourceRoots = getProject().getCompileSourceRoots();
		for (String sourceRoot : sourceRoots)
		{
			try
			{
				if (scanAndGenerateMap(new File(sourceRoot), sources))
				{
					hasChanges = true;
				}
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Failed to generate mapping files", e);
			}
		}
		if (includeResources)
		{
			List<Resource> resources = getProject().getResources();

			for (Resource resource : resources)
			{
				try
				{
					if (scanAndGenerateMap(new File(resource.getDirectory()), sources))
					{
						hasChanges = true;
					}
				}
				catch (Exception e)
				{
					throw new MojoExecutionException("Failed to generate mapping files", e);
				}
			}
		}

		if (hasChanges)
		{
			includeChanges(sources);
			generateIncrementalMappingFile();
		}
		else if (!CheckFileExists)
		{
			generateIncrementalMappingFile();
		}
	}

	protected JavaCommand createJavaCommand()
	{
		return getResourcesMojo().createJavaCommand();
	}

	protected abstract boolean generateFullMappingFile() throws MojoExecutionException;

	protected abstract void generateIncrementalMappingFile() throws MojoExecutionException;

	protected abstract File getCheckFile() throws MojoExecutionException;

	protected Collection<File> getClasspath(String scopeCompile, boolean addSources) throws MojoExecutionException
	{
		return getResourcesMojo().getClasspath(scopeCompile, addSources);
	}

	protected JavaClass getJavaClass(String sourceFile) throws MojoExecutionException
	{
		String className = getTopLevelClassName(sourceFile);
		return getJavaDocBuilder().getClassByName(className);
	}

	protected JavaDocBuilder getJavaDocBuilder() throws MojoExecutionException
	{
		return getResourcesMojo().getJavaDocBuilder();
	}

	protected Scanner getScanner(File sourceRoot) throws IOException
	{
		Scanner scanner;
		if (fullSourcesSearch)
		{
			DirectoryScanner dirScanner = new DirectoryScanner();
			dirScanner.setBasedir(sourceRoot.getCanonicalPath());
			scanner = dirScanner;

		}
		else
		{
			scanner = getBuildContext().newScanner(sourceRoot);
		}
		return scanner;
	}

	protected abstract String[] getScannerExpressions() throws MojoExecutionException;

	protected String getTopLevelClassName(String sourceFile)
	{
		String className = sourceFile.substring(0, sourceFile.length() - 5); // strip ".java"
		return className.replace(File.separatorChar, '.');
	}

	protected abstract void includeChanged(String sourceFile) throws MojoExecutionException;

	protected abstract boolean isElegibleForGeneration(String source) throws MojoExecutionException;

	protected boolean scanAndGenerateMap(File sourceRoot, Set<String> sources) throws Exception
	{
		if (getLog().isDebugEnabled())
		{
			getLog().debug("Scanning source folder: " + sourceRoot.getCanonicalPath());
		}

		Scanner scanner = getScanner(sourceRoot);
		scanner.setIncludes(getScannerExpressions());
		scanner.scan();
		String[] includedSources = scanner.getIncludedFiles();
		if (includedSources.length == 0)
		{
			return false;
		}
		boolean hasChanges = false;
		for (String source : includedSources)
		{
			File sourceFile = new File(sourceRoot, source);
			if (!getBuildContext().isUptodate(getCheckFile(), sourceFile))
			{
				if (isElegibleForGeneration(source))
				{
					hasChanges = true;
					break;
				}
			}
		}
		sources.addAll(Arrays.asList(includedSources));
		return hasChanges;
	}

	private void includeChanges(Set<String> sources) throws MojoExecutionException 
	{
		for (String source : sources)
		{
			if (isElegibleForGeneration(source))
			{
				includeChanged(source);
			}
		}
	}
}
