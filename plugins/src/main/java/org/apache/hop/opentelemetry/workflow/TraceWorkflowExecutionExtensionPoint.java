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
 */

package org.apache.hop.opentelemetry.workflow;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.opentelemetry.OpenTelemetryPlugin;
import org.apache.hop.workflow.IActionListener;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

@ExtensionPoint(id = "OpenTelemetryTraceWorkflowExecutionExtensionPoint",
    description = "Trace execution of a workflow for OpenTelemetry",
    extensionPointId = "WorkflowStart")
public class TraceWorkflowExecutionExtensionPoint
    implements IExtensionPoint<IWorkflowEngine<WorkflowMeta>> {

  public static final String INSTRUMENTATION_SCOPE = "org.apache.hop.opentelemetry";

  public static final String WORKFLOW_SPAN = "workflow.span";
  public static final String ACTION_SPAN = "action.span";

  private static final AttributeKey<String> WORKFLOW_RUN_CONFIGURATION_KEY = stringKey("workflow.run.configuration");
  private static final AttributeKey<String> WORKFLOW_EXECUTION_ID_KEY = stringKey("workflow.execution.id");
  private static final AttributeKey<String> WORKFLOW_CONTAINER_ID_KEY = stringKey("workflow.container.id");
  private static final AttributeKey<String> WORKFLOW_NAME_KEY = stringKey("workflow.name");
  private static final AttributeKey<String> WORKFLOW_DESCRIPTION_KEY = stringKey("workflow.description");
  private static final AttributeKey<String> WORKFLOW_FIELNAME_KEY = stringKey("workflow.filename");
  private static final AttributeKey<String> ACTION_PLUGIN_ID_KEY = stringKey("action.plugin.id");
  private static final AttributeKey<String> ACTION_DESCRIPTION_KEY = stringKey("action.description");
  
  // Acquiring a meter
  private static final Meter meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE);
  // Acquiring a tracer
  private static final Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE);
  
  private LongCounter workflow_execution_count;
  //private LongCounter workflow_execution_successes;
  //private LongCounter workflow_execution_failures;

  public TraceWorkflowExecutionExtensionPoint() {
    super();

    workflow_execution_count = meter.counterBuilder("workflow.execution.count")
        .setDescription("Counts workflow execution.").setUnit("unit").build();
    
//    workflow_execution_successes = meter.counterBuilder("workflow.execution.success.count")
//        .setDescription("Counts workflow execution successes.").setUnit("unit").build();

//    workflow_execution_failures = meter.counterBuilder("workflow.execution.failure.count")
//        .setDescription("Counts workflow execution failures.").setUnit("unit").build();
  }

  @Override
  public void callExtensionPoint(ILogChannel log, IVariables variables,
      IWorkflowEngine<WorkflowMeta> workflow) throws HopException {

    Attributes attributes = Attributes.builder()
        .put(WORKFLOW_RUN_CONFIGURATION_KEY,workflow.getWorkflowRunConfiguration().getName())
        .put(WORKFLOW_CONTAINER_ID_KEY,workflow.getContainerId())
        .put(WORKFLOW_EXECUTION_ID_KEY,workflow.getLogChannelId())
        .put(WORKFLOW_NAME_KEY, workflow.getWorkflowMeta().getName())        
        .put(WORKFLOW_DESCRIPTION_KEY,workflow.getWorkflowMeta().getDescription())
        .put(WORKFLOW_FIELNAME_KEY,workflow.getFilename())
        .build();
    
    SpanBuilder spanBuilder = tracer.spanBuilder(workflow.getWorkflowName())
        .setSpanKind(OpenTelemetryPlugin.getSpanKind())
        .setAttribute("component", "workflow")
        .setAllAttributes(attributes)     
        .setStartTimestamp(workflow.getExecutionStartDate().toInstant());

    IWorkflowEngine<?> parent = workflow.getParentWorkflow();
    if (parent != null) {
      Span parentSpan = (Span) parent.getExtensionDataMap().get(WORKFLOW_SPAN);
      spanBuilder.setParent(Context.current().with(parentSpan));
    }

    Span workflowSpan = spanBuilder.startSpan();

    workflow.getExtensionDataMap().put(WORKFLOW_SPAN, workflowSpan);

    workflow.addWorkflowFinishedListener(engine -> {

      
      
      Result result = engine.getResult();
      if (result.getNrErrors() > 0) {
        workflowSpan.setStatus(StatusCode.ERROR);
       // attributes.
        //workflowSpan.recordException(log.);
        //workflow_execution_failures.add(1, attributes);
      } else {
        workflowSpan.setStatus(StatusCode.OK);
        //workflow_execution_successes.add(1, attributes);
      }

      if ( engine.getExecutionEndDate()!=null ) {
        workflowSpan.end(engine.getExecutionEndDate().toInstant());
      }      
      
      workflow_execution_count.add(1, attributes);
      
//      io.opentelemetry.api.logs.Logger logger = GlobalOpenTelemetry.get().getLogsBridge().get("custom-log-appender");
//      logger
//        .logRecordBuilder()
//        .setSeverity(Severity.INFO)
//        .setBody("A log message from a custom appender without a span")
//        .setAllAttributes(attributes)
//        .emit();
    });
    
    // Also log every workflow action execution results.
    //
    workflow.addActionListener(new IActionListener() {
      @Override
      public void beforeExecution(IWorkflowEngine workflow, ActionMeta actionMeta, IAction action) {

        // Ignore workflow
//        if ("WORKFLOW".equalsIgnoreCase(action.getPluginId())) {
//          return;
//        }

        Span actionSpan =
            tracer.spanBuilder(actionMeta.getName()).setParent(Context.current().with(workflowSpan))
                .startSpan().setAttribute("component", "action")
                .setAttribute(ACTION_PLUGIN_ID_KEY, action.getPluginId())
                .setAttribute(ACTION_DESCRIPTION_KEY, action.getDescription());

        workflow.getExtensionDataMap().put(ACTION_SPAN, actionSpan);
      }

      @Override
      public void afterExecution(IWorkflowEngine workflow, ActionMeta actionMeta, IAction action,
          Result result) {

        // Ignore workflow executor
//        if ("WORKFLOW".equalsIgnoreCase(action.getPluginId())) {
//          return;
//        }

        Span actionSpan = (Span) workflow.getExtensionDataMap().get(ACTION_SPAN);
        if (actionSpan != null) {
          actionSpan.end();
        }
      }
    });

  }
}
