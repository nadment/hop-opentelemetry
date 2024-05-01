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

import io.opentelemetry.api.common.AttributeKey;

public final class HopAttributes {

  private HopAttributes() {
    super();
  }

  /** The name of the Hop tool running. */
  public static final AttributeKey<String> HOP_RUNTIME = stringKey("hop.runtime");

  /** The name of the Hop project. */
  public static final AttributeKey<String> HOP_PROJECT = stringKey("hop.project");

  /** The name of the Hop environment. */
  public static final AttributeKey<String> HOP_ENVIRONMENT = stringKey("hop.environment");

  public static final AttributeKey<String> WORKFLOW_ENGINE = stringKey("hop.workflow.engine");
  public static final AttributeKey<String> WORKFLOW_RUN_CONFIGURATION =
      stringKey("hop.workflow.run_configuration");
  public static final AttributeKey<String> WORKFLOW_EXECUTION_ID =
      stringKey("hop.workflow.execution_id");
  public static final AttributeKey<String> WORKFLOW_CONTAINER_ID =
      stringKey("hop.workflow.container_id");
  public static final AttributeKey<String> WORKFLOW_FILE_PATH = stringKey("hop.workflow.file.path");
  public static final AttributeKey<String> WORKFLOW_VERSION = stringKey("hop.workflow.version");

  public static final AttributeKey<String> ACTION_PLUGIN_ID = stringKey("hop.action.plugin_id");

  public static final AttributeKey<String> PIPELINE_ENGINE = stringKey("hop.pipeline.engine");
  public static final AttributeKey<String> PIPELINE_RUN_CONFIGURATION =
      stringKey("hop.pipeline.run_configuration");
  public static final AttributeKey<String> PIPELINE_EXECUTION_ID =
      stringKey("hop.pipeline.execution_id");
  public static final AttributeKey<String> PIPELINE_CONTAINER_ID =
      stringKey("hop.pipeline.container_id");
  public static final AttributeKey<String> PIPELINE_FILE_PATH = stringKey("hop.pipeline.file.path");
  public static final AttributeKey<String> PIPELINE_VERSION = stringKey("hop.pipeline.version");

  public static final AttributeKey<String> TRANSFORM_PLUGIN_ID =
      stringKey("hop.transform.plugin_id");
}
