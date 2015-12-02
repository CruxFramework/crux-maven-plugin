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
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.cruxframework.crux.core.declarativeui.ViewProcessor;
import org.cruxframework.crux.plugin.maven.view.MojoViewLoader;
import org.w3c.dom.Document;

/**
 * @author Thiago da Rosa de Bustamante
 * 
 */
public class PageResources extends AbstractResourcesHandler
{
	public PageResources(CruxResourcesMojo resourcesMojo)
    {
		super(resourcesMojo);
    }
	
	protected void generatePages() throws MojoExecutionException
	{
		Set<File> sources;
		try
		{
			sources = getResourcesMojo().getAllFiles("**/*.crux.xml", null, true);
			
			if (sources.size() == 0)
			{
				return;
			}
			
			for (File sourceFile : sources)
			{
				String viewId = getViewId(sourceFile.getCanonicalPath());
				if (!StringUtils.isEmpty(viewId))
				{
					File targetFile = getTargetFile(viewId);
					if (getBuildContext().isUptodate(targetFile, sourceFile))
					{
						getLog().debug(targetFile.getAbsolutePath() + " is up to date. Generation skipped");
						continue;
					}
					
					getLog().info("Generating HTML page for file " + sourceFile.getCanonicalPath());
					targetFile.getParentFile().mkdirs();
					generateHTMLPage(viewId, sourceFile, targetFile);
				}
			}
		}
		catch (Exception e)
		{
			throw new MojoExecutionException("Error generating pages.", e);
		}
	}

	protected String getModuleBaseFolder()
	{
		CruxResourcesMojo resourcesMojo = getResourcesMojo();
		return resourcesMojo.getModuleBaseFolder();
	}

	protected File getPagesOutputDir()
	{
		CruxResourcesMojo resourcesMojo = getResourcesMojo();
		return resourcesMojo.getPagesOutputDir();
	}
	
	protected String getViewBaseFolder()
	{
		CruxResourcesMojo resourcesMojo = getResourcesMojo();
		return resourcesMojo.getViewBaseFolder();
	}

	private boolean generateHTMLPage(String viewId, File sourceFile, File targetFile) throws Exception
	{
		ViewProcessor viewProcessor = new ViewProcessor(new MojoViewLoader());
		Document view = viewProcessor.getView(new FileInputStream(sourceFile), viewId, null);
		FileOutputStream out = new FileOutputStream(targetFile);
		viewProcessor.generateHTML(viewId, view, out);
		out.close();
		return true;
	}

	private File getTargetFile(String viewId)
    {
	    if (viewId != null)
	    {
	    	return new File(getPagesOutputDir(), viewId+".html");
	    }
	    return null;
    }
	
	private String getViewId(String source)
    {
		if (File.separatorChar != '/')
		{
			source = source.replace(File.separatorChar, '/');
		}
		String modulePrefix = getModuleBaseFolder()+"/"+getViewBaseFolder()+"/";
		if (source.startsWith(modulePrefix))
		{
			return source.substring(modulePrefix.length(), source.length()-9);
		}
		
		return null;
    }
}
