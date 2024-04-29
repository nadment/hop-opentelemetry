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

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.tab.GuiTab;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.ConstUi;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.configuration.ConfigurationPerspective;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

@GuiPlugin(description = "i18n::OpenTelemetryConfig.Description")
public class OpenTelemetryConfigTab {
  private static final Class<?> PKG = OpenTelemetryConfigTab.class; // For Translator

  public OpenTelemetryConfigTab() {
    super();
  }

  private Text wServiceName;
  private Text wEndpoint;
  private TableView wHeaders;

  @GuiTab(
      id = "10300-config-perspective-opentelemetry-tab",
      parentId = ConfigurationPerspective.CONFIG_PERSPECTIVE_TABS,
      description = "OpenTelemetry configuration")
  public void addConfigOpenTelemetryTab(CTabFolder wTabFolder) {
    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    CTabItem tabItem = new CTabItem(wTabFolder, SWT.NONE);
    tabItem.setFont(GuiResource.getInstance().getFontDefault());
    tabItem.setText(BaseMessages.getString(PKG, "OpenTelemetryConfig.Title"));
    tabItem.setImage(
        GuiResource.getInstance()
            .getImage(
                "opentelemetry.svg",
                this.getClass().getClassLoader(),
                ConstUi.SMALL_ICON_SIZE,
                ConstUi.SMALL_ICON_SIZE));

    Composite wComposite = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wComposite);
    FormLayout varsCompLayout = new FormLayout();
    varsCompLayout.marginWidth = PropsUi.getFormMargin();
    varsCompLayout.marginHeight = PropsUi.getFormMargin();
    wComposite.setLayout(varsCompLayout);

    // Service name
    //
    Label wlServiceName = new Label(wComposite, SWT.RIGHT);
    wlServiceName.setText(BaseMessages.getString(PKG, "OpenTelemetryConfig.ServiceName.Label"));
    wlServiceName.setToolTipText(
        BaseMessages.getString(PKG, "OpenTelemetryConfig.ServiceName.Tooltip"));
    PropsUi.setLook(wlServiceName);
    FormData fdlServiceName = new FormData();
    fdlServiceName.left = new FormAttachment(0, 0);
    fdlServiceName.right = new FormAttachment(middle, -margin);
    fdlServiceName.top = new FormAttachment(0, margin);
    wlServiceName.setLayoutData(fdlServiceName);

    wServiceName = new Text(wComposite, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wServiceName);
    FormData fdServiceName = new FormData();
    fdServiceName.left = new FormAttachment(middle, 0);
    fdServiceName.right = new FormAttachment(100, 0);
    fdServiceName.top = new FormAttachment(wlServiceName, 0, SWT.CENTER);
    wServiceName.setLayoutData(fdServiceName);

    // Endpoint
    //
    Label wlEndpoint = new Label(wComposite, SWT.RIGHT);
    wlEndpoint.setText(BaseMessages.getString(PKG, "OpenTelemetryConfig.Endpoint.Label"));
    wlEndpoint.setToolTipText(BaseMessages.getString(PKG, "OpenTelemetryConfig.Endpoint.Tooltip"));
    PropsUi.setLook(wlEndpoint);
    FormData fdlEndpoint = new FormData();
    fdlEndpoint.left = new FormAttachment(0, 0);
    fdlEndpoint.right = new FormAttachment(middle, -margin);
    fdlEndpoint.top = new FormAttachment(wServiceName, margin);
    wlEndpoint.setLayoutData(fdlEndpoint);

    wEndpoint = new Text(wComposite, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wEndpoint);
    FormData fdEndpoint = new FormData();
    fdEndpoint.left = new FormAttachment(middle, 0);
    fdEndpoint.right = new FormAttachment(100, 0);
    fdEndpoint.top = new FormAttachment(wlEndpoint, 0, SWT.CENTER);
    wEndpoint.setLayoutData(fdEndpoint);

    // Headers
    //
    Label wlHeaders = new Label(wComposite, SWT.RIGHT);
    wlHeaders.setText(BaseMessages.getString(PKG, "OpenTelemetryConfig.Headers.Label"));
    wlHeaders.setToolTipText(BaseMessages.getString(PKG, "OpenTelemetryConfig.Headers.Tooltip"));
    PropsUi.setLook(wlHeaders);
    FormData fdlHeaders = new FormData();
    fdlHeaders.left = new FormAttachment(0, 0);
    fdlHeaders.right = new FormAttachment(middle, -margin);
    fdlHeaders.top = new FormAttachment(wEndpoint, margin);
    wlHeaders.setLayoutData(fdlHeaders);

    ColumnInfo[] columns = {
      new ColumnInfo(
          BaseMessages.getString(PKG, "OpenTelemetryConfig.Header.Name.Label"),
          ColumnInfo.COLUMN_TYPE_TEXT,
          false,
          false),
      new ColumnInfo(
          BaseMessages.getString(PKG, "OpenTelemetryConfig.Header.Value.Label"),
          ColumnInfo.COLUMN_TYPE_TEXT,
          false,
          false)
    };

    wHeaders =
        new TableView(
            Variables.getADefaultVariableSpace(),
            wComposite,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL,
            columns,
            0,
            null,
            PropsUi.getInstance());
    wHeaders.setReadonly(false);
    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(middle, 0);
    fdFields.top = new FormAttachment(wlHeaders, 0, SWT.TOP);
    fdFields.right = new FormAttachment(100, 0);
    fdFields.bottom = new FormAttachment(100, margin);
    wHeaders.setLayoutData(fdFields);
    PropsUi.setLook(wHeaders);

    load();

    // Add listener after load, because save is called on each setText() and we lose headers
    wServiceName.addListener(SWT.Modify, e -> save());
    wEndpoint.addListener(SWT.Modify, e -> save());

    tabItem.setControl(wComposite);
  }

  private void load() {
    OpenTelemetryConfig config = OpenTelemetryPlugin.getInstance().loadConfig();

    // Get the headers
    //
    for (Map.Entry<String, String> entry : config.getHeaders().entrySet()) {
      TableItem item = new TableItem(wHeaders.table, SWT.NONE);
      item.setText(1, Const.NVL(entry.getKey(), ""));
      item.setText(2, Const.NVL(entry.getValue(), ""));
    }
    wHeaders.optimizeTableView();

    wServiceName.setText(Const.NVL(config.getServiceName(), ""));
    wEndpoint.setText(Const.NVL(config.getEndpoint(), ""));
  }

  private void save() {
    try {
      // Save the configuration...
      OpenTelemetryConfig config = new OpenTelemetryConfig();
      config.setServiceName(wServiceName.getText());
      config.setEndpoint(wEndpoint.getText());

      Map<String, String> headers = new HashMap<>();
      for (int i = 0; i < wHeaders.nrNonEmpty(); i++) {
        TableItem item = wHeaders.getNonEmpty(i);
        String name = item.getText(1);
        String value = item.getText(2);
        headers.put(name, value);
      }
      config.setHeaders(headers);

      // config.setTimeout(timeout);
      OpenTelemetryPlugin.getInstance().saveConfig(config);

    } catch (Exception e) {
      new ErrorDialog(
          HopGui.getInstance().getShell(), "Error", "Error saving opentelemetry configuration", e);
    }
  }
}
