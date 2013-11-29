/*
 * SonarQube Flex Plugin
 * Copyright (C) 2010 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.flex.checks;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.squid.checks.SquidCheck;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.flex.FlexGrammar;
import org.sonar.flex.checks.utils.Clazz;
import org.sonar.sslr.parser.LexerlessGrammar;

import javax.annotation.Nullable;
import java.util.Stack;

@Rule(
  key = "S1467",
  priority = Priority.BLOCKER)
@BelongsToProfile(title = CheckList.SONAR_WAY_PROFILE, priority = Priority.BLOCKER)
public class ConstructorCallsDispatchEventCheck extends SquidCheck<LexerlessGrammar> {

  boolean isInClass;
  private Stack<ClassState> classStack = new Stack<ClassState>();

  class ClassState {
    String className;
    private boolean isInContructor;

    public ClassState (String className) {
      this.className = className;
    }
  }

  // TODO: Nested class

  @Override
  public void init() {
    subscribeTo(
      FlexGrammar.CLASS_DEF,
      FlexGrammar.FUNCTION_DEF,
      FlexGrammar.PRIMARY_EXPR);
  }

  @Override
  public void visitFile(@Nullable AstNode astNode) {
    classStack.clear();
  }

  @Override
  public void visitNode(AstNode astNode) {
    if (astNode.is(FlexGrammar.CLASS_DEF)) {
      isInClass = true;
      String className = astNode
        .getFirstChild(FlexGrammar.CLASS_NAME)
        .getFirstChild(FlexGrammar.CLASS_IDENTIFIERS)
        .getLastChild().getTokenValue();
      classStack.push(new ClassState(className));
    }
    else if (isConstructor(astNode)) {
      classStack.peek().isInContructor = true;
    } else if (isCallToDispatchEventInConstructor(astNode)) {
      getContext().createLineViolation(this, "Remove this event dispatch from the {0} constructor", astNode, classStack.peek().className);
    }
  }

  private boolean isConstructor(AstNode astNode) {
    return isInClass && astNode.is(FlexGrammar.FUNCTION_DEF) && Clazz.isConstructor(astNode, classStack.peek().className);
  }

  private boolean  isCallToDispatchEventInConstructor(AstNode astNode) {
    return isInClass && classStack.peek().isInContructor && astNode.is(FlexGrammar.PRIMARY_EXPR) && isCallToDispatchEvent(astNode);
  }

  private static boolean isCallToDispatchEvent(AstNode primaryExpr) {
    return "dispatchEvent".equals(primaryExpr.getTokenValue())
      && primaryExpr.getNextAstNode().is(FlexGrammar.ARGUMENTS)
      && primaryExpr.getNextAstNode().getFirstChild(FlexGrammar.LIST_EXPRESSION) != null;
  }

  @Override
  public void leaveNode(AstNode astNode) {
    if (isInClass && classStack.peek().isInContructor && astNode.is(FlexGrammar.FUNCTION_DEF)) {
      classStack.peek().isInContructor = false;
    } else if (isInClass && astNode.is(FlexGrammar.CLASS_DEF)) {
      classStack.pop();
      isInClass = classStack.empty() ? false : true;
    }
  }
}