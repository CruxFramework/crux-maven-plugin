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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.cruxframework.crux.core.utils.FileUtils;
import org.cruxframework.crux.plugin.maven.mojo.AbstractResourcesMojo;

/**
 * Create a map of application services. It is used by RestServlet and RPCServlet to find out which implementation should be invoked for
 * each requested operation.
 * 
 * @author Thiago da Rosa de Bustamante
 */
@Mojo(name = "process-crux-resources", defaultPhase = LifecyclePhase.COMPILE, 
requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class CruxResourcesMojo extends AbstractResourcesMojo
{
	/**
	 * The name of the module that contains the crux pages to be processed.
	 */
	@Parameter(property = "crux.module", required=true)
	private String module;

	/**
	 * Location on filesystem where Crux will write output files.
	 */
	@Parameter(property = "pages.output.dir", defaultValue = "${project.build.directory}/${project.build.finalName}/")
	private File pagesOutputDir;

	/**
	 * The expressions used to identify service candidates on project.
	 */
	@Parameter(property = "crux.service.expression", defaultValue="**/*Service.java")
	private String serviceExpression;
	
	/**
	 * The expressions used to identify service candidates on project.
	 */
	@Parameter()
	private String[] serviceExpressions;

	/**
	 * Location on filesystem where Crux will write service metadata files.
	 */
	@Parameter(property = "services.output.dir", defaultValue = "${project.build.directory}/${project.build.finalName}/")
	private File servicesOutputDir;


	/**
	 * If true, plugin will automatically update META-INF/ library metadata files.
	 */
	@Parameter(property = "crux.sync.library.metadata", defaultValue="true")
	private boolean syncLibraryMetadata;

	/**
	 * If true, plugin will automatically update META-INF/ service metadata files.
	 */
	@Parameter(property = "crux.sync.service.metadata", defaultValue="true")
	private boolean syncServiceMetadata;

	/**
	 * The name of the module that contains the crux pages to be processed.
	 */
	@Parameter(property = "crux.targetWebXml", defaultValue="${project.build.directory}/${project.build.finalName}/WEB-INF/web.xml")
	private File targetWebXml;

	/**
	 * The name of the module that contains the crux pages to be processed.
	 */
	@Parameter(property = "crux.view.base.folder", defaultValue="client/view")
	private String viewBaseFolder;
	
	/**
	 * The name of the module that contains the crux pages to be processed.
	 */
	@Parameter(property = "crux.webXml", defaultValue="${basedir}/src/main/webapp/WEB-INF/web.xml")
	private File webXml;

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
	
	
	public void execute() throws MojoExecutionException
	{
		if ("pom".equals(getProject().getPackaging()))
		{
			getLog().info("HTML generation is skipped");
			return;
		}

		setupGenerateDirectory();
		setuptPageFolders();

		updateWebXml();

		PageResources pageResources = new PageResources(this); 
		pageResources.generatePages();

		if (syncLibraryMetadata)
		{
			LibraryResources libraryResources = new LibraryResources(this);
			libraryResources.generateMapping();
		}
		if (syncServiceMetadata)
		{
			ServiceResources serviceResources = new ServiceResources(this);
			serviceResources.generateMapping();
		}
	}

	@Override
	public boolean isGenerator()
	{
	    return true;
	}

	protected String getModuleBaseFolder()
	{
		return module.substring(0, module.lastIndexOf('.')).replace('.', '/');
	}

	protected File getPagesOutputDir()
	{
		return pagesOutputDir;
	}
	
	protected String[] getServiceExpression()
	{
		if (serviceExpressions == null)
		{
			serviceExpressions = new String[]{serviceExpression};
		}
		return serviceExpressions;
	}

	protected File getServicesOuputDir()
	{
		return servicesOutputDir;
	}

	protected String getViewBaseFolder()
	{
		return viewBaseFolder;
	}
	
	protected String[] getWidgetCreatorExpression()
	{
		if (widgetCreatorExpressions == null)
		{
			widgetCreatorExpressions = new String[]{widgetCreatorExpression};
		}
		return widgetCreatorExpressions;
	}
	
	protected void updateWebXml() throws MojoExecutionException
	{
		if(targetWebXml == null || webXml.lastModified() > targetWebXml.lastModified())
		{
			try
			{
				targetWebXml.getParentFile().mkdirs();
				FileUtils.copyFile(webXml, targetWebXml);
			}
			catch (IOException e)
			{
				throw new MojoExecutionException("Error updating web.xml file", e);
			}
		}
	}
	
	private void setuptPageFolders()
	{
		viewBaseFolder = viewBaseFolder.replace('\\', '/');
		
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
	}
}