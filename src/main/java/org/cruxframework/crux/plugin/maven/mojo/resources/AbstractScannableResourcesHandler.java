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
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public abstract class AbstractScannableResourcesHandler extends AbstractResourcesHandler
{
	protected AbstractScannableResourcesHandler(CruxResourcesMojo resourcesMojo)
	{
		super(resourcesMojo);
	}

	public void generateMapping() throws MojoExecutionException
	{
		if (!getCheckFile().exists())
		{
			generateFullMappingFile();
		}

		List<String> sourceRoots = getProject().getCompileSourceRoots();
		for (String sourceRoot : sourceRoots)
		{
			try
			{
				if (scanAndGenerateMap(new File(sourceRoot)))
				{
					return;
				}
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Failed to generate libraries mapping files", e);
			}
		}

		if (getLog().isDebugEnabled())
		{
			getLog().debug("All library metadata is uptaded. Ignoring generation.");
		}
	}

	protected abstract void generateFullMappingFile() throws MojoExecutionException;

	protected JavaCommand createJavaCommand()
	{
		return getResourcesMojo().createJavaCommand();
	}

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

	protected abstract String[] getScannerExpressions() throws MojoExecutionException;

	protected String getTopLevelClassName(String sourceFile)
	{
		String className = sourceFile.substring(0, sourceFile.length() - 5); // strip ".java"
		return className.replace(File.separatorChar, '.');
	}

	protected abstract boolean isElegibleForGeneration(String source) throws MojoExecutionException;

	protected abstract void includeChanged(String sourceFile) throws MojoExecutionException;

	protected boolean scanAndGenerateMap(File sourceRoot) throws Exception
	{
		if (getLog().isDebugEnabled())
		{
			getLog().debug("Scanning source folder: " + sourceRoot.getCanonicalPath());
		}

//		Scanner scanner = getBuildContext().newScanner(sourceRoot);
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(sourceRoot.getCanonicalPath());
		scanner.setIncludes(getScannerExpressions());
		scanner.scan();
		String[] sources = scanner.getIncludedFiles();
		if (sources.length == 0)
		{
			return false;
		}
		boolean hasChanges = false;
		for (String source : sources)
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
		if (hasChanges)
		{
			generateMapForChanges(sources);
		}
		return hasChanges;
	}

	private void generateMapForChanges(String[] sources) throws Exception
	{
		for (String source : sources)
		{
			if (isElegibleForGeneration(source))
			{
				includeChanged(source);
			}
		}
		generateIncrementalMappingFile();
	}
}
