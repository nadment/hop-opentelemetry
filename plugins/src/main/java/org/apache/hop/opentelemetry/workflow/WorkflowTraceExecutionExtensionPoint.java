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

import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.execution.ExecutionType;
import org.apache.hop.opentelemetry.HopAttributes;
import org.apache.hop.opentelemetry.TraceExecution;
import org.apache.hop.workflow.IActionListener;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.apache.hop.workflow.engine.WorkflowEnginePlugin;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.ResourceAttributes;

@ExtensionPoint(id = "OpenTelemetryWorkflowTraceExecutionExtensionPoint",
    description = "Trace execution of a workflow for OpenTelemetry",
    extensionPointId = "WorkflowStart")
public class WorkflowTraceExecutionExtensionPoint extends TraceExecution implements IExtensionPoint<IWorkflowEngine<WorkflowMeta>> {
  
  public static final String INSTRUMENTATION_WORKFLOW_SCOPE = "Workflow";
  public static final String INSTRUMENTATION_ACTION_SCOPE = "Action";
       
  private LongCounter workflow_execution_count;
  private LongCounter action_execution_count;

  public WorkflowTraceExecutionExtensionPoint() {
    super();

    workflow_execution_count =  GlobalOpenTelemetry.getMeter(INSTRUMENTATION_WORKFLOW_SCOPE).counterBuilder("workflow.execution.count")
        .setDescription("Counts workflow execution.").setUnit("unit").build();
    
    action_execution_count =  GlobalOpenTelemetry.getMeter(INSTRUMENTATION_ACTION_SCOPE).counterBuilder("action.execution.count")
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
    
    // Acquiring a tracer
    Tracer workflowTracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_WORKFLOW_SCOPE);
    
    // Define context    
    Context context = getContext(workflow);
    
    // Create workflow trace
    final Span workflowSpan = workflowTracer.spanBuilder(workflow.getWorkflowName())
        .setSpanKind(SpanKind.SERVER)
        .setParent(context)       
        .setAttribute(ResourceAttributes.OTEL_SCOPE_NAME, ExecutionType.Workflow.name())
        .setAttribute(HopAttributes.WORKFLOW_ENGINE, workflowPlugin.id())
        .setAttribute(HopAttributes.WORKFLOW_RUN_CONFIGURATION, workflow.getWorkflowRunConfiguration().getName())
        .setAttribute(HopAttributes.WORKFLOW_CONTAINER_ID, workflow.getContainerId())
        .setAttribute(HopAttributes.WORKFLOW_EXECUTION_ID, workflow.getLogChannelId())        
        .setAttribute(HopAttributes.WORKFLOW_FILE_PATH, workflowMeta.getFilename())
        .setStartTimestamp(workflow.getExecutionStartDate().toInstant())
        .startSpan();

    this.addProjectAndEnvironment(variables, workflowSpan);
    
    workflow.getExtensionDataMap().put(SPAN, workflowSpan);
    
    workflow.addWorkflowFinishedListener(engine -> {

      // Update trace
      Result result = engine.getResult();      

      workflowSpan.setStatus(result.getNrErrors()>0 ? StatusCode.ERROR:StatusCode.OK);
      
      if ( engine.getExecutionEndDate()!=null ) {
        workflowSpan.end(engine.getExecutionEndDate().toInstant());
      }      
      
      // Increment metrics
      workflow_execution_count.add(1,  Attributes.builder() 
          .put(HopAttributes.WORKFLOW_ENGINE, workflowPlugin.id())
          .build());
      
      // Acquiring a logger  
      Logger logger = GlobalOpenTelemetry.get().getLogsBridge().get(INSTRUMENTATION_WORKFLOW_SCOPE);  
      
      // Logs result
      logger
        .logRecordBuilder()
        .setContext(context.with(workflowSpan))
        .setSeverity(Severity.INFO)        
        .setBody(result.getLogText())
        .setAttribute(ResourceAttributes.OTEL_SCOPE_NAME, ExecutionType.Workflow.name())        
        .setAttribute(HopAttributes.WORKFLOW_CONTAINER_ID, workflow.getContainerId())
        .setAttribute(HopAttributes.WORKFLOW_EXECUTION_ID, workflow.getLogChannelId())       
        .emit();
    });
    
    // Also trace every workflow action execution results.
    //
    workflow.addActionListener(new IActionListener() {
      @Override
      public void beforeExecution(IWorkflowEngine workflow, ActionMeta actionMeta, IAction action) {

        // Acquiring a tracer
        Tracer actionTracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_ACTION_SCOPE);
        
        // Create action trace
        Span actionSpan =
            actionTracer.spanBuilder(actionMeta.getName())
                .setSpanKind(SpanKind.SERVER)
                .setParent(context.with(workflowSpan))
                .setAttribute(ResourceAttributes.OTEL_SCOPE_NAME, ExecutionType.Action.name())                
                .setAttribute(HopAttributes.ACTION_PLUGIN_ID, action.getPluginId())
                .startSpan();

        //addProjectAndEnvironment(variables, actionTracer);
        
        action.getExtensionDataMap().put(SPAN, actionSpan);
      }

      @Override
      public void afterExecution(IWorkflowEngine workflow, ActionMeta actionMeta, IAction action,
          Result result) {

        Span actionSpan = (Span) action.getExtensionDataMap().get(SPAN);
        actionSpan.setStatus(result.getNrErrors()>0 ? StatusCode.ERROR:StatusCode.OK);
        actionSpan.end();
        
        action_execution_count.add(1,  Attributes.builder() 
            .put(HopAttributes.ACTION_PLUGIN_ID, action.getPluginId())
            .build());
      }
    });

  }
}
