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
package com.ibm.wala.cfg;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.ExceptionHandler;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.Instruction;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.Exceptions;
import com.ibm.wala.util.ShrikeUtil;
import com.ibm.wala.util.collections.ArrayIterator;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.Trace;
import com.ibm.wala.util.graph.impl.NodeWithNumber;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.WarningSet;

/**
 * 
 * A graph of basic blocks.
 * 
 * @author sfink
 * @author roca
 */
public class ShrikeCFG extends AbstractCFG {

  private static final boolean DEBUG = false;

  private int[] instruction2Block;

  private final WarningSet warnings;

  private final ShrikeCTMethod method;

  /**
   * Cache this here for efficiency
   */
  private final int hashBase;

  /**
   * Set of Shrike ExceptionHandler objects that cover this method.
   */
  final private Set<ExceptionHandler> exceptionHandlers = HashSetFactory.make(10);

  public ShrikeCFG(ShrikeCTMethod method, WarningSet warnings) throws IllegalArgumentException {
    super(method);
    if (method == null) {
      throw new IllegalArgumentException("method cannot be null");
    }
    this.method = method;
    this.hashBase = method.hashCode() * 9967;
    this.warnings = warnings;
    makeBasicBlocks();
    init();
    computeI2BMapping();
    computeEdges();
  }

