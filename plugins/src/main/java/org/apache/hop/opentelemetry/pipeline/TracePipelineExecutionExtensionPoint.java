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

package org.apache.hop.opentelemetry.pipeline;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.opentelemetry.OpenTelemetryPlugin;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEnginePlugin;
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

@ExtensionPoint(id = "OpenTelemetryTracePipelineExecutionExtensionPoint",
    description = "Trace execution of a pipeline for OpenTelemetry",
    extensionPointId = "PipelinePrepareExecution")

public class TracePipelineExecutionExtensionPoint
    implements IExtensionPoint<IPipelineEngine<PipelineMeta>> {

  public static final String INSTRUMENTATION_SCOPE = "org.apache.hop.opentelemetry";
  public static final String CURRENT_WORKFLOW_SPAN = "workflow.span";
  public static final String PIPELINE_LOGGING_FLAG = "PipelineLoggingActive";

  private static final AttributeKey<String> COMPONENT_KEY = stringKey("component");
  private static final AttributeKey<String> PIPELINE_ENGINE_KEY = stringKey("pipeline.engine");
  private static final AttributeKey<String> PIPELINE_EXECUTION_ID_KEY = stringKey("pipeline.execution.id");
  private static final AttributeKey<String> PIPELINE_CONTAINER_ID_KEY = stringKey("pipeline.container.id");
  private static final AttributeKey<String> PIPELINE_NAME_KEY = stringKey("pipeline.name");
  private static final AttributeKey<String> PIPELINE_DESCRIPTION_KEY = stringKey("pipeline.description");
  private static final AttributeKey<String> PIPELINE_FIELNAME_KEY = stringKey("pipeeline.filename");

  // Acquiring a meter
  private static final Meter meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE);
  
  // Acquiring a tracer
  private static final Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE);

  private LongCounter pipeline_execution_count;
  private LongCounter transform_execution_count;
    
  public TracePipelineExecutionExtensionPoint() {
    super();

    pipeline_execution_count = meter
    .counterBuilder("pipeline.execution.count")
    .setDescription("Counts pipeline execution.")
    .setUnit("unit")
    .build();  
  }


  @Override
  public void callExtensionPoint(ILogChannel log, IVariables variables,
      IPipelineEngine<PipelineMeta> pipeline) throws HopException {
    
    // Prevent doing observability on the logging pipeline
    //
    if (pipeline.getExtensionDataMap().get(PIPELINE_LOGGING_FLAG) != null) {
      return;
    }
    
    PipelineEnginePlugin pipelinePlugin = pipeline.getClass().getAnnotation(PipelineEnginePlugin.class);

    // Create pipeline trace
    SpanBuilder spanBuilder = tracer.spanBuilder(pipeline.getPipelineMeta().getName())
        .setSpanKind(OpenTelemetryPlugin.getSpanKind())
        .setAttribute(COMPONENT_KEY, "pipeline")
        .setAttribute(PIPELINE_ENGINE_KEY, pipelinePlugin.id())
        .setAttribute(PIPELINE_CONTAINER_ID_KEY, pipeline.getContainerId()) 
        .setAttribute(PIPELINE_EXECUTION_ID_KEY, pipeline.getLogChannelId())
        .setAttribute(PIPELINE_NAME_KEY, pipeline.getPipelineMeta().getName())
        .setAttribute(PIPELINE_DESCRIPTION_KEY, pipeline.getPipelineMeta().getDescription())    
        .setAttribute(PIPELINE_FIELNAME_KEY, pipeline.getFilename())
        .setStartTimestamp(pipeline.getExecutionStartDate().toInstant());
    
    Span pipelineSpan = spanBuilder.startSpan();
    
    // Set parent
    IWorkflowEngine<?> parent = pipeline.getParentWorkflow();
    if (parent != null) {
      Span parentSpan = (Span) parent.getExtensionDataMap().get(CURRENT_WORKFLOW_SPAN);
      spanBuilder.setParent(Context.current().with(parentSpan));
    }
    
    
    // Pipeline metrics
    pipeline_execution_count.add(1,  Attributes.builder() 
        .put(PIPELINE_ENGINE_KEY, pipelinePlugin.id()).build());
    
    // Pipeline trace
    pipeline.addExecutionFinishedListener(engine -> {
      Result result = engine.getResult();      
      if ( result.getNrErrors()>0 ) {        
        pipelineSpan.setStatus(StatusCode.ERROR);
      } 
      else {
        pipelineSpan.setStatus(StatusCode.OK);
      }
      
      if ( engine.getExecutionEndDate()!=null ) {
        pipelineSpan.end(engine.getExecutionEndDate().toInstant());
      }
    });
  }
}

