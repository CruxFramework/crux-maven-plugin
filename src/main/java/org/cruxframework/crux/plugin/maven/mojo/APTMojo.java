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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.StringUtils;
import org.cruxframework.crux.core.annotation.processor.CruxAnnotationProcessor;
import org.cruxframework.crux.core.annotation.processor.RestServiceProcessor;
import org.cruxframework.crux.tools.annotation.processor.LibraryProcessor;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
@Mojo(name = "apt", defaultPhase = LifecyclePhase.COMPILE, 
		requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class APTMojo extends AbstractToolMojo
{
	private static final String JAVA_FILES = "**/*.java";
	
	@Parameter
	private String sourceVersion;

	@Parameter(property = "apt.incremental", defaultValue="true")
	private boolean incremental;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if ("pom".equals(getProject().getPackaging()))
		{
			getLog().info("HTML generation is skipped");
			return;
		}
		long before = System.currentTimeMillis();

		setupGenerateDirectory();

		Set<String> newSourceFiles = process();
		if (newSourceFiles != null) // changed
		{
			updateReportFile(newSourceFiles);
		}
		
		long after = System.currentTimeMillis();

		if (getLog().isDebugEnabled())
		{
			getLog().debug("Annotation processors executed [" + (after - before) + "ms]");
		}
	}

	/**
	 * Retrieve the compilation source version
	 * 
	 * @return
	 */
	public String getSourceVersion()
	{
		if (StringUtils.isEmpty(sourceVersion))
		{
			sourceVersion = getLatestSupportedVersion();
			if (getLog().isInfoEnabled())
			{
				getLog().info("source version not informed. Using [" + sourceVersion + "] as default.");
			}
		}
		return sourceVersion;
	}

	public Set<String> process() throws MojoExecutionException
    {
		boolean hasChanges = false;

		Set<File> modifiedSources = new HashSet<File>();
		Set<String> allSources = new HashSet<String>();

		if (scanModifiedSourceFiles(modifiedSources, allSources))
		{
			hasChanges = true;
		}

		List<String> previousSourceFiles = getPreviousSourceFiles();

		if (!incremental || hasDeletedResources(allSources, previousSourceFiles))
		{
			runAPT();
		}
		else if (hasChanges)
		{
			runAPT(modifiedSources, true);
		}
		else
		{
			return null;
		}
		return allSources;
    }
	
	public void runAPT() throws MojoExecutionException
	{
		Set<File> files;
		try
		{
			files = getAllFiles(JAVA_FILES, null, false);
		}
		catch (IOException e)
		{
			throw new MojoExecutionException("Error retrieving the source files list.", e);
		}
		runAPT(files, false);
	}
	
	public void runAPT(Set<File> files, boolean incremental) throws MojoExecutionException
	{
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

		if (compiler == null)
		{
			getLog().error("JVM is not suitable for processing annotation! ToolProvider.getSystemJavaCompiler() is null.");
			return;
		}

		Charset charset = getCharset();

		final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null,
		    (charset == null) ? Charset.defaultCharset() : charset);

		final List<JavaFileObject> allSources = new java.util.ArrayList<JavaFileObject>();
		if (files != null && !files.isEmpty())
		{
			for (JavaFileObject f : fileManager.getJavaFileObjectsFromFiles(files))
			{
				allSources.add(f);
			}
		}

		if (allSources.isEmpty())
		{
			getLog().warn("no source file(s) detected! Processor task will be skipped");
			return;
		}

		final DiagnosticListener<JavaFileObject> dl = getDiagnosticListener();

		List<String> options = getOptions(incremental);

		if (getLog().isDebugEnabled())
		{
			for (JavaFileObject source : allSources)
			{
				getLog().debug(String.format("compilation source: %s", source.getName()));
			}
			
			for (String option : options)
			{
				getLog().debug(String.format("javac option: %s", option));
			}
		}

		CompilationTask task = compiler.getTask(new PrintWriter(System.out), fileManager, dl, options, null, allSources);
		
		task.setProcessors(getProcessors());

		// Perform the compilation task.
		if (!task.call())
		{
			throw new MojoExecutionException("error during compilation");
		}
	}

	private ArrayList<CruxAnnotationProcessor> getProcessors()
	{
		ArrayList<CruxAnnotationProcessor> processors = new ArrayList<CruxAnnotationProcessor>();
		processors.add(new RestServiceProcessor());
		processors.add(new LibraryProcessor());
		return processors;
	}

	protected List<String> getPreviousSourceFiles() throws MojoExecutionException
	{
		BufferedReader reader = null;
		try
		{
			File checkFile = getCheckFile();
			
			if (!checkFile.exists())
			{
				return null;
			}
			
			List<String> sourceFiles = new ArrayList<String>();
			reader = new BufferedReader(new FileReader(checkFile));
			String line;
			while ((line = reader.readLine()) != null)
			{
				String sourceFile = line.trim();
				if (!sourceFile.startsWith("#"))
				{
					sourceFiles.add(sourceFile);
				}
			}
			
			return sourceFiles;
		}
		catch (Exception e)
		{
			throw new MojoExecutionException("Error checking source files modifications", e);
		}
		finally
		{
			if (reader != null)
			{
				try
                {
	                reader.close();
                }
                catch (IOException e)
                {
                	//IGNORE
                }
			}
		}
	}
	
	protected boolean hasDeletedResources(Set<String> allSources, List<String> previousSourceFiles) throws MojoExecutionException
	{
		if (previousSourceFiles != null)
		{
			for (String source : previousSourceFiles)
			{
				if (!allSources.contains(source))
				{
					return true;
				}
			}
		}
		return false;
	}

	protected boolean scanModifiedSourceFiles(Set<File> modifiedSources, Set<String> allSources) throws MojoExecutionException
	{
		try
		{
			boolean hasChanges = false;
			File checkFile = getCheckFile();

			Set<File> files = getAllFiles(JAVA_FILES, null, false);		
			for (File sourceFile : files)
			{
				allSources.add(sourceFile.getCanonicalPath());
				if (!isUptodate(checkFile, sourceFile))
				{
					if (getLog().isDebugEnabled())
					{
						getLog().debug("Modified file found: " + sourceFile.getCanonicalPath() + " is newer than " + checkFile.getCanonicalPath());
					}
					hasChanges = true;
					modifiedSources.add(sourceFile);
				}
			}
			return hasChanges;
		}
		catch (Exception e)
		{
			throw new MojoExecutionException("Failed to generate mapping files", e);
		}
	}
	
	private Charset getCharset()
	{
		Charset charset = null;
		;

		if (getEncoding() != null)
		{
			try
			{
				charset = Charset.forName(getEncoding());
			}
			catch (IllegalCharsetNameException ex1)
			{
				getLog().warn(String.format("the given charset name [%s] is illegal!. default is used", getEncoding()));
				charset = null;
			}
			catch (UnsupportedCharsetException ex2)
			{
				getLog().warn(String.format("the given charset name [%s] is unsupported!. default is used", getEncoding()));
				charset = null;
			}
		}
		return charset;
	}

	private File getCheckFile()
    {
		return new File(getProject().getBuild().getOutputDirectory(), "META-INF/crux-plugin/report");
    }

	private DiagnosticListener<JavaFileObject> getDiagnosticListener()
	{
		final DiagnosticListener<JavaFileObject> dl = new DiagnosticListener<JavaFileObject>()
		{
			public void report(Diagnostic<? extends JavaFileObject> diagnostic)
			{
				final Kind kind = diagnostic.getKind();

				if (Kind.ERROR == kind)
				{
					getLog().error(String.format("APT: %s", diagnostic));
				}
				else if (Kind.MANDATORY_WARNING == kind || Kind.WARNING == kind)
				{
					getLog().warn(String.format("APT: %s", diagnostic));
				}
				else if (Kind.NOTE == kind)
				{
					getLog().info(String.format("APT: %s", diagnostic));
				}
				else if (Kind.OTHER == kind)
				{

					getLog().info(String.format("APT: %s", diagnostic));
				}
			}
		};
		return dl;
	}

	private static String getLatestSupportedVersion()
	{
		String JDK_VERSION = SourceVersion.latestSupported().toString();
		switch (JDK_VERSION)
		{
			case "RELEASE_8":
				return "8";
			case "RELEASE_7":
				return "7";
			case "RELEASE_6":
				return "6";
			case "RELEASE_5":
				return "5";
			case "RELEASE_4":
				return "1.4";
			case "RELEASE_3":
				return "1.3";
			case "RELEASE_2":
				return "1.2";
			case "RELEASE_1":
				return "1.1";
			default:
				return JDK_VERSION.substring("RELEASE_".length());
		}
	}
	
	private List<String> getOptions(boolean incremental) throws MojoExecutionException
	{
		List<String> options = new ArrayList<String>(10);

		options.add("-cp");
		Collection<File> classpath = getClasspath(Artifact.SCOPE_COMPILE, false);
		List<String> path = new ArrayList<String>(classpath.size());
		for (File file : classpath)
		{
			path.add(file.getAbsolutePath());
		}
		options.add(StringUtils.join(path.iterator(), File.pathSeparator));

		options.add("-proc:only");

		options.add("-source");
		options.add(getSourceVersion());

		if (incremental)
		{
			options.add("-A"+CruxAnnotationProcessor.CRUX_APT_INCREMENTAL+"=true");
		}
		
		options.add("-A"+CruxAnnotationProcessor.CRUX_RUN_APT+"=true");
		
		options.add("-d");
		options.add(getOutputDirectory().getPath());

		options.add("-s");
		options.add(getGeneratedSourcesDir().getPath());
		return options;
	}

	private void updateReportFile(Collection<String> previousListSource) throws MojoExecutionException
    {
		File checkFile = getCheckFile();
		
		if (!checkFile.exists())
		{
			checkFile.getParentFile().mkdirs();
		}
		
		try
        {
	        PrintWriter printer = new PrintWriter(checkFile);
	        printer.println("# executed at: " + System.currentTimeMillis());
	        
	        for (String sourceFile : previousListSource)
            {
		        printer.println(sourceFile);
            }
	        
	        printer.close();
        }
        catch (Exception e)
        {
	        throw new MojoExecutionException("Error generating report file", e);
        }
    }
}