  @Override
  public int hashCode() {
    return 9511 * getMethod().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof ShrikeCFG) && getMethod().equals(((ShrikeCFG) o).getMethod());
  }

  public IInstruction[] getInstructions() {
    try {
      return method.getInstructions();
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
      return null;
    }
  }

  /**
   * Compute a mapping from instruction to basic block. Also, compute the blocks
   * that end with a 'normal' return.
   */
  private void computeI2BMapping() {
    instruction2Block = new int[getInstructions().length];
    for (Iterator it = iterator(); it.hasNext();) {
      final BasicBlock b = (BasicBlock) it.next();
      for (int j = b.getFirstInstructionIndex(); j <= b.getLastInstructionIndex(); j++) {
        instruction2Block[j] = getNumber(b);
      }
    }
  }

  /**
   * Compute outgoing edges in the control flow graph.
   */
  private void computeEdges() {
    for (Iterator it = iterator(); it.hasNext();) {
      BasicBlock b = (BasicBlock) it.next();
      if (b.equals(exit())) {
        continue;
      } else if (b.equals(entry())) {
        addNormalEdge(b, getBlockForInstruction(0));
      } else {
        b.computeOutgoingEdges();
      }
    }
  }

  /**
   * Method getBasicBlockStarts, stolen from ShrikeBT verifier
   */
  private void makeBasicBlocks() {
    ExceptionHandler[][] handlers;
    try {
      handlers = method.getHandlers();
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
      handlers = null;
    }
    boolean[] r = new boolean[getInstructions().length];
    boolean[] catchers = new boolean[getInstructions().length];
    // we initially start with both the entry and exit block.
    int blockCount = 2;

    // Compute r so r[i] == true iff instruction i begins a basic block.
    // While doing so count the number of blocks.
    r[0] = true;
    Instruction[] instructions = (Instruction[]) getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      int[] targets = instructions[i].getBranchTargets();

      // if there are any targets, then break the basic block here.
      // also break the basic block after a return
      if (targets.length > 0 || !instructions[i].isFallThrough()) {
        if (i + 1 < instructions.length && !r[i + 1]) {
          r[i + 1] = true;
          blockCount++;
        }
      }

      for (int j = 0; j < targets.length; j++) {
        if (!r[targets[j]]) {
          r[targets[j]] = true;
          blockCount++;
        }
      }
      if (Exceptions.isPEI(instructions[i])) {
        ExceptionHandler[] hs = handlers[i];
        // break the basic block here.
        if (i + 1 < instructions.length && !r[i + 1]) {
          r[i + 1] = true;
          blockCount++;
        }
        if (hs != null && hs.length > 0) {
          for (int j = 0; j < hs.length; j++) {
            exceptionHandlers.add(hs[j]);
            if (!r[hs[j].getHandler()]) {
              // we have not discovered the catch block yet.
              // form a new basic block
              r[hs[j].getHandler()] = true;
              blockCount++;
            }
            catchers[hs[j].getHandler()] = true;
          }
        }
      }
    }

    BasicBlock entry = new BasicBlock(-1);
    addNode(entry);

    int j = 1;
    for (int i = 0; i < r.length; i++) {
      if (r[i]) {
        BasicBlock b = new BasicBlock(i);
        addNode(b);
        if (catchers[i]) {
          setCatchBlock(j);
        }
        j++;
      }
    }

    BasicBlock exit = new BasicBlock(-1);
    addNode(exit);
  }

  /**
   * Return an instruction's basic block in the CFG given the index of the
   * instruction in the CFG's instruction array.
   */
  public IBasicBlock getBlockForInstruction(int index) {
    return getNode(instruction2Block[index]);
  }

  public final class BasicBlock extends NodeWithNumber implements IBasicBlock {

    /**
     * The number of the ShrikeBT instruction that begins this block.
     */
    final private int startIndex;

    public BasicBlock(int startIndex) {
      this.startIndex = startIndex;
    }

    public boolean isCatchBlock() {
      return ShrikeCFG.this.isCatchBlock(getNumber());
    }

    /**
     * Method computeOutgoingEdges.
     */
    private void computeOutgoingEdges() {
      if (DEBUG) {
        Trace.println("Block " + this + ": computeOutgoingEdges()");
      }

      Instruction last = (Instruction) getInstructions()[getLastInstructionIndex()];
      int[] targets = last.getBranchTargets();
      for (int i = 0; i < targets.length; i++) {
        BasicBlock b = (BasicBlock) getBlockForInstruction(targets[i]);
        addNormalEdgeTo(b);
      }
      addExceptionalEdges(last);
      if (last.isFallThrough()) {
        BasicBlock next = (BasicBlock) getNode(getNumber() + 1);
        addNormalEdgeTo(next);
      }
      if (last instanceof ReturnInstruction) {
        // link each return instrution to the exit block.
        BasicBlock exit = (BasicBlock) exit();
        addNormalEdgeTo(exit);
      }
    }

    /**
     * Add any exceptional edges generated by the last instruction in a basic
     * block.
     * 
     * @param last
     *          the last instruction in a basic block.
     */
    private void addExceptionalEdges(Instruction last) {
      IClassHierarchy cha = getMethod().getClassHierarchy();
      if (Exceptions.isPEI(last)) {
        Collection<TypeReference> exceptionTypes = null;
        boolean goToAllHandlers = false;

        ExceptionHandler[] hs = getExceptionHandlers();
        if (last.getOpcode() == OP_athrow) {
          // this class does not have the type information needed
          // to determine what the athrow throws. So, add an
          // edge to all reachable handlers. Better information can
          // be obtained later with SSA type propagation.
          // TODO: consider pruning to only the exception types that
          // this method either catches or allocates, since these are
          // the only types that can flow to an athrow.
          goToAllHandlers = true;
        } else {
          if (hs != null && hs.length > 0) {
            exceptionTypes = Exceptions.getExceptionTypes(getMethod().getDeclaringClass().getReference().getClassLoader(), last,
                cha, warnings);
          }
        }

        if (hs != null && hs.length > 0) {
          // found a handler for this PEI

          // create a mutable copy
          if (!goToAllHandlers) {
            exceptionTypes = new HashSet<TypeReference>(exceptionTypes);
          }

          for (int j = 0; j < hs.length; j++) {
            if (DEBUG) {
              Trace.println(" handler " + hs[j]);
            }
            BasicBlock b = (BasicBlock) getBlockForInstruction(hs[j].getHandler());
            if (DEBUG) {
              Trace.println(" target " + b);
            }
            if (goToAllHandlers) {
              // add an edge to the catch block.
              addExceptionalEdgeTo(b);
            } else {
              TypeReference caughtException = null;

              if (hs[j].getCatchClass() != null) {
                ClassLoaderReference loader = ShrikeCFG.this.getMethod().getDeclaringClass().getReference().getClassLoader();
                caughtException = ShrikeUtil.makeTypeReference(loader, hs[j].getCatchClass());
                IClass caughtClass = cha.lookupClass(caughtException);
                if (caughtClass == null) {
                  // conservatively add the edge, and raise a warning
                  addExceptionalEdgeTo(b);
                  warnings.add(FailedExceptionResolutionWarning.create(caughtException));
                  // null out caughtException, to avoid attempting to process it
                  caughtException = null;
                }
              }
              if (caughtException == null) {
                // this means that the handler catches all exceptions.
                // add the edge and null out all types
                addExceptionalEdgeTo(b);
                exceptionTypes.clear();
              } else {
                // the set "caught" should be the set of exceptions that MUST
                // have been caught
                // by the handlers in scope
                HashSet<TypeReference> caught = HashSetFactory.make(exceptionTypes.size());
                // check if we should add an edge to the catch block.
                for (Iterator it = exceptionTypes.iterator(); it.hasNext();) {
                  TypeReference t = (TypeReference) it.next();
                  if (t != null) {
                    IClass klass = cha.lookupClass(t);
                    if (klass == null) {
                      warnings.add(FailedExceptionResolutionWarning.create(caughtException));
                      // conservatively add an edge
                      addExceptionalEdgeTo(b);
                    } else {
                      IClass caughtClass = cha.lookupClass(caughtException);
                      if (cha.isSubclassOf(klass, caughtClass) || cha.isSubclassOf(caughtClass, klass)) {
                        // add the edge and null out the type from the array
                        addExceptionalEdgeTo(b);
                        if (cha.isSubclassOf(klass, caughtClass)) {
                          caught.add(t);
                        }
                      }
                    }
                  }
                }
                exceptionTypes.removeAll(caught);
              }
            }
          }
          // if needed, add an edge to the exit block.
          if (exceptionTypes == null || !exceptionTypes.isEmpty()) {
            BasicBlock exit = (BasicBlock) exit();
            addExceptionalEdgeTo(exit);
          }
        } else {
          // found no handler for this PEI ... link to the exit block.
          BasicBlock exit = (BasicBlock) exit();
          addExceptionalEdgeTo(exit);
        }
      }
    }

    private ExceptionHandler[] getExceptionHandlers() {
      ExceptionHandler[][] handlers;
      try {
        handlers = method.getHandlers();
      } catch (InvalidClassFileException e) {
        e.printStackTrace();
        Assertions.UNREACHABLE();
        handlers = null;
      }
      ExceptionHandler[] hs = handlers[getLastInstructionIndex()];
      return hs;
    }

    // /**
    // * @return true iff exception extends RuntimeException or Error
    // */
    // private boolean isUndeclaredType(IClass exception) {
    // IClass re = cha.lookupClass(TypeReference.JavaLangRuntimeException);
    // IClass error = cha.lookupClass(TypeReference.JavaLangError);
    // boolean result = cha.isSubclassOf(exception, re) ||
    // cha.isSubclassOf(exception, error);
    // return result;
    // }

    /**
     * @param b
     */
    private void addNormalEdgeTo(BasicBlock b) {
      addNormalEdge(this, b);
    }

    /**
     * @param b
     */
    private void addExceptionalEdgeTo(BasicBlock b) {
      addExceptionalEdge(this, b);
    }

    /**
     * Method getLastInstructionIndex.
     * 
     * @return int
     */
    public int getLastInstructionIndex() {

      if (this == entry() || this == exit()) {
        // these are the special end blocks
        return -2;
      }
      if (getNumber() == (getMaxNumber() - 1)) {
        // this is the last non-exit block
        return getInstructions().length - 1;
      } else {
        BasicBlock next = (BasicBlock) getNode(getNumber() + 1);
        return next.getFirstInstructionIndex() - 1;
      }
    }

    /**
     * Method getFirstInstructionIndex.
     */
    public int getFirstInstructionIndex() {
      return startIndex;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
      return "BB[Shrike]" + getNumber() + " - " + method.getDeclaringClass().getReference().getName() + "." + method.getName();
    }

    /*
     * @see com.ibm.wala.cfg.IBasicBlock#isExitBlock()
     */
    public boolean isExitBlock() {
      return this == ShrikeCFG.this.exit();
    }

    /*
     * @see com.ibm.wala.cfg.IBasicBlock#isEntryBlock()
     */
    public boolean isEntryBlock() {
      return this == ShrikeCFG.this.entry();
    }

    /*
     * @see com.ibm.wala.cfg.IBasicBlock#getMethod()
     */
    public IMethod getMethod() {
      return ShrikeCFG.this.getMethod();
    }

    @Override
    public int hashCode() {
      return hashBase + getNumber();
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof BasicBlock) && ((BasicBlock) o).getMethod().equals(getMethod())
          && ((BasicBlock) o).getNumber() == getNumber();
    }

    /*
     * @see com.ibm.wala.cfg.IBasicBlock#getNumber()
     */
    public int getNumber() {
      return getGraphNodeId();
    }

    public Iterator<IInstruction> iterator() {
      return new ArrayIterator<IInstruction>(getInstructions(), getFirstInstructionIndex(), getLastInstructionIndex());
    }
  }

  /**
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuffer s = new StringBuffer("");
    for (Iterator it = iterator(); it.hasNext();) {
      BasicBlock bb = (BasicBlock) it.next();
      s.append("BB").append(getNumber(bb)).append("\n");
      for (int j = bb.getFirstInstructionIndex(); j <= bb.getLastInstructionIndex(); j++) {
        s.append("  ").append(j).append("  ").append(getInstructions()[j]).append("\n");
      }

      Iterator<IBasicBlock> succNodes = getSuccNodes(bb);
      while (succNodes.hasNext()) {
        s.append("    -> BB").append(getNumber(succNodes.next())).append("\n");
      }
    }
    return s.toString();
  }

  /**
   * Method getMaxStackHeight.
   * 
   * @return int
   */
  public int getMaxStackHeight() {
    return method.getMaxStackHeight();
  }

  /**
   * Method getMaxLocals
   * 
   * @return int
   */
  public int getMaxLocals() {
    return method.getMaxLocals();
  }

  /**
   * Returns the exceptionHandlers.
   * 
   * @return Set
   */
  public Set<ExceptionHandler> getExceptionHandlers() {
    return exceptionHandlers;
  }

  /*
   * @see com.ibm.wala.cfg.ControlFlowGraph#getProgramCounter(int)
   */
  public int getProgramCounter(int index) {
    try {
      return method.getBytecodeIndex(index);
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
      return -1;
    }
  }

  /**
   * @author sfink
   * 
   * A warning when we fail to resolve the type of an exception
   */
  private static class FailedExceptionResolutionWarning extends Warning {

    final TypeReference T;

    FailedExceptionResolutionWarning(TypeReference T) {
      super(Warning.MODERATE);
      this.T = T;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + T;
    }

    public static FailedExceptionResolutionWarning create(TypeReference T) {
      return new FailedExceptionResolutionWarning(T);
    }
  }
}
