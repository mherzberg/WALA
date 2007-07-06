/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.client.impl;

import com.ibm.wala.client.CallGraphBuilderFactory;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.IClassHierarchy;

/**
 * 
 * A factory to create call graph builders using RTA
 * 
 * @author sfink
 */
public class RTABuilderFactory implements CallGraphBuilderFactory {

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.wala.j2ee.client.CallGraphBuilderFactory#make(com.ibm.wala.ipa.callgraph.AnalysisOptions,
   *      com.ibm.wala.ipa.cha.IClassHierarchy, java.lang.ClassLoader,
   *      com.ibm.wala.j2ee.J2EEAnalysisScope,
   *      com.ibm.wala.util.warnings.WarningSet, boolean)
   */
  public CallGraphBuilder make(AnalysisOptions options,
			       IClassHierarchy cha,
			       AnalysisScope scope,
			       boolean keepPointsTo)
  {
    if (cha == null) {
      throw new IllegalArgumentException("cha is null");
    }
    return Util.makeRTABuilder(options, cha, scope);
  }
}
