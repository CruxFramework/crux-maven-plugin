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
package org.cruxframework.crux.plugin.maven.view;

import java.util.List;

import org.cruxframework.crux.core.declarativeui.template.TemplateLoader;
import org.cruxframework.crux.core.declarativeui.view.ViewException;
import org.cruxframework.crux.core.declarativeui.view.ViewLoader;
import org.cruxframework.crux.tools.scanner.template.Templates;
import org.w3c.dom.Document;

import com.google.gwt.dev.resource.Resource;

/**
 * @author Thiago da Rosa de Bustamante
 *
 */
public class MojoViewLoader implements ViewLoader
{
	@Override
    public TemplateLoader getTemplateLoader()
    {
	    return new TemplateLoader()
		{
			@Override
			public Document getTemplate(String library, String id)
			{
				return Templates.getTemplate(library, id);
			}
		};
    }

	@Override
    public Resource getView(String id) throws ViewException
    {
	    return null;
    }

	@Override
    public List<String> getViews()
    {
	    return null;
    }
}
