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

package org.apache.hop.opentelemetry;

import org.apache.hop.core.IExtensionData;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.execution.ExecutionType;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEnginePlugin;
import org.apache.hop.pipeline.transform.ITransform;
import java.time.Instant;
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

@ExtensionPoint(id = "PipelineTelemetryExtensionPoint",
    description = "Trace execution of a pipeline for OpenTelemetry",
    extensionPointId = "PipelinePrepareExecution")
public class PipelineExecutionTelemetryExtensionPoint extends ExecutionTelemetry
    implements IExtensionPoint<IPipelineEngine<PipelineMeta>> {
  public static final String INSTRUMENTATION_PIPELINE_SCOPE = "Pipeline";
  public static final String INSTRUMENTATION_TRANSFORM_SCOPE = "Transform";

  public static final String PIPELINE_LOGGING_FLAG = "PipelineLoggingActive";
  
  private LongCounter pipeline_execution_count;
  private LongCounter transform_execution_count;
    
  public PipelineExecutionTelemetryExtensionPoint() {
    super();

    pipeline_execution_count = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_PIPELINE_SCOPE)
    .counterBuilder("pipeline.execution.count")
    .setDescription("The total number of times a pipeline has been executed.")
    .build();
    
    transform_execution_count = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_TRANSFORM_SCOPE)
    .counterBuilder("transformation.execution.count")
    .setDescription("The total number of times a transform has been executed.")
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

    // Acquiring a tracer
    Tracer pipelineTracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_PIPELINE_SCOPE);
    
    PipelineEnginePlugin pipelinePlugin = pipeline.getClass().getAnnotation(PipelineEnginePlugin.class);
    PipelineMeta pipelineMeta = pipeline.getPipelineMeta();
    
    // Define context    
    Context context = getContext(pipeline);
    
    // Create pipeline trace
    final Span pipelineSpan  = pipelineTracer.spanBuilder(pipeline.getPipelineMeta().getName())
        .setSpanKind(SpanKind.SERVER)
        .setParent(context)
        .setAttribute(ResourceAttributes.OTEL_SCOPE_NAME, ExecutionType.Pipeline.name())
        .setAttribute(HopAttributes.PIPELINE_ENGINE, pipelinePlugin.id())
        .setAttribute(HopAttributes.PIPELINE_RUN_CONFIGURATION, pipeline.getPipelineRunConfiguration().getName())
        .setAttribute(HopAttributes.PIPELINE_CONTAINER_ID, pipeline.getContainerId()) 
        .setAttribute(HopAttributes.PIPELINE_EXECUTION_ID, pipeline.getLogChannelId())
        .setAttribute(HopAttributes.PIPELINE_FILE_PATH, pipelineMeta.getFilename())
        .setAttribute(HopAttributes.PIPELINE_VERSION, pipelineMeta.getPipelineVersion())
        .setStartTimestamp(pipeline.getExecutionStartDate().toInstant())
        .startSpan();
    
    this.addProjectAndEnvironment(variables, pipelineSpan);

    pipeline.getExtensionDataMap().put(SPAN, pipelineSpan);
    
    // Set pipeline span to all transforms
    for (IEngineComponent component:pipeline.getComponents()) {
      if ( component instanceof IExtensionData ) {
        ((IExtensionData) component).getExtensionDataMap().put(SPAN, pipelineSpan);
      }
    }
    
    // Pipeline trace
    pipeline.addExecutionFinishedListener(engine -> {
      Result result = engine.getResult();      
      pipelineSpan.setStatus(result.getNrErrors()>0 ? StatusCode.ERROR:StatusCode.OK, pipeline.getStatusDescription());

      if ( engine.getExecutionEndDate()!=null ) {
        pipelineSpan.end(engine.getExecutionEndDate().toInstant());
      }
      
      // Acquiring a tracer
      Tracer transformTracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_TRANSFORM_SCOPE);

      // Create transform trace after execution
      Context transformContext = context.with(pipelineSpan);
      for (IEngineComponent component:pipeline.getComponents()) {
        
        String pluginId = null;
        if ( component instanceof ITransform ) {
          pluginId = ((ITransform) component).getTransformPluginId();
        }
        
        // In Beam context execution start date is null 
        Instant executionStartDate = null;
        if ( component.getExecutionStartDate()!=null )  {
          executionStartDate = component.getExecutionStartDate().toInstant();
        }
        
        Span transformSpan = transformTracer.spanBuilder(component.getName())
            .setSpanKind(SpanKind.SERVER)
            .setParent(transformContext)
            .setAttribute(ResourceAttributes.OTEL_SCOPE_NAME, ExecutionType.Transform.name())
            .setAttribute(HopAttributes.TRANSFORM_PLUGIN_ID, pluginId)
            .setStartTimestamp(executionStartDate)                        
            .startSpan();
        
        if ( component.getExecutionEndDate()!=null ) {
          transformSpan.end(component.getExecutionEndDate().toInstant()); 
        }
        
        transformSpan.setStatus(component.getErrors()>0 ? StatusCode.ERROR:StatusCode.OK);
        
        this.addProjectAndEnvironment(variables, transformSpan);
      }
            
      // Increment metrics
      pipeline_execution_count.add(1,  Attributes.builder() 
          .put(HopAttributes.PIPELINE_ENGINE, pipelinePlugin.id()).build());

      // Logs pipeline result
      if (result.getLogText() != null) {

        // Acquiring a logger  
        Logger logger = GlobalOpenTelemetry.get().getLogsBridge().get(INSTRUMENTATION_PIPELINE_SCOPE);  
        
        logger.logRecordBuilder()
            .setContext(context)
            .setSeverity(Severity.INFO)
            .setBody(result.getLogText())
            //.setAttribute(COMPONENT_KEY, ExecutionType.Pipeline.name())
            .setAttribute(HopAttributes.PIPELINE_CONTAINER_ID, engine.getContainerId())
            .setAttribute(HopAttributes.PIPELINE_EXECUTION_ID, engine.getLogChannelId()).emit();
      }
    });
    
    // Add event if pipeline is stopped
    pipeline.addExecutionStoppedListener(engine -> {
      pipelineSpan.addEvent("Stopped");
    }); 
  }
}

