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

import org.apache.hop.core.HopVersionProvider;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
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

/**
 * Initialize OpenTelemetry plugin
 * 
 * It is important to initialize telemetry as early as possible in application's lifecycle
 */
@ExtensionPoint(id = "OpenTelemetryExtensionPoint", extensionPointId = "HopEnvironmentAfterInit",
    description = "Initialize OpenTelemetry")
public class OpenTelemetryExtensionPoint implements IExtensionPoint<PluginRegistry> {


  @Override
  public void callExtensionPoint(ILogChannel log, IVariables variables,
      PluginRegistry pluginRegistry) throws HopException {
    try {
      
    //  Thread.sleep(5000);
      
      OpenTelemetryConfig config = OpenTelemetryPlugin.loadConfig();
      
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
