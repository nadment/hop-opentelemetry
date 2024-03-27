/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.hop.opentelemetry;

import org.apache.commons.lang.StringUtils;
import org.apache.hop.core.config.plugin.ConfigPlugin;
import org.apache.hop.core.config.plugin.IConfigOptions;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHasHopMetadataProvider;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.tabs.ConfigPluginOptionsTab;
import org.eclipse.swt.widgets.Control;
import picocli.CommandLine;

@ConfigPlugin(id = "OpenTelemetryOptionPlugin",
    description = "Allows command line editing for OpenTelemetry.",
    category = ConfigPlugin.CATEGORY_CONFIG)
@GuiPlugin(description = "i18n::OpenTelemetryConfig.Description")
public class OpenTelemetryConfigOptions
    implements IConfigOptions, IGuiPluginCompositeWidgetsListener {

  private static OpenTelemetryConfigOptions instance;

  public OpenTelemetryConfigOptions() {
    super();
  }
  
  public OpenTelemetryConfigOptions(OpenTelemetryConfig config) {
    super();
    this.serviceName = config.getServiceName();
    this.endpoint = config.getEndpoint();
  }

  private static final String WIDGET_ID_SERVICE_NAME = "10000-opentelemetry-service-name";
  private static final String WIDGET_ID_ENDPOINT = "10010-opentelemetry-endpoint";
  private static final String WIDGET_ID_TIMEOUT = "10020-opentelemetry-timeout";

  @GuiWidgetElement(id = WIDGET_ID_SERVICE_NAME,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID, type = GuiElementType.TEXT,
      variables = true, label = "i18n::OpenTelemetryConfig.ServiceName.Label",
      toolTip = "i18n::OpenTelemetryConfig.ServiceName.Tooltip")
  private String serviceName;
  
  @GuiWidgetElement(id = WIDGET_ID_ENDPOINT,
      parentId = ConfigPluginOptionsTab.GUI_WIDGETS_PARENT_ID, type = GuiElementType.TEXT,
      variables = true, label = "i18n::OpenTelemetryConfig.Endpoint.Label",
      toolTip = "i18n::OpenTelemetryConfig.Endpoint.Tooltip")
  @CommandLine.Option(names = {"-ote", "--opentelemetry-endpoint"},
      description = "The endpoint to wich send data.")
  private String endpoint;

  /**
   * Gets instance
   *
   * @return value of instance
   */
  public static OpenTelemetryConfigOptions getInstance() {
    if (instance == null) {
      OpenTelemetryConfig config = OpenTelemetryPlugin.loadConfig();
      instance = new OpenTelemetryConfigOptions(config);
    }
    return instance;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }
  
  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public boolean handleOption(ILogChannel log, IHasHopMetadataProvider metadataProvider,
      IVariables variables) throws HopException {
    try {
      OpenTelemetryConfigOptions config = OpenTelemetryConfigOptions.getInstance();
      boolean changed = false;
      if (StringUtils.isNotEmpty(endpoint)) {
        config.setEndpoint(endpoint);
        changed = true;
      }
      return changed;
    } catch (Exception e) {
      throw new HopException("Error handling opentelemetry configuration options", e);
    }
  }

  @Override
  public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {}

  @Override
  public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {}

  @Override
  public void widgetModified(GuiCompositeWidgets compositeWidgets, Control changedWidget,
      String widgetId) {
    persistContents(compositeWidgets);
  }

  @Override
  public void persistContents(GuiCompositeWidgets compositeWidgets) {
    try {

   

      for (String widgetId : compositeWidgets.getWidgetsMap().keySet()) {
        Control control = compositeWidgets.getWidgetsMap().get(widgetId);
        switch (widgetId) {
          case WIDGET_ID_SERVICE_NAME:
            serviceName = ((TextVar) control).getText();            
            break;
          case WIDGET_ID_ENDPOINT:
            endpoint = ((TextVar) control).getText();
            break;
          case WIDGET_ID_TIMEOUT:
            break;
        }
      }

      // Save the configuration...
      OpenTelemetryConfig config = new OpenTelemetryConfig();
      config.setServiceName(serviceName);
      config.setEndpoint(endpoint);
      // config.setTimeout(timeout);
      OpenTelemetryPlugin.saveConfig(config);
     
    } catch (Exception e) {
      new ErrorDialog(HopGui.getInstance().getShell(), "Error", "Error saving option", e);
    }
  }
}
