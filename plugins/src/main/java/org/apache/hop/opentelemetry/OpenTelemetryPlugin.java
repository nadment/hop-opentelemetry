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

import org.apache.hop.core.Const;
import org.apache.hop.core.config.HopConfig;
import org.apache.hop.core.variables.Variable;
import org.apache.hop.core.variables.VariableScope;
import io.opentelemetry.api.trace.SpanKind;

public class OpenTelemetryPlugin {
  
  @Variable(scope = VariableScope.SYSTEM, description = "The default service name of spans, metrics, or logs.")
  public static final String OTEL_SERVICE_NAME = "OTEL_SERVICE_NAME";
  
  @Variable(scope = VariableScope.SYSTEM, description = "Target URL of the OpenTelemetry Collector to which send spans, metrics, or logs.")
  public static final String OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";

  @Variable(scope = VariableScope.SYSTEM, description = "Key-value pairs to be used as headers associated with gRPC or HTTP requests, i.e.: key1=value1,key2=value2.")
  public static final String OTEL_EXPORTER_OTLP_HEADERS = "OTEL_EXPORTER_OTLP_HEADERS";

  @Variable(scope = VariableScope.SYSTEM, description = "Maximum time the OTLP exporter will wait for each batch export.")
  public static final String OTEL_EXPORTER_OTLP_TIMEOUT = "OTEL_EXPORTER_OTLP_TIMEOUT";
  
  private static OpenTelemetryConfig instance;
  
  /**
   * Gets instance
   *
   * @return value of instance
   */
  public static OpenTelemetryConfig getInstance() {
    if (instance == null) {      
      instance = new OpenTelemetryConfig();
    }
    return instance;
  }
  
  public OpenTelemetryPlugin() {
    super();
  }
  
  public static SpanKind getSpanKind() {
    if ("SERVER".equalsIgnoreCase(Const.getHopPlatformRuntime())) {
      return SpanKind.SERVER;
    }
    if ("GUI".equalsIgnoreCase(Const.getHopPlatformRuntime())) {
      return SpanKind.CLIENT;
    }
    return SpanKind.CLIENT;
  }
  
  /**
   *  Load configuration from the HopConfig store 
   */
  public static OpenTelemetryConfig loadConfig() {   
    OpenTelemetryConfig config = new OpenTelemetryConfig();

    // By default use system properties
    String serviceName = System.getProperty(OTEL_SERVICE_NAME);
    if ( serviceName==null ) {
      serviceName = HopConfig.readOptionString(OTEL_SERVICE_NAME, "Apache Hop");
    }
    config.setServiceName(serviceName);
    
    String endpoint = System.getProperty(OTEL_EXPORTER_OTLP_ENDPOINT);
    if ( endpoint==null ) {
      endpoint = HopConfig.readOptionString(OTEL_EXPORTER_OTLP_ENDPOINT, "");
    }
    config.setEndpoint(endpoint);

   // config.setTimeout(HopConfig.readOptionInteger(OTEL_EXPORTER_OTLP_TIMEOUT, 10));  
        
    return config;
  }

  /**
   *  Save configuration to the HopConfig store 
   */
  public static void saveConfig(OpenTelemetryConfig config) {
    System.setProperty(OTEL_SERVICE_NAME, config.getServiceName());
    System.setProperty(OTEL_EXPORTER_OTLP_ENDPOINT, config.getEndpoint());
    //System.setProperty(OTEL_EXPORTER_OTLP_TIMEOUT, config.getEndpoint());

    HopConfig.getInstance().saveOption(OTEL_SERVICE_NAME, config.getServiceName());
    HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_ENDPOINT, config.getEndpoint());
    //HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_TIMEOUT, config.getEndpoint());    
  }  
}
