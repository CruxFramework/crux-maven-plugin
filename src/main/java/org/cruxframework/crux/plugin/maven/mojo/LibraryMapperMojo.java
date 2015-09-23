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

import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;
import org.cruxframework.crux.plugin.maven.shell.JavaCommandException;
import org.cruxframework.crux.tools.widgets.LibraryMapper;

/**
 * Create a map of application services. It is used by RestServlet and RPCServlet to find out which implementation should be invoked for
 * each requested operation.
 * 
 * @author Thiago da Rosa de Bustamante
 */
@Mojo(name = "library-mapper", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class LibraryMapperMojo extends AbstractResourcesMojo
{
	public void execute() throws MojoExecutionException
	{
		if ("pom".equals(getProject().getPackaging()))
		{
			getLog().info("Library mapping is skipped");
			return;
		}

		setupGenerateDirectory();

		libraryMapping();
	}

	private void libraryMapping() throws MojoExecutionException
	{
		getLog().info("Mapping Libraries...");
		JavaCommand cmd = createJavaCommand().setMainClass(LibraryMapper.class.getCanonicalName());
		cmd.addToClasspath(getClasspath(Artifact.SCOPE_COMPILE, true));

		try
		{
			cmd.arg("projectDir", getGeneratedResourcesDir().getCanonicalPath());
			if (isOverride())
			{
				cmd.arg("-override");
			}

			cmd.setErr(new StreamConsumer()
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
			throw new MojoExecutionException("Can write files on the informed output directory", e);
		}
	}
}