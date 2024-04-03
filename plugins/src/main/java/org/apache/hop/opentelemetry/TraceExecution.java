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

import org.apache.hop.core.Const;
import org.apache.hop.core.IExtensionData;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.workflow.action.IAction;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;


public class TraceExecution {
  
  public static final String VARIABLE_HOP_PROJECT_NAME = "HOP_PROJECT_NAME";
  public static final String VARIABLE_HOP_ENVIRONMENT_NAME = "HOP_ENVIRONMENT_NAME";
  
  public static final String SPAN = "opentelemetry.span";
  
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
  
  public void addProjectAndEnvironment(IVariables variables, Span span) {
    span.setAttribute(HopAttributes.SERVICE_PLATFORM_RUNTIME, Const.getHopPlatformRuntime());
    String project = variables.getVariable(VARIABLE_HOP_PROJECT_NAME);
    if ( !Utils.isEmpty(project) ) {
      span.setAttribute(HopAttributes.HOP_PROJECT, project);
    }
    String environment = variables.getVariable(VARIABLE_HOP_ENVIRONMENT_NAME);
    if ( !Utils.isEmpty(environment) ) {
      span.setAttribute(HopAttributes.HOP_ENVIRONMENT, environment);
    }
  }
}
