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

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import org.apache.hop.core.Const;
import org.apache.hop.core.IExtensionData;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.workflow.action.IAction;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;


public class OpenTelemetryExecution {

  public static final String INSTRUMENTATION_SCOPE = "org.apache.hop.opentelemetry";

  public static final String SPAN = "opentelemetry.span";
      
  public static final AttributeKey<String> COMPONENT_KEY = stringKey("component");
  
  // Acquiring a logger  
  protected static final Logger logger = GlobalOpenTelemetry.get().getLogsBridge().get(INSTRUMENTATION_SCOPE);  
  // Acquiring a meter
  protected static final Meter meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE);
  // Acquiring a tracer
  protected static final Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE);
  
  public static SpanKind getSpanKind() {
    if ("SERVER".equalsIgnoreCase(Const.getHopPlatformRuntime())) {
      return SpanKind.SERVER;
    }
    if ("GUI".equalsIgnoreCase(Const.getHopPlatformRuntime())) {
      return SpanKind.CLIENT;
    }
    return SpanKind.CLIENT;
  }

  public Context getContext(ILoggingObject object) {
    Context context = Context.current();
    
    ILoggingObject parent = object.getParent();    
    Span span = null;

    // Workflow or pipeline
    if ( parent instanceof IExtensionData ) {
      span = (Span) ((IExtensionData) parent).getExtensionDataMap().get(SPAN);      
    }

    if ( span==null && parent instanceof ITransform ) {
      ITransform transform = (ITransform) parent;
      span = (Span) transform.getPipeline().getExtensionDataMap().get(SPAN);            
    }        

    if ( span==null && parent instanceof IAction ) {
      IAction action = (IAction) parent;
      span = (Span) action.getParentWorkflow().getExtensionDataMap().get(SPAN);            
    }  
    
    if ( span!=null ) {
      context = context.with(span);
    }
    
    return context;
  }
}
