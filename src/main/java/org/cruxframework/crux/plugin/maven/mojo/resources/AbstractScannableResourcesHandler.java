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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.Scanner;
import org.cruxframework.crux.plugin.maven.mojo.AbstractResourcesMojo;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public abstract class AbstractScannableResourcesHandler extends AbstractResourcesHandler
{
	private boolean includeResources;

	protected AbstractScannableResourcesHandler(AbstractResourcesMojo resourcesMojo, boolean includeResources)
	{
		super(resourcesMojo);
		this.includeResources = includeResources;
	}

	public void generateMapping() throws MojoExecutionException
	{
		boolean shouldProcessIncrementally = true;
		boolean checkFileExists = getCheckFile().exists();
		if (!checkFileExists)
		{
			if (getLog().isDebugEnabled())
			{
				getLog().debug("Running Full mapping...");
			}
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
			if (getLog().isDebugEnabled())
			{
				getLog().debug("Running Incremental mapping...");
			}
			generateIncrementalMappingFile();
		}
		else if (!checkFileExists && shouldProcessIncrementally)
		{
			if (getLog().isDebugEnabled())
			{
				getLog().debug("Running Incremental mapping...");
			}
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
		return getJavaProjectBuilder().getClassByName(className);
	}

	protected JavaProjectBuilder getJavaProjectBuilder() throws MojoExecutionException
	{
		return getResourcesMojo().getJavaProjectBuilder();
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
			if (!isUptodate(getCheckFile(), sourceFile) && isElegibleForGeneration(source))
			{
				if (getLog().isDebugEnabled())
				{
					getLog().debug("Modified file found: " + sourceFile.getCanonicalPath() + " is newer than " + getCheckFile().getCanonicalPath());
				}
				hasChanges = true;
				break;
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
