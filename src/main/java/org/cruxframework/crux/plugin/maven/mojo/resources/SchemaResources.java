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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.cruxframework.crux.core.rebind.screen.widget.WidgetCreator;
import org.cruxframework.crux.core.rebind.screen.widget.declarative.DeclarativeFactory;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;
import org.cruxframework.crux.plugin.maven.shell.JavaCommandException;
import org.cruxframework.crux.tools.schema.SchemaGenerator;

import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * @author Thiago da Rosa de Bustamante
 * 
 */
public class SchemaResources extends AbstractScannableResourcesHandler
{
	private static final String DECLARATIVE_FACTORY_ANNOTATION = DeclarativeFactory.class.getCanonicalName();
	
	private File catalogFile;

	public SchemaResources(GenerateSchemasMojo resourcesMojo)
	{
		super(resourcesMojo, true, true);
		catalogFile = new File(getXsdOutputDir(), "crux-catalog.xml");
	}
	
	@Override
	protected boolean generateFullMappingFile() throws MojoExecutionException
	{
		if (!catalogFile.exists())
		{
			generateSchemas();
			return false;
		}
		return true;
	}
	
	@Override
	protected void generateIncrementalMappingFile() throws MojoExecutionException
	{
		generateSchemas();
	}
	@Override
	protected File getCheckFile() throws MojoExecutionException
	{
	    return catalogFile;
	}

	protected String[] getScannerExpressions() throws MojoExecutionException
	{
		GenerateSchemasMojo resourcesMojo = getResourcesMojo();
		String[] expressions = resourcesMojo.getWidgetCreatorExpression();
		expressions = Arrays.copyOf(expressions, expressions.length+1);
		expressions[expressions.length-1] = "**/*.template.xml";
		return expressions;
	}

	protected void includeChanged(String sourceFile) throws MojoExecutionException
    {
		// Nothing to do. We perform a full rebuild for schemas
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
			for (Annotation annot: javaClass.getAnnotations())
			{
				if (annot.getType().getValue().equals(DECLARATIVE_FACTORY_ANNOTATION))
				{
					return true;
				}
			}
		}
		return false;
	}
	
	private void generateSchemas() throws MojoExecutionException
	{
		getLog().info("Generating XSD files...");
		JavaCommand cmd = createJavaCommand().setMainClass(SchemaGenerator.class.getCanonicalName());
		cmd.addToClasspath(getClasspath(Artifact.SCOPE_COMPILE, true));
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

	private File getXsdOutputDir()
    {
		GenerateSchemasMojo resourcesMojo = getResourcesMojo();
	    return resourcesMojo.getXsdOutputDir();
    }

	private boolean isGenerateDoc()
    {
		GenerateSchemasMojo resourcesMojo = getResourcesMojo();
	    return resourcesMojo.isGenerateDoc();
    }
}
