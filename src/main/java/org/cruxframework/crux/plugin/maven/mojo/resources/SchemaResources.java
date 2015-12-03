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
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.Scanner;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.cruxframework.crux.core.rebind.screen.widget.WidgetCreator;
import org.cruxframework.crux.core.rebind.screen.widget.declarative.DeclarativeFactory;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;
import org.cruxframework.crux.plugin.maven.shell.JavaCommandException;
import org.cruxframework.crux.tools.schema.SchemaGenerator;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * @author Thiago da Rosa de Bustamante
 * 
 */
public class SchemaResources extends AbstractResourcesHandler
{
	private static final String DECLARATIVE_FACTORY_ANNOTATION = DeclarativeFactory.class.getCanonicalName();
	
	private File catalogFile;

	public SchemaResources(GenerateSchemasMojo resourcesMojo)
	{
		super(resourcesMojo);
		catalogFile = new File(getXsdOutputDir(), "crux-catalog.xml");
	}
	
	public void generateMapping() throws MojoExecutionException
	{
		boolean checkFileExists = getCheckFile().exists();
		if (!checkFileExists)
		{
			generateSchemas();
			return;
		}

		boolean hasChanges = false;
		List<String> sourceRoots = getProject().getCompileSourceRoots();
		for (String sourceRoot : sourceRoots)
		{
			try
			{
				if (hasChanges(new File(sourceRoot)))
				{
					hasChanges = true;
				}
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Failed to generate mapping files", e);
			}
		}
		List<Resource> resources = getProject().getResources();

		for (Resource resource : resources)
		{
			try
			{
				if (hasChanges(new File(resource.getDirectory())))
				{
					hasChanges = true;
				}
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Failed to generate mapping files", e);
			}
		}

		if (hasChanges)
		{
			generateSchemas();
		}
	}
	
	protected void generateSchemas() throws MojoExecutionException
	{
		getLog().info("Generating XSD files...");
		JavaCommand cmd = getResourcesMojo().createJavaCommand().setMainClass(SchemaGenerator.class.getCanonicalName());
		cmd.addToClasspath(getResourcesMojo().getClasspath(Artifact.SCOPE_COMPILE, true));
		cmd.addToClasspath(new File(getProject().getBuild().getOutputDirectory()));
		
		try
		{
			cmd.arg(getProject().getBasedir().getCanonicalPath());
			if (isGenerateDoc())
			{
				cmd.arg("-generateDoc");
			}
			
			cmd.arg(getXsdOutputDir().getCanonicalPath())
			   .setErr(new StreamConsumer()
			{
				@Override
				public void consumeLine(String line)
				{
					getLog().info(line);
				}
			})
			   .execute();
		}
		catch (JavaCommandException e)
		{
			throw new MojoExecutionException(e.getMessage(), e);
		}
		catch (IOException e)
		{
			throw new MojoExecutionException("Can not write on the informed output directory", e);
		}
	}

	protected File getCheckFile() throws MojoExecutionException
	{
	    return catalogFile;
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
	
	protected String[] getScannerExpressions() throws MojoExecutionException
	{
		GenerateSchemasMojo resourcesMojo = getResourcesMojo();
		String[] expressions = resourcesMojo.getWidgetCreatorExpression();
		expressions = Arrays.copyOf(expressions, expressions.length+1);
		expressions[expressions.length-1] = "**/*.template.xml";
		return expressions;
	}

	protected String getTopLevelClassName(String sourceFile)
	{
		String className = sourceFile.substring(0, sourceFile.length() - 5); // strip ".java"
		return className.replace(File.separatorChar, '.');
	}

	protected File getXsdOutputDir()
    {
		GenerateSchemasMojo resourcesMojo = getResourcesMojo();
	    return resourcesMojo.getXsdOutputDir();
    }

	protected boolean hasChanges(File sourceRoot) throws Exception
	{
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
		return hasChanges;
	}

	protected boolean isElegibleForGeneration(String sourceFile) throws MojoExecutionException
	{
		if (sourceFile.endsWith(".template.xml"))
		{
			return true;
		}
		
		JavaClass javaClass = getJavaClass(sourceFile);
		if (!javaClass.isAbstract() && javaClass.isPublic() && javaClass.isA(WidgetCreator.class.getCanonicalName()))
		{
			for (JavaAnnotation annot: javaClass.getAnnotations())
			{
				if (annot.getType().getFullyQualifiedName().equals(DECLARATIVE_FACTORY_ANNOTATION))
				{
					return true;
				}
			}
		}
		return false;
	}

	protected boolean isGenerateDoc()
    {
		GenerateSchemasMojo resourcesMojo = getResourcesMojo();
	    return resourcesMojo.isGenerateDoc();
    }
}
