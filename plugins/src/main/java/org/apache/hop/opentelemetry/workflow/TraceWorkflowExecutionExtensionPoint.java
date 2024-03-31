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
import org.apache.hop.execution.ExecutionType;
import org.apache.hop.opentelemetry.OpenTelemetryExecution;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.workflow.IActionListener;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.apache.hop.workflow.engine.WorkflowEnginePlugin;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

@ExtensionPoint(id = "OpenTelemetryTraceWorkflowExecutionExtensionPoint",
    description = "Trace execution of a workflow for OpenTelemetry",
    extensionPointId = "WorkflowStart")
public class TraceWorkflowExecutionExtensionPoint extends OpenTelemetryExecution implements IExtensionPoint<IWorkflowEngine<WorkflowMeta>> {

  public static final AttributeKey<String> WORKFLOW_ENGINE_KEY = stringKey("workflow.engine");
  public static final AttributeKey<String> WORKFLOW_RUN_CONFIGURATION_KEY = stringKey("workflow.run.configuration");
  public static final AttributeKey<String> WORKFLOW_EXECUTION_ID_KEY = stringKey("workflow.execution.id");
  public static final AttributeKey<String> WORKFLOW_CONTAINER_ID_KEY = stringKey("workflow.container.id");
  public static final AttributeKey<String> WORKFLOW_NAME_KEY = stringKey("workflow.name");
  public static final AttributeKey<String> WORKFLOW_DESCRIPTION_KEY = stringKey("workflow.description");
  public static final AttributeKey<String> WORKFLOW_FIELNAME_KEY = stringKey("workflow.filename");
  public static final AttributeKey<String> ACTION_PLUGIN_ID_KEY = stringKey("action.plugin.id");
  public static final AttributeKey<String> ACTION_DESCRIPTION_KEY = stringKey("action.description");
    
  private LongCounter workflow_execution_count;
  private LongCounter action_execution_count;

  public TraceWorkflowExecutionExtensionPoint() {
    super();

    workflow_execution_count = meter.counterBuilder("workflow.execution.count")
        .setDescription("Counts workflow execution.").setUnit("unit").build();
    
    action_execution_count = meter.counterBuilder("action.execution.count")
        .setDescription("Counts workflow action execution.").setUnit("unit").build();
  }

  @Override
  public void callExtensionPoint(ILogChannel log, IVariables variables,
      IWorkflowEngine<WorkflowMeta> workflow) throws HopException {

    WorkflowEnginePlugin workflowPlugin = workflow.getClass().getAnnotation(WorkflowEnginePlugin.class);
    WorkflowMeta workflowMeta = workflow.getWorkflowMeta();

    // Ignore bug #3769 with transform workflow executor 
    if (workflow.getExecutionStartDate()==null)
      return;
    
    // Define context    
    Context context = Context.current();
    IWorkflowEngine<WorkflowMeta> parentWorkflow = workflow.getParentWorkflow();
    if (parentWorkflow != null) {
      Span parentSpan = (Span) parentWorkflow.getExtensionDataMap().get(PARENT_SPAN);
      context = context.with(parentSpan);
    } else {
      IPipelineEngine<PipelineMeta> parentPipeline = workflow.getParentPipeline();
      if (parentPipeline != null) {
        Span parentSpan = (Span) parentPipeline.getExtensionDataMap().get(PARENT_SPAN);
        context = context.with(parentSpan);
      }
    }
    
    // Create workflow trace
    Span workflowSpan = tracer.spanBuilder(workflow.getWorkflowName())
        .setSpanKind(getSpanKind())
        .setParent(context)
        .setAttribute(COMPONENT_KEY, ExecutionType.Workflow.name())
        .setAttribute(WORKFLOW_ENGINE_KEY, workflowPlugin.id())
        .setAttribute(WORKFLOW_RUN_CONFIGURATION_KEY, workflow.getWorkflowRunConfiguration().getName())
        .setAttribute(WORKFLOW_CONTAINER_ID_KEY, workflow.getContainerId())
        .setAttribute(WORKFLOW_EXECUTION_ID_KEY, workflow.getLogChannelId())
        .setAttribute(WORKFLOW_NAME_KEY, workflowMeta.getName())        
        .setAttribute(WORKFLOW_FIELNAME_KEY, workflowMeta.getFilename())
        .setStartTimestamp(workflow.getExecutionStartDate().toInstant())
        .startSpan();

    workflow.getExtensionDataMap().put(PARENT_SPAN, workflowSpan);

    workflow.addWorkflowFinishedListener(engine -> {

      // Update trace
      Result result = engine.getResult();      
      if (result.getNrErrors() > 0) {
        workflowSpan.setStatus(StatusCode.ERROR);        
      } else {
        workflowSpan.setStatus(StatusCode.OK);     
      }

      if ( engine.getExecutionEndDate()!=null ) {
        workflowSpan.end(engine.getExecutionEndDate().toInstant());
      }      
      
      // Increment metrics
      workflow_execution_count.add(1,  Attributes.builder() 
          .put(WORKFLOW_ENGINE_KEY, workflowPlugin.id())
          .build());

      // Logs result
      logger
        .logRecordBuilder()
        .setContext(Context.current().with(workflowSpan))
        .setSeverity(Severity.INFO)
        .setBody(result.getLogText())
        .setAttribute(WORKFLOW_CONTAINER_ID_KEY, workflow.getContainerId())
        .setAttribute(WORKFLOW_EXECUTION_ID_KEY, workflow.getLogChannelId())
        .emit();
    });
    
    // Also trace every workflow action execution results.
    //
    workflow.addActionListener(new IActionListener() {
      @Override
      public void beforeExecution(IWorkflowEngine workflow, ActionMeta actionMeta, IAction action) {

        Span actionSpan =
            tracer.spanBuilder(actionMeta.getName())
                .setParent(Context.current().with(workflowSpan))
                .setAttribute(COMPONENT_KEY, ExecutionType.Action.name())
                .setAttribute(ACTION_PLUGIN_ID_KEY, action.getPluginId())
                .setAttribute(ACTION_DESCRIPTION_KEY, action.getDescription())
                .startSpan();

        
        workflow.getExtensionDataMap().put(ACTION_SPAN+action.hashCode(), actionSpan);
      }

      @Override
      public void afterExecution(IWorkflowEngine workflow, ActionMeta actionMeta, IAction action,
          Result result) {

        Span actionSpan = (Span) workflow.getExtensionDataMap().get(ACTION_SPAN+action.hashCode());
        if (actionSpan != null) {
          actionSpan.end();
        }
        
        if (result.getNrErrors() > 0) {
          actionSpan.setStatus(StatusCode.ERROR);        
        } else {
          actionSpan.setStatus(StatusCode.OK);     
        }
        
        action_execution_count.add(1,  Attributes.builder() 
            .put(ACTION_PLUGIN_ID_KEY, action.getPluginId())
            .build());
      }
    });

  }
}
