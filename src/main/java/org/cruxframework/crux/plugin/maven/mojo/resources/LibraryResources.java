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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.cruxframework.crux.core.rebind.screen.widget.WidgetCreator;
import org.cruxframework.crux.core.rebind.screen.widget.declarative.DeclarativeFactory;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;
import org.cruxframework.crux.plugin.maven.shell.JavaCommandException;
import org.cruxframework.crux.tools.widgets.LibraryMapper;

import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaType;
import com.thoughtworks.qdox.model.expression.Constant;
import com.thoughtworks.qdox.model.expression.TypeRef;

/**
 * @author Thiago da Rosa de Bustamante
 * 
 */
public class LibraryResources extends AbstractScannableResourcesHandler
{
	private static final String DECLARATIVE_FACTORY_ANNOTATION = DeclarativeFactory.class.getCanonicalName();
	
	private File factoryMapFile;
	private File dependenciesGenDir;
	private File depsFactoryMapFile;
	private File typeMapFile;
	private File depsTypeMapFile;
	private Properties typeProperties;
	private Properties factoryProperties;

	public LibraryResources(CruxResourcesMojo resourcesMojo)
	{
		super(resourcesMojo, true, false);
		File metaInfFile = new File(getGeneratedResourcesDir(), "META-INF");
		dependenciesGenDir = new File(getGeneratedResourcesDir(), "../deps");
		depsFactoryMapFile = new File(dependenciesGenDir, "META-INF/crux-widgets-factory");
		depsTypeMapFile = new File(dependenciesGenDir, "META-INF/crux-widgets-type");
		factoryMapFile = new File(metaInfFile, "crux-widgets-factory");
		typeMapFile = new File(metaInfFile, "crux-widgets-type");
	}
	
	@Override
	protected boolean generateFullMappingFile() throws MojoExecutionException
	{
		if (!depsFactoryMapFile.exists())
		{
			generateDpendenciesMappinfile();	
		}
		return true;
	}
	
	@Override
	protected void generateIncrementalMappingFile() throws MojoExecutionException
	{
		try
        {
			if (!factoryMapFile.getParentFile().exists())
			{
				factoryMapFile.getParentFile().mkdirs();
			}
	        getFactoryProperties().store(new FileOutputStream(factoryMapFile), "Widget Factories mapping");
	        getTypeProperties().store(new FileOutputStream(typeMapFile), "Widget Types mapping");
        }
        catch (IOException e)
        {
			throw new MojoExecutionException("Error writing dependencies mapping file.", e);
        }
	}

	private Properties getFactoryProperties() throws MojoExecutionException
    {
		if (factoryProperties == null)
		{
			factoryProperties = new Properties();
			try
			{
				factoryProperties.load(new FileInputStream(depsFactoryMapFile));
			}
			catch (IOException e)
			{
				throw new MojoExecutionException("Error reading dependencies mapping file.", e);
			}	
		}
		return factoryProperties;
    }
	
	private Properties getTypeProperties() throws MojoExecutionException
    {
		if (typeProperties == null)
		{
			typeProperties = new Properties();
			try
			{
				typeProperties.load(new FileInputStream(depsTypeMapFile));
			}
			catch (IOException e)
			{
				throw new MojoExecutionException("Error reading dependencies mapping file.", e);
			}	
		}
		return typeProperties;
    }
	
	private void generateDpendenciesMappinfile() throws MojoExecutionException
	{
		JavaCommand cmd = createJavaCommand().setMainClass(LibraryMapper.class.getCanonicalName());
		cmd.addToClasspath(getClasspath(Artifact.SCOPE_COMPILE, false));

		try
		{
			cmd.arg("projectDir", dependenciesGenDir.getCanonicalPath())
			   .arg("-override")
			   .setErr(new StreamConsumer()
			{
				@Override
				public void consumeLine(String line)
				{
					getLog().info(line);
				}
			}).execute();
		}
		catch (JavaCommandException e)
		{
			throw new MojoExecutionException(e.getMessage(), e);
		}
		catch (IOException e)
		{
			throw new MojoExecutionException("Can not write files on the informed output directory", e);
		}	
	}

	@Override
	protected File getCheckFile() throws MojoExecutionException
	{
	    return factoryMapFile;
	}
	
	protected String[] getScannerExpressions() throws MojoExecutionException
	{
		CruxResourcesMojo resourcesMojo = getResourcesMojo();
		return resourcesMojo.getWidgetCreatorExpression();
	}

	protected boolean isElegibleForGeneration(String sourceFile) throws MojoExecutionException
	{
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

	protected void includeChanged(String sourceFile) throws MojoExecutionException
    {
		JavaClass javaClass = getJavaClass(sourceFile);
		for (JavaAnnotation annot: javaClass.getAnnotations())
		{
			if ( annot.getType().getFullyQualifiedName().equals(DECLARATIVE_FACTORY_ANNOTATION))
			{
			    String id = (String) ((Constant) annot.getProperty("id")).getValue();
			    String library = (String)((Constant) annot.getProperty("library")).getValue();
			    JavaType targetWidget = ((TypeRef) annot.getProperty("targetWidget")).getType();
		
			    String widgetType = library + "_" + id;
			    
			    Properties factoryProperties = getFactoryProperties();
			    Properties typeProperties = getTypeProperties();
			    factoryProperties.put(widgetType, javaClass.getFullyQualifiedName());
			    typeProperties.put(targetWidget.getFullyQualifiedName(), widgetType);
			}
		}
    }
}
