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
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;


public class OpenTelemetryExecution {

  public static final String INSTRUMENTATION_SCOPE = "org.apache.hop.opentelemetry";

  public static final String PARENT_SPAN = "opentelemetry.span";
  public static final String ACTION_SPAN = "opentelemetry.span.";
  
    
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
}
