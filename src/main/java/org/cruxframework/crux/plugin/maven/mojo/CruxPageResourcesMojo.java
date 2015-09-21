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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.Scanner;
import org.cruxframework.crux.core.declarativeui.ViewProcessor;
import org.cruxframework.crux.plugin.maven.view.MojoViewLoader;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.w3c.dom.Document;

/**
 * Create a map of application services. It is used by RestServlet and RPCServlet to find out which implementation should be invoked for
 * each requested operation.
 * 
 * @author Thiago da Rosa de Bustamante
 */
@Mojo(name = "process-crux-pages", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class CruxPageResourcesMojo extends AbstractToolMojo
{
	@Component
	private BuildContext buildContext;

	/**
	 * The name of the module that contains the crux pages to be processed.
	 */
	@Parameter(property = "crux.module", alias = "Module", required=true)
	private String module;

	/**
	 * Location on filesystem where Crux will write output files.
	 */
	@Parameter(property = "pages.output.dir", defaultValue = "${project.build.directory}/${project.build.finalName}/", alias = "PagesOutputDirectory")
	private File pagesOutputDir;
	
	/**
	 * The name of the module that contains the crux pages to be processed.
	 */
	@Parameter(property = "crux.view.base.folder", alias = "ViewBaseFolder", defaultValue="client/view")
	private String viewBaseFolder;
	
	public void execute() throws MojoExecutionException
	{
		if ("pom".equals(getProject().getPackaging()))
		{
			getLog().info("HTML generation is skipped");
			return;
		}

		if (viewBaseFolder.endsWith("/"))
		{
			viewBaseFolder = viewBaseFolder.substring(0, viewBaseFolder.length()-1);
		}
		if (viewBaseFolder.startsWith("/"))
		{
			viewBaseFolder = viewBaseFolder.substring(1);
		}

		if (!pagesOutputDir.exists())
		{
			getLog().debug("Creating target output directory " + pagesOutputDir.getAbsolutePath());
			pagesOutputDir.mkdirs();
		}
		
		generatePages();
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

	private void generatePages() throws MojoExecutionException
	{
		List<String> sourceRoots = getProject().getCompileSourceRoots();
		for (String sourceRoot : sourceRoots)
		{
			try
			{
				scanAndGeneratePages(new File(sourceRoot));
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Failed to generate HTML file", e);
			}
		}
		
		List<Resource> resources = getProject().getResources();
		for (Resource resource : resources)
        {
			try
			{
				scanAndGeneratePages(new File(resource.getDirectory()));
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Failed to generate HTML file", e);
			}
	        
        }
	}
	
	private String getModuleBaseFolder()
	{
		return module.substring(0, module.lastIndexOf('.')).replace('.', '/');
	}

	private File getTargetFile(String viewId)
    {
	    if (viewId != null)
	    {
	    	return new File(pagesOutputDir, viewId+".html");
	    }
	    return null;
    }

	private String getViewId(String source)
    {
		String modulePrefix = getModuleBaseFolder()+"/"+viewBaseFolder+"/";
		if (source.startsWith(modulePrefix))
		{
			return source.substring(modulePrefix.length(), source.length()-9);
		}
		
		return null;
    }
	
	private void scanAndGeneratePages(File sourceRoot) throws Exception
	{
		if (getLog().isDebugEnabled())
		{
			getLog().debug("Scanning source folder: "+sourceRoot.getCanonicalPath());
		}

		
		Scanner scanner = buildContext.newScanner(sourceRoot);
		scanner.setIncludes(new String[] { "**/*.crux.xml" });
		scanner.scan();
		String[] sources = scanner.getIncludedFiles();
		if (sources.length == 0)
		{
			return;
		}
		for (String source : sources)
		{
			File sourceFile = new File(sourceRoot, source);
			String viewId = getViewId(source);
			if (!StringUtils.isEmpty(viewId))
			{
				File targetFile = getTargetFile(viewId);
				if (buildContext.isUptodate(targetFile, sourceFile))
				{
					getLog().debug(targetFile.getAbsolutePath() + " is up to date. Generation skipped");
					continue;
				}
				
				getLog().info("Generating HTML page for file " + source);
				targetFile.getParentFile().mkdirs();
				generateHTMLPage(viewId, sourceFile, targetFile);
			}
		}
	}
}