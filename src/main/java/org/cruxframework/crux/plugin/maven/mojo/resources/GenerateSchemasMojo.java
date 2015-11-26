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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.cruxframework.crux.plugin.maven.mojo.AbstractResourcesMojo;

/**
 * Generate XSD files to validate crux view files, based on libraries and templates
 *  used by the application. 
 * @author Thiago da Rosa de Bustamante
 */
@Mojo(name = "generate-xsds", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, 
		requiresDependencyResolution=ResolutionScope.COMPILE, threadSafe = true)
public class GenerateSchemasMojo extends AbstractResourcesMojo
{
	/**
	 * If true generates also an HTML Documentation for the libraries.
	 */
	@Parameter(property = "xsd.gen.doc", defaultValue = "false")
	private boolean generateDoc;

	/**
	 * The expressions used to identify widgetCreator candidates on project.
	 */
	@Parameter(property = "crux.widget.creator.expression", defaultValue="**/rebind/**/*Factory.java")
	private String widgetCreatorExpression;

	/**
	 * The expressions used to identify widgetCreator candidates on project.
	 */
	@Parameter()
	private String[] widgetCreatorExpressions;
	
	/**
	 * Location on filesystem where Crux will write generated resource files.
	 */
	@Parameter(property = "xsd.output.dir", defaultValue = "${project.build.directory}/xsd")
	private File xsdOutputDir;

	public void execute() throws MojoExecutionException
	{
		if ("pom".equals(getProject().getPackaging()))
		{
			getLog().info("XSD generation is skipped");
			return;
		}

		if (!xsdOutputDir.exists())
		{
			getLog().debug("Creating target directory " + xsdOutputDir.getAbsolutePath());
			xsdOutputDir.mkdirs();
		}

//		setupGenerateDirectory();

		SchemaResources schemaResources = new SchemaResources(this);
		schemaResources.generateMapping();
	}

	public File getXsdOutputDir()
    {
	    return xsdOutputDir;
    }
	
	public boolean isGenerateDoc()
	{
		return generateDoc;
	}
	
	@Override
	public boolean isGenerator()
	{
	    return true;
	}

	protected String[] getWidgetCreatorExpression()
	{
		if (widgetCreatorExpressions == null)
		{
			widgetCreatorExpressions = new String[]{widgetCreatorExpression};
		}
		return widgetCreatorExpressions;
	}

}