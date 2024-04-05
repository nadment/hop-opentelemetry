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

import org.apache.commons.lang.StringUtils;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for using OTLP.
 */
public class OpenTelemetryConfig {

  private String serviceName;
  /**
   * URL to the OTel collector's receiver.
   */
  private String endpoint;
  private String protocol;
  /**
   * Custom HTTP headers you want to pass to the collector, for example auth headers.
   */
  private Map<String, String> headers;

  private Duration timeout;

  public OpenTelemetryConfig() {
    super();
    this.headers = new HashMap<String, String>();
    this.timeout = Duration.ofSeconds(10);
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = StringUtils.trim(serviceName);
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {    
    this.endpoint = StringUtils.trim(endpoint);
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public String getHeadersAsSrtring() {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (builder.length() > 0) {
        builder.append(',');
      }
      builder.append(StringUtils.trim(entry.getKey()));
      builder.append('=');
      builder.append( StringUtils.trim(entry.getValue()));
    }

    return builder.toString();
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public void setHeadersAsString(String str) {
    this.headers = new HashMap<String, String>();
    if (str == null) {
      return;
    }
    for (String header : str.split("[,]", 0)) {
      String[] pair = header.split("=", 2);
      if (pair.length == 2) {
        String name = pair[0];
        String value = pair[1];
        headers.put(name, value);
      }
    }
  }

  public String getProtocol() {
    return protocol;
  }

  /**
   * Set the OLTP transport protocol.
   * <p>Options MUST be one of:
   * <ul>
   *    <li>grpc</li>
   *    <li>http/protobuf</li>
   * </ul>
   */
  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }
}
