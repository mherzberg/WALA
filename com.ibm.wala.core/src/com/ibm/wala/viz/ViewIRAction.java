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
package com.ibm.wala.viz;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.util.warnings.WalaException;

/**
 * 
 * An SWT action that spawns spawns a ghostview to see the IR for a call graph
 * node which is the current selection in a tree viewer.
 * 
 * @author sfink
 */
public class ViewIRAction extends Action {
  /**
   * Governing tree viewer
   */
  private final SWTTreeViewer viewer;

  /**
   * Governing call graph
   */
  private final CallGraph cg;

  /**
   * name of postscript file to generate
   */
  private final String psFile;

  /**
   * name of dot file to generate
   */
  private final String dotFile;

  /**
   * path to dot.exe
   */
  private final String dotExe;

  /**
   * path to ghostview executable
   */
  private final String gvExe;

  /**
   * @param viewer
   *            Governing tree viewer
   * @param cg
   *            Governing call graph
   */
  public ViewIRAction(SWTTreeViewer viewer, CallGraph cg, String psFile, String dotFile, String dotExe, String gvExe) {
    this.viewer = viewer;
    this.cg = cg;
    this.psFile = psFile;
    this.dotFile = dotFile;
    this.dotExe = dotExe;
    this.gvExe = gvExe;
    setText("View IR");
  }

  /*
   * @see org.eclipse.jface.action.IAction#run()
   */
  @Override
  public void run() {

    IR ir = getIRForSelection();
    // spawn the viewer
    System.err.println("Spawn IR Viewer for " + ir.getMethod());
    try {
      GhostviewUtil.ghostviewIR(cg.getClassHierarchy(), ir, psFile, dotFile, dotExe, gvExe);
    } catch (WalaException e) {
      e.printStackTrace();
    }
  }

  protected IR getIRForSelection() {
    // we assume the tree viewer's current selection is a CGNode
    IStructuredSelection selection = viewer.getSelection();
    if (selection.size() != 1) {
      throw new UnsupportedOperationException("did not expect selection of size " + selection.size());
    }
    CGNode first = (CGNode) selection.getFirstElement();

    // get the IR for the node
    return first.getIR();
  }

  protected CGNode getNodeForSelection() {
    // we assume the tree viewer's current selection is a CGNode
    IStructuredSelection selection = viewer.getSelection();
    if (selection.size() != 1) {
      throw new UnsupportedOperationException("did not expect selection of size " + selection.size());
    }
    CGNode first = (CGNode) selection.getFirstElement();
    return first;
  }

  protected SWTTreeViewer getViewer() {
    return viewer;
  }

  protected CallGraph getCg() {
    return cg;
  }

  protected String getDotExe() {
    return dotExe;
  }

  protected String getDotFile() {
    return dotFile;
  }

  protected String getGvExe() {
    return gvExe;
  }

  protected String getPsFile() {
    return psFile;
  }
}