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

import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.HopVersionProvider;
import org.apache.hop.core.config.HopConfig;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variable;
import org.apache.hop.core.variables.VariableScope;
import java.util.concurrent.TimeUnit;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;

public class OpenTelemetryPlugin {
  
  @Variable(scope = VariableScope.SYSTEM, description = "The default service name of spans, metrics, or logs.")
  public static final String OTEL_SERVICE_NAME = "OTEL_SERVICE_NAME";
  
  @Variable(scope = VariableScope.SYSTEM, description = "Target URL of the OpenTelemetry Collector to which send spans, metrics, or logs.")
  public static final String OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";

  @Variable(scope = VariableScope.SYSTEM, description = "Key-value pairs to be used as headers associated with gRPC or HTTP requests, i.e.: key1=value1,key2=value2.")
  public static final String OTEL_EXPORTER_OTLP_HEADERS = "OTEL_EXPORTER_OTLP_HEADERS";

  @Variable(scope = VariableScope.SYSTEM, description = "Maximum time the OTLP exporter will wait for each batch export.")
  public static final String OTEL_EXPORTER_OTLP_TIMEOUT = "OTEL_EXPORTER_OTLP_TIMEOUT";
  
  private static OpenTelemetryPlugin instance;
  
  /**
   * Gets instance
   *
   * @return value of instance
   */
  public static OpenTelemetryPlugin getInstance() {
    if (instance == null) {      
      instance = new OpenTelemetryPlugin();
    }
    return instance;
  }
  
  private OpenTelemetryPlugin() {
    super();
  }

  void init(ILogChannel log, IVariables variables) {
    try {
      
      //  Thread.sleep(5000);
        
        OpenTelemetryConfig config = loadConfig();
        
        log.logBasic("OpenTelemetry initialization service '"+config.getServiceName()+"' to endpoint: "+config.getEndpoint());

        // Initialize OpenTelemetry
        //
        OpenTelemetrySdk telemetry = OpenTelemetrySdk.builder()
            .setLoggerProvider(createLoggerProvider(config))
            .setTracerProvider(createTracerProvider(config))
            .setMeterProvider(createMeterProvider(config))
            .buildAndRegisterGlobal();

        // Add hook to close SDK, which flushes logs, metrics and traces
        //
        Runtime.getRuntime().addShutdownHook(new Thread(telemetry::close));
      } catch (Exception e) {
        log.logError("OpenTelemetry initialization error", e);
      }
  }
  
  /**
   *  Load configuration from the HopConfig store 
   */
  public OpenTelemetryConfig loadConfig() {   
    OpenTelemetryConfig config = new OpenTelemetryConfig();

    // By default use system properties
    String serviceName = System.getProperty(OTEL_SERVICE_NAME);
    if (  StringUtils.isEmpty(serviceName) ) {
      serviceName = HopConfig.readOptionString(OTEL_SERVICE_NAME, "Apache Hop");
    }
    config.setServiceName(serviceName);
    
    String endpoint = System.getProperty(OTEL_EXPORTER_OTLP_ENDPOINT);
    if (  StringUtils.isEmpty(endpoint) ) {
      endpoint = HopConfig.readOptionString(OTEL_EXPORTER_OTLP_ENDPOINT, "");
    }
    config.setEndpoint(endpoint);

    String headers = System.getProperty(OTEL_EXPORTER_OTLP_HEADERS);
    if ( StringUtils.isEmpty(headers) ) {
      headers = HopConfig.readOptionString(OTEL_EXPORTER_OTLP_HEADERS, "");
    }
    config.setHeadersAsString(headers);
    
   // config.setTimeout(HopConfig.readOptionInteger(OTEL_EXPORTER_OTLP_TIMEOUT, 10));  
    return config;
  }

  /**
   *  Save configuration to the HopConfig store 
   */
  public void saveConfig(OpenTelemetryConfig config) {
    HopConfig.getInstance().saveOption(OTEL_SERVICE_NAME, config.getServiceName());
    HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_ENDPOINT, config.getEndpoint());
    HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_HEADERS, config.getHeadersAsSrtring());
    HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_TIMEOUT, String.valueOf(config.getTimeout()));    
  }  
  
  /** 
   * Initialize meter provider
   */
  public SdkMeterProvider createMeterProvider(OpenTelemetryConfig config) {
    return SdkMeterProvider.builder().setResource(getResource(config))
    .registerMetricReader(PeriodicMetricReader.builder(OtlpGrpcMetricExporter.builder()
        .setEndpoint(config.getEndpoint())
        .setHeaders(() -> config.getHeaders())
        .setTimeout(config.getTimeout(), TimeUnit.SECONDS).build())        
        .build())     
    .build();
  }
  
  /** 
   * Initialize tracer provider
   */
  public SdkTracerProvider createTracerProvider(OpenTelemetryConfig config) {   
    return SdkTracerProvider.builder().setResource(getResource(config))
        .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder()
        .setEndpoint(config.getEndpoint())        
        .setHeaders(() -> config.getHeaders())
        .setTimeout(config.getTimeout(), TimeUnit.SECONDS).build())
        .setScheduleDelay(100, TimeUnit.MILLISECONDS)
        .build())
    .setSampler(Sampler.alwaysOn()).build();
  }
  
  /** 
   * Initialize logger provider
   */
  public SdkLoggerProvider createLoggerProvider(OpenTelemetryConfig config) {
    return SdkLoggerProvider.builder().setResource(getResource(config))
        .addLogRecordProcessor(BatchLogRecordProcessor.builder(
            OtlpGrpcLogRecordExporter.builder()
              .setEndpoint(config.getEndpoint())
              .setHeaders(() -> config.getHeaders())
              .setTimeout(config.getTimeout(), TimeUnit.SECONDS)
              .build())
            .build())
        .build();
  }
  
  /**
   * Build common resource attributes for all spans and metrics
   */
  public Resource getResource(OpenTelemetryConfig config) {
    HopVersionProvider versionProvider = new HopVersionProvider();

    return Resource.getDefault().merge(Resource.create(Attributes.builder()
        .put(ResourceAttributes.SERVICE_NAME, config.getServiceName())
        .put(ResourceAttributes.SERVICE_VERSION, versionProvider.getVersion()[0]).build()));
  }
}
