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

import static java.time.temporal.ChronoUnit.SECONDS;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.HopVersionProvider;
import org.apache.hop.core.config.HopConfig;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variable;
import org.apache.hop.core.variables.VariableScope;

public class OpenTelemetryPlugin {

  @Variable(
      scope = VariableScope.SYSTEM,
      description = "The default service name of spans, metrics, or logs.")
  public static final String OTEL_SERVICE_NAME = "OTEL_SERVICE_NAME";

  @Variable(
      scope = VariableScope.SYSTEM,
      description =
          "Target URL of the OpenTelemetry Collector to which send spans, metrics, or logs.")
  public static final String OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";

  @Variable(
      scope = VariableScope.SYSTEM,
      description =
          "Key-value pairs to be used as headers associated with gRPC or HTTP requests, i.e.: key1=value1,key2=value2.")
  public static final String OTEL_EXPORTER_OTLP_HEADERS = "OTEL_EXPORTER_OTLP_HEADERS";

  @Variable(
      scope = VariableScope.SYSTEM,
      description = "The OLTP transport protocol. Options MUST be one of: grpc, http/protobuf.")
  public static final String OTEL_EXPORTER_OTLP_PROTOCOL = "OTEL_EXPORTER_OTLP_PROTOCOL";

  @Variable(
      scope = VariableScope.SYSTEM,
      description = "Maximum time the OTLP exporter will wait for each batch export.")
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

      log.logBasic(
          "OpenTelemetry for service '"
              + config.getServiceName()
              + "' with transport protocol "
              + config.getProtocol()
              + " to endpoint: "
              + config.getEndpoint());

      // Initialize OpenTelemetry
      //
      OpenTelemetrySdk telemetry =
          OpenTelemetrySdk.builder()
              .setLoggerProvider(createLoggerProvider(config))
              .setTracerProvider(createTracerProvider(config))
              .setMeterProvider(createMeterProvider(config))
              .buildAndRegisterGlobal();

      // Add hook to close SDK, which flushes logs, metrics and traces
      //
      Runtime.getRuntime().addShutdownHook(new Thread(telemetry::close));

      LongCounter hopStartCount =
          GlobalOpenTelemetry.getMeter("CLIENT")
              .counterBuilder("hop.start.count")
              .setDescription("The total number of times a hop runtime has been started.")
              .build();

