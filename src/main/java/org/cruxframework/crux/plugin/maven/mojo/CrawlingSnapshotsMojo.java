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
import java.io.PrintWriter;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;
import org.cruxframework.crux.plugin.maven.shell.JavaCommandException;
import org.cruxframework.crux.tools.crawling.CrawlingTool;
import org.cruxframework.crux.tools.server.JettyDevServer;

/**
 * Generate an HTML snapshot for each provided application fragment. It is used to provide information
 * for search engines, like google or bing be able to index information contained on crux pages.
 * @author Thiago da Rosa de Bustamante
 */
@Mojo(name = "generate-html-snapshots", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, 
requiresDependencyResolution=ResolutionScope.COMPILE, threadSafe = true)
public class CrawlingSnapshotsMojo extends AbstractShellMojo
{
	/**
     * The directory into which extra, non-deployed files will be written.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}")
    private File appTargetDir;
    
	/**
	 * Location on filesystem where Crux will write output files.
	 */
	@Parameter(property = "snapshots.output.dir", defaultValue = "${project.build.directory}/${project.build.finalName}/WEB-INF/classes/", alias = "SnapshotsOutputDirectory")
	private File snapshotsOutputDir;

	/**
	 * Time to wait for page rendering before takes the snapshot (in miliseconds).
	 */
	@Parameter(property = "snapshots.javascript.time", defaultValue = "2000", alias = "SnapshotsJavascriptTime")
	private int javascriptTime;
	
	/**
	 * The port used by the internal snapshots server .
	 */
	@Parameter(property = "snapshots.server.port", defaultValue = "8765", alias = "SnapshotsServerPort")
	private int snapshotsServerPort;
	
	/**
	 * If true, starts an embedded jetty to run the application that will be target by CrawlingTool build the HMTL snapshots.
	 */
	@Parameter(property = "snapshots.use.embedded.server", defaultValue = "true", alias = "SnapshotsUseEmbeddedServer")
	private boolean snapshotsUseEmbeddedServer;

	/**
	 * If true, the build will be breaked when an error occurs during snapshots generation.
	 */
	@Parameter(property = "snapshots.stop.onerrors", defaultValue = "true", alias = "SnapshotsStopOnErrors")
	private boolean stopSnapshotsOnErrors;
	
	
	/**
	 * Web application root URL.
	 */
	@Parameter(property = "snapshots.application.base.url", alias = "SnapshotsApplicationBaseURL")
	private String snapshotsApplicationBaseURL;

	/**
	 * A list of URLs to generate snapshots.
	 */
	@Parameter
	private List<Target> snapshots;

	private File urlListFile;
	
	@Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
		if ("pom".equals(getProject().getPackaging()))
		{
			getLog().info("HTML Snapshots generation is skipped");
			return;
		}

		if (!snapshotsOutputDir.exists())
		{
			snapshotsOutputDir.mkdirs();
		}

		String appBaseURL = null;
		if (StringUtils.isNotEmpty(snapshotsApplicationBaseURL))
		{
			appBaseURL = snapshotsApplicationBaseURL;
		}
		else if (snapshotsUseEmbeddedServer)
		{
			appBaseURL = "http://localhost:"+snapshotsServerPort+"/"; 
			startEmbeddedServer();
		}
		else
		{
			throw new MojoExecutionException("Can not realize the application base URL. "
				+ "Use snapshotsApplicationBaseURL or snapshotsUseEmbeddedServer configuration property.");
		}
		String urlFileList = createURLFileList();
		if (urlFileList == null)
		{
			getLog().info("No snapshot URL configured. Skipping HTML Snapshots generation...");
		}
		else
		{
			generateSnapshots(urlFileList, appBaseURL);
			if (urlListFile != null && urlListFile.exists())
			{
				urlListFile.delete();
			}
		}
    }

	private void startEmbeddedServer() throws MojoExecutionException
    {
		getLog().info("Starting an embedded server for snapshots generation.");
		JettyDevServer jettyServer = new JettyDevServer();
		
		jettyServer.setBindAddress("localhost");
		jettyServer.setPort(snapshotsServerPort);
		jettyServer.setAppRootDir(appTargetDir);
		
		try
        {
			if (getLog().isDebugEnabled())
			{
				getLog().debug("Application directory ["+appTargetDir.getCanonicalPath()+"]");
			}
	        jettyServer.start(false);
        }
        catch (Exception e)
        {
        	throw new MojoExecutionException("Error running embedded jetty server.", e);
        }
    }

	private void generateSnapshots(String urlFileList, String appBaseURL) throws MojoExecutionException
    {
		getLog().info("Generating HTML Snapshots...");
		JavaCommand cmd = createJavaCommand().setMainClass(CrawlingTool.class.getCanonicalName());
		cmd.addToClasspath(getClasspath(Artifact.SCOPE_COMPILE, true));

		try
		{
			cmd.arg("applicationBaseURL", appBaseURL)
			   .arg("outputDir", snapshotsOutputDir.getCanonicalPath())
			   .arg("urls", urlFileList)
			   .arg("javascriptTime", Integer.toString(javascriptTime))
			   .arg("stopOnErrors", Boolean.toString(stopSnapshotsOnErrors))
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
			throw new MojoExecutionException("Can not read the informed output directory.", e);
		}
    }
	
	private String createURLFileList() throws MojoExecutionException
    {
		if (snapshots != null && snapshots.size() > 0)
		{
			try
			{
				urlListFile = File.createTempFile("cruxCrawlingList", "urls");
				PrintWriter out = new PrintWriter(urlListFile);
				for (Target target : snapshots)
				{
					String utl = target.getPage()+":"+target.getEscapedFragment();
					out.println(utl);
				}
				out.close();
				return urlListFile.getCanonicalPath();
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Error generating the crawling url list", e);
			}
		}
		return null;
    }
	
	public static class Target
	{
		private String page;
		private String escapedFragment;
		
		public String getPage()
		{
			return page;
		}
		public void setPage(String page)
		{
			this.page = page;
		}
		public String getEscapedFragment()
		{
			return escapedFragment;
		}
		public void setEscapedFragment(String escapedFragment)
		{
			this.escapedFragment = escapedFragment;
		}
	}
}
