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
import org.apache.hop.execution.ExecutionType;
import org.apache.hop.opentelemetry.OpenTelemetryExecution;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEnginePlugin;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

@ExtensionPoint(id = "OpenTelemetryTracePipelineExecutionExtensionPoint",
    description = "Trace execution of a pipeline for OpenTelemetry",
    extensionPointId = "PipelinePrepareExecution")
public class TracePipelineExecutionExtensionPoint extends OpenTelemetryExecution
    implements IExtensionPoint<IPipelineEngine<PipelineMeta>> {

  public static final String PIPELINE_LOGGING_FLAG = "PipelineLoggingActive";

  public static final AttributeKey<String> PIPELINE_ENGINE_KEY = stringKey("pipeline.engine");
  public static final AttributeKey<String> PIPELINE_EXECUTION_ID_KEY = stringKey("pipeline.execution.id");
  public static final AttributeKey<String> PIPELINE_CONTAINER_ID_KEY = stringKey("pipeline.container.id");
  public static final AttributeKey<String> PIPELINE_NAME_KEY = stringKey("pipeline.name");
  public static final AttributeKey<String> PIPELINE_DESCRIPTION_KEY = stringKey("pipeline.description");
  public static final AttributeKey<String> PIPELINE_FIELNAME_KEY = stringKey("pipeline.filename");

  private LongCounter pipeline_execution_count;
  private LongCounter transform_execution_count;
    
  public TracePipelineExecutionExtensionPoint() {
    super();

    pipeline_execution_count = meter
    .counterBuilder("pipeline.execution.count")
    .setDescription("Counts pipeline execution.")
    .setUnit("unit")
    .build();
    
    transform_execution_count = meter
    .counterBuilder("transformation.execution.count")
    .setDescription("Counts transformation execution.")
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
    PipelineMeta pipelineMeta = pipeline.getPipelineMeta();
    
    // Define context    
    Context context = Context.current();
    IWorkflowEngine<?> parentWorkflow = pipeline.getParentWorkflow();
    if (parentWorkflow != null) {
      Span parentSpan = (Span) parentWorkflow.getExtensionDataMap().get(PARENT_SPAN);
      context = context.with(parentSpan);
    } else {
      IPipelineEngine<PipelineMeta> parentPipeline = pipeline.getParentPipeline();
      if (parentPipeline != null) {
        Span parentSpan = (Span) parentPipeline.getExtensionDataMap().get(PARENT_SPAN);
        context = context.with(parentSpan);
      }
    }
    
    // Create pipeline trace
    final Span pipelineSpan  = tracer.spanBuilder(pipeline.getPipelineMeta().getName())
        .setSpanKind(getSpanKind())
        .setParent(context)
        .setAttribute(COMPONENT_KEY, ExecutionType.Pipeline.name())
        .setAttribute(PIPELINE_ENGINE_KEY, pipelinePlugin.id())
        .setAttribute(PIPELINE_CONTAINER_ID_KEY, pipeline.getContainerId()) 
        .setAttribute(PIPELINE_EXECUTION_ID_KEY, pipeline.getLogChannelId())
        .setAttribute(PIPELINE_NAME_KEY, pipelineMeta.getName())   
        .setAttribute(PIPELINE_FIELNAME_KEY, pipelineMeta.getFilename())
        .setStartTimestamp(pipeline.getExecutionStartDate().toInstant())
        .startSpan();
    
    
    pipeline.getExtensionDataMap().put(PARENT_SPAN, pipelineSpan);
    
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

      // Increment metrics
      pipeline_execution_count.add(1,  Attributes.builder() 
          .put(PIPELINE_ENGINE_KEY, pipelinePlugin.id()).build());

      // Logs result
      if (result.getLogText() != null) {
        logger.logRecordBuilder()
            .setContext(Context.current().with(pipelineSpan))
            .setSeverity(Severity.INFO)
            .setBody(result.getLogText())
            .setAttribute(PIPELINE_CONTAINER_ID_KEY, engine.getContainerId())
            .setAttribute(PIPELINE_EXECUTION_ID_KEY, engine.getLogChannelId()).emit();
      }
    });
  }
}