      hopStartCount.add(1);
    } catch (Exception e) {
      log.logError("OpenTelemetry initialization error", e);
    }
  }

  /**
   * Load configuration.
   *
   * <p>By default use system properties else use the HopConfig.
   */
  public OpenTelemetryConfig loadConfig() {
    OpenTelemetryConfig config = new OpenTelemetryConfig();

    String serviceName = System.getProperty(OTEL_SERVICE_NAME);
    if (StringUtils.isEmpty(serviceName)) {
      serviceName = HopConfig.readOptionString(OTEL_SERVICE_NAME, "Apache Hop");
    }
    config.setServiceName(serviceName);

    String endpoint = System.getProperty(OTEL_EXPORTER_OTLP_ENDPOINT);
    if (StringUtils.isEmpty(endpoint)) {
      endpoint = HopConfig.readOptionString(OTEL_EXPORTER_OTLP_ENDPOINT, "");
    }
    config.setEndpoint(endpoint);

    String protocol = System.getProperty(OTEL_EXPORTER_OTLP_PROTOCOL);
    if (StringUtils.isEmpty(protocol)) {
      protocol = HopConfig.readOptionString(OTEL_EXPORTER_OTLP_PROTOCOL, "grpc");
    }
    config.setProtocol(protocol);

    String headers = System.getProperty(OTEL_EXPORTER_OTLP_HEADERS);
    if (StringUtils.isEmpty(headers)) {
      headers = HopConfig.readOptionString(OTEL_EXPORTER_OTLP_HEADERS, "");
    }
    config.setHeadersAsString(headers);

    int timeout = Const.toInt(System.getProperty(OTEL_EXPORTER_OTLP_TIMEOUT), 0);
    if (timeout == 0) {
      timeout = HopConfig.readOptionInteger(OTEL_EXPORTER_OTLP_TIMEOUT, 10);
    }
    config.setTimeout(Duration.of(timeout, SECONDS));

    return config;
  }

  /** Save configuration to the HopConfig store */
  public void saveConfig(OpenTelemetryConfig config) {
    HopConfig.getInstance().saveOption(OTEL_SERVICE_NAME, config.getServiceName());
    HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_ENDPOINT, config.getEndpoint());
    HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_PROTOCOL, config.getProtocol());
    HopConfig.getInstance().saveOption(OTEL_EXPORTER_OTLP_HEADERS, config.getHeadersAsSrtring());
    HopConfig.getInstance()
        .saveOption(OTEL_EXPORTER_OTLP_TIMEOUT, String.valueOf(config.getTimeout().getSeconds()));
  }

  /** Initialize meter provider */
  public SdkMeterProvider createMeterProvider(OpenTelemetryConfig config) {

    MetricExporter exporter = null;
    if ("grpc".equalsIgnoreCase(config.getProtocol())) {
      // Create an OTLP metric exporter via gRPC
      exporter =
          OtlpGrpcMetricExporter.builder()
              .setEndpoint(config.getEndpoint())
              .setTimeout(config.getTimeout())
              .setHeaders(config::getHeaders)
              .build();
    } else {
      // Create an OTLP metric exporter via HTTP
      exporter =
          OtlpHttpMetricExporter.builder()
              .setEndpoint(config.getEndpoint() + "/v1/metrics")
              .setTimeout(config.getTimeout())
              .setHeaders(config::getHeaders)
              .build();
    }

    return SdkMeterProvider.builder()
        .setResource(getResource(config))
        .registerMetricReader(PeriodicMetricReader.builder(exporter).build())
        // .registerMetricReader(PeriodicMetricReader.create(LoggingMetricExporter.create()))
        .build();
  }

  /** Initialize tracer provider */
  public SdkTracerProvider createTracerProvider(OpenTelemetryConfig config) {

    SpanExporter exporter = null;
    if ("grpc".equalsIgnoreCase(config.getProtocol())) {
      // Create an OTLP trace exporter via gRPC
      exporter =
          OtlpGrpcSpanExporter.builder()
              .setEndpoint(config.getEndpoint())
              .setTimeout(config.getTimeout())
              .setHeaders(config::getHeaders)
              .build();
    } else {
      // Create an OTLP trace exporter via HTTP
      exporter =
          OtlpHttpSpanExporter.builder()
              .setEndpoint(config.getEndpoint() + "/v1/traces")
              .setTimeout(config.getTimeout())
              .setHeaders(config::getHeaders)
              .build();
    }

    return SdkTracerProvider.builder()
        .setResource(getResource(config))
        .addSpanProcessor(
            BatchSpanProcessor.builder(exporter)
                .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                .build())
        .setSampler(Sampler.alwaysOn())
        .build();
  }

  /** Initialize logger provider */
  public SdkLoggerProvider createLoggerProvider(OpenTelemetryConfig config) {

    LogRecordExporter exporter = null;
    if ("grpc".equalsIgnoreCase(config.getProtocol())) {
      // Create an OTLP log exporter via gRPC
      exporter =
          OtlpGrpcLogRecordExporter.builder()
              .setEndpoint(config.getEndpoint())
              .setTimeout(config.getTimeout())
              .setHeaders(() -> config.getHeaders())
              .build();
    } else {
      // Create an OTLP log exporter via HTTP
      exporter =
          OtlpHttpLogRecordExporter.builder()
              .setEndpoint(config.getEndpoint() + "/v1/logs")
              .setTimeout(config.getTimeout())
              .setHeaders(config::getHeaders)
              .build();
    }

    return SdkLoggerProvider.builder()
        .setResource(getResource(config))
        .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).build())
        .build();
  }

  /** Build common resource attributes for all spans and metrics */
  public Resource getResource(OpenTelemetryConfig config) {
    HopVersionProvider versionProvider = new HopVersionProvider();

    return Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.builder()
                    .put(HostIncubatingAttributes.HOST_NAME, Const.getHostname())
                    .put(ServiceAttributes.SERVICE_NAME, config.getServiceName())
                    .put(ServiceAttributes.SERVICE_VERSION, versionProvider.getVersion()[0])
                    .put(HopAttributes.HOP_RUNTIME, Const.getHopPlatformRuntime())
                    .build()));
  }
}
