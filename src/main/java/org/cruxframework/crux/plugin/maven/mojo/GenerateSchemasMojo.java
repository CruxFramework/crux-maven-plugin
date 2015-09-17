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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;
import org.cruxframework.crux.plugin.maven.shell.JavaCommandException;
import org.cruxframework.crux.tools.schema.SchemaGenerator;

/**
 * Generate XSD files to validate crux view files, based on libraries and templates
 *  used by the application. 
 * @author Thiago da Rosa de Bustamante
 */
@Mojo(name = "generate-xsds", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, 
		requiresDependencyResolution=ResolutionScope.COMPILE, threadSafe = true)
public class GenerateSchemasMojo extends AbstractToolMojo
{
	/**
	 * Location on filesystem where Crux will write output files.
	 */
	@Parameter(property = "xsd.output.dir", defaultValue = "${project.basedir}/xsd", alias = "XsdOutputDirectory")
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
			xsdOutputDir.mkdirs();
		}

		generateSchemas();
	}

	private void generateSchemas() throws MojoExecutionException
	{
		getLog().info("Generating XSD files...");
		JavaCommand cmd = createJavaCommand().setMainClass(SchemaGenerator.class.getCanonicalName());
		cmd.addToClasspath(getClasspath(Artifact.SCOPE_COMPILE));

		try
		{
			cmd.arg(getProject().getBasedir().getCanonicalPath())
			   .arg(xsdOutputDir.getCanonicalPath())
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
			throw new MojoExecutionException("Can not read the informed output directory", e);
		}
	}
}