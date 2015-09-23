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
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.cruxframework.crux.core.server.rest.annotation.RestService;
import org.cruxframework.crux.core.utils.FileUtils;
import org.cruxframework.crux.plugin.maven.shell.JavaCommand;
import org.cruxframework.crux.plugin.maven.shell.JavaCommandException;
import org.cruxframework.crux.tools.servicemap.ServiceMapper;

import com.google.gwt.user.client.rpc.RemoteService;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.annotation.AnnotationConstant;

/**
 * @author Thiago da Rosa de Bustamante
 * 
 */
public class ServiceResources extends AbstractScannableResourcesHandler
{
	private static final String REST_SERVICE_ANNOTATION = RestService.class.getCanonicalName();
	private static final String REMOTE_SERVICE_INTERFACE = RemoteService.class.getCanonicalName();
	private File dependenciesGenDir;
	private File depsRestMapFile;
	private File restMapFile;
	private Properties restProperties;
	private File depsRpcMapFile;
	private Properties rpcProperties;
	private File rpcMapFile;

	public ServiceResources(CruxResourcesMojo resourcesMojo)
	{
		super(resourcesMojo);
		File metaInfFile = new File(getGeneratedResourcesDir(), "META-INF");
		dependenciesGenDir = new File(getGeneratedResourcesDir(), "../deps");
		restMapFile = new File(metaInfFile, "crux-rest");
		rpcMapFile = new File(metaInfFile, "crux-remote");
		depsRestMapFile = new File(dependenciesGenDir, "META-INF/crux-rest");
		depsRpcMapFile = new File(dependenciesGenDir, "META-INF/crux-remote");
	}

	@Override
	protected void generateFullMappingFile() throws MojoExecutionException
	{
		if (!depsRestMapFile.exists())
		{
			generateDpendenciesMappinfile();	
		}
	}

	@Override
	protected void generateIncrementalMappingFile() throws MojoExecutionException
	{
		try
        {
			if (!restMapFile.getParentFile().exists())
			{
				restMapFile.getParentFile().mkdirs();
			}
			getRestProperties().store(new FileOutputStream(restMapFile), "Crux RestServices implementations");
	        getRpcProperties().store(new FileOutputStream(rpcMapFile), "Crux RemoteServices implementations");
        }
        catch (IOException e)
        {
			throw new MojoExecutionException("Error writing dependencies mapping file.", e);
        }
		installGeneratedResources(getGeneratedResourcesDir(), getServicesInstallationDir());
	}
	
	@Override
	protected File getCheckFile() throws MojoExecutionException
	{
		return restMapFile;
	}

	protected String[] getScannerExpressions() throws MojoExecutionException
	{
		return getResourcesMojo().getServiceExpression();
	}

	protected boolean isElegibleForGeneration(String sourceFile) throws MojoExecutionException
	{
		JavaClass javaClass = getJavaClass(sourceFile);
		if (!javaClass.isAbstract() && javaClass.isPublic())
		{
			if (!javaClass.isInterface() && javaClass.isPublic() && javaClass.isA( REMOTE_SERVICE_INTERFACE ))
			{
				for (JavaClass intf: javaClass.getImplementedInterfaces())
				{
					if (intf.isA(REMOTE_SERVICE_INTERFACE))
					{
						return true;
					}
				}
			}
			else
			{
				for (Annotation annot: javaClass.getAnnotations())
				{
					if ( annot.getType().getValue().equals(REST_SERVICE_ANNOTATION))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	protected void includeChanged(String sourceFile) throws MojoExecutionException
	{
		JavaClass javaClass = getJavaClass(sourceFile);
		if (!javaClass.isInterface() && javaClass.isPublic() && javaClass.isA( REMOTE_SERVICE_INTERFACE ))
		{
			Properties cruxRpc= getRpcProperties();
			
			for (JavaClass intf: javaClass.getImplementedInterfaces())
			{
				if (intf.isA(REMOTE_SERVICE_INTERFACE))
				{
					cruxRpc.put(intf.getFullyQualifiedName(), javaClass.getFullyQualifiedName());
				}
			}
		}
		else
		{
			for (Annotation annot: javaClass.getAnnotations())
			{
				if ( annot.getType().getValue().equals(REST_SERVICE_ANNOTATION))
				{
					Properties cruxRest = getRestProperties();
					String value = (String) ((AnnotationConstant) annot.getProperty("value")).getValue();
					if (cruxRest.containsKey(value))
					{
						throw new MojoExecutionException("Duplicated rest service [{"+value+"}].");
					}
					cruxRest.put(value, javaClass.getFullyQualifiedName());
				}
			}
		}
	}
	
	private Properties getRestProperties() throws MojoExecutionException
    {
		if (restProperties == null)
		{
			restProperties = new Properties();
			try
			{
				restProperties.load(new FileInputStream(depsRestMapFile));
			}
			catch (IOException e)
			{
				throw new MojoExecutionException("Error reading dependencies mapping file.", e);
			}	
		}
		return restProperties;
    }

	private Properties getRpcProperties() throws MojoExecutionException
    {
		if (rpcProperties == null)
		{
			rpcProperties = new Properties();
			try
			{
				rpcProperties.load(new FileInputStream(depsRpcMapFile));
			}
			catch (IOException e)
			{
				throw new MojoExecutionException("Error reading dependencies mapping file.", e);
			}	
		}
		return rpcProperties;
    }

	private void generateDpendenciesMappinfile() throws MojoExecutionException
    {
	    JavaCommand cmd = createJavaCommand().setMainClass(ServiceMapper.class.getCanonicalName());
		cmd.addToClasspath(getClasspath(Artifact.SCOPE_COMPILE, false));

		try
		{
			cmd.arg("projectDir", dependenciesGenDir.getCanonicalPath())
			   .arg("-override")
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
			throw new MojoExecutionException("Can write files on the informed output directory", e);
		}
    }

	private File getServicesInstallationDir()
    {
	    return getResourcesMojo().getServicesOuputDir();
    }

	public static void installGeneratedResources(File generatedResourcesDir, File servicesOutputDir) throws MojoExecutionException
    {
		try
		{
			if (!servicesOutputDir.exists())
			{
				servicesOutputDir.mkdirs();
			}

			FileUtils.copyFilesFromDir(generatedResourcesDir, servicesOutputDir, "META-INF/crux-re*", null);
		}
		catch (IOException e)
		{
			throw new MojoExecutionException("Can write files on the informed output directory", e);
		}
    }
	
}
