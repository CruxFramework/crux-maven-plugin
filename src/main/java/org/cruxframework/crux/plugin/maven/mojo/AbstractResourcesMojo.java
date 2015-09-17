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

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author thiago
 *
 */
public abstract class AbstractResourcesMojo extends AbstractToolMojo
{
	/**
	 * Location on filesystem where Crux will write generated resource files.
	 */
	@Parameter(property = "gen.resources.dir", defaultValue = "${project.build.directory}/generated-resources/crux", alias = "GenResourcesDir")
	private File generatedResourcesDir;

	/**
	 * If true, override any previous mapping found.
	 */
	@Parameter(property = "override", defaultValue = "false", alias = "Override")
	private boolean override;

	public File getGeneratedResourcesDir()
	{
		return generatedResourcesDir;
	}

	public boolean isOverride()
	{
		return override;
	}
	
	protected File setupGenerateDirectory()
	{
		if (!getGeneratedResourcesDir().exists())
		{
			getLog().debug("Creating target directory " + getGeneratedResourcesDir().getAbsolutePath());
			getGeneratedResourcesDir().mkdirs();
		}
		getLog().debug("Add compile source root " + getGeneratedResourcesDir().getAbsolutePath());
		
		getProject().addCompileSourceRoot(getGeneratedResourcesDir().getAbsolutePath());
		return getGeneratedResourcesDir();
	}
}
