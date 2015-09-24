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

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.cruxframework.crux.plugin.maven.mojo.AbstractResourcesMojo;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public abstract class AbstractResourcesHandler
{
	private AbstractResourcesMojo resourcesMojo;

	protected AbstractResourcesHandler(AbstractResourcesMojo resourcesMojo)
	{
		this.resourcesMojo = resourcesMojo;
	}
	protected BuildContext getBuildContext()
	{
		return resourcesMojo.getBuildContext();
	}

	protected File getGeneratedResourcesDir()
    {
	    return resourcesMojo.getGeneratedResourcesDir();
    }

	protected Log getLog()
	{
		return resourcesMojo.getLog();
	}

	protected MavenProject getProject()
	{
		return resourcesMojo.getProject();
	}

	@SuppressWarnings("unchecked")
    protected <T extends AbstractResourcesMojo> T getResourcesMojo()
	{
		return (T)resourcesMojo;
	}
}
