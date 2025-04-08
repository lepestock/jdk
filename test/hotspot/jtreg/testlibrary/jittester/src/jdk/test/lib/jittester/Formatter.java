/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.test.lib.jittester;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import jdk.test.lib.jittester.arrays.ArrayCreation;
import jdk.test.lib.jittester.arrays.ArrayElement;
import jdk.test.lib.jittester.arrays.ArrayExtraction;
import jdk.test.lib.jittester.classes.ClassDefinitionBlock;
import jdk.test.lib.jittester.classes.Interface;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.classes.MainKlass;
import jdk.test.lib.jittester.functions.ArgumentDeclaration;
import jdk.test.lib.jittester.functions.ConstructorDefinition;
import jdk.test.lib.jittester.functions.ConstructorDefinitionBlock;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.functions.FunctionDeclaration;
import jdk.test.lib.jittester.functions.FunctionDeclarationBlock;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionDefinitionBlock;
import jdk.test.lib.jittester.functions.FunctionRedefinition;
import jdk.test.lib.jittester.functions.FunctionRedefinitionBlock;
import jdk.test.lib.jittester.functions.Return;
import jdk.test.lib.jittester.functions.StaticConstructorDefinition;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.DoWhile;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.loops.While;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

public class Formatter {
    private static final JavaCodeVisitor VISITOR = new JavaCodeVisitor();

    public static String format(Collection<Symbol> collection) {
        StringBuilder sb = new StringBuilder("(");
        sb.append(collection.getClass().getSimpleName());
        for (Symbol item : collection) {
            sb.append(" ");
            sb.append(item.toString());
        }
        sb.append(")");
        return sb.toString();
    }

    public static String format(List<IRNode> nodes) {
        return nodes.stream()
            .map(Formatter::format)
            .collect(Collectors.joining("\n"));
    }

    public static String format(IRNode nodde) {
        return switch (nodde) {
            case ArgumentDeclaration node -> VISITOR.visit(node);
            case ArrayCreation node -> VISITOR.visit(node);
            case ArrayElement node -> VISITOR.visit(node);
            case ArrayExtraction node -> VISITOR.visit(node);
            case BinaryOperator node -> VISITOR.visit(node);
            case Block node -> VISITOR.visit(node);
            case Break node -> VISITOR.visit(node);
            case CastOperator node -> VISITOR.visit(node);
            case ClassDefinitionBlock node -> VISITOR.visit(node);
            case ConstructorDefinition node -> VISITOR.visit(node);
            case ConstructorDefinitionBlock node -> VISITOR.visit(node);
            case Continue node -> VISITOR.visit(node);
            case CounterInitializer node -> VISITOR.visit(node);
            case CounterManipulator node -> VISITOR.visit(node);
            case Declaration node -> VISITOR.visit(node);
            case DoWhile node -> VISITOR.visit(node);
            case For node -> VISITOR.visit(node);
            case Function node -> VISITOR.visit(node);
            case FunctionDeclaration node -> VISITOR.visit(node);
            case FunctionDeclarationBlock node -> VISITOR.visit(node);
            case FunctionDefinition node -> VISITOR.visit(node);
            case FunctionDefinitionBlock node -> VISITOR.visit(node);
            case FunctionRedefinition node -> VISITOR.visit(node);
            case FunctionRedefinitionBlock node -> VISITOR.visit(node);
            case If node -> VISITOR.visit(node);
            case Initialization node -> VISITOR.visit(node);
            case Interface node -> VISITOR.visit(node);
            case Klass node -> VISITOR.visit(node);
            case Literal node -> VISITOR.visit(node);
            case LocalVariable node -> VISITOR.visit(node);
            case LoopingCondition node -> VISITOR.visit(node);
            case MainKlass node -> VISITOR.visit(node);
            case NonStaticMemberVariable node -> VISITOR.visit(node);
            case Nothing node -> VISITOR.visit(node);
            case PrintVariables node -> VISITOR.visit(node);
            case Return node -> VISITOR.visit(node);
            case Throw node -> VISITOR.visit(node);
            case Statement node -> VISITOR.visit(node);
            case StaticConstructorDefinition node -> VISITOR.visit(node);
            case StaticMemberVariable node -> VISITOR.visit(node);
            case Switch node -> VISITOR.visit(node);
            case TernaryOperator node -> VISITOR.visit(node);
            case TypeArray node -> VISITOR.visit(node);
            case Type node -> VISITOR.visit(node);
            case UnaryOperator node -> VISITOR.visit(node);
            case VariableDeclaration node -> VISITOR.visit(node);
            case VariableDeclarationBlock node -> VISITOR.visit(node);
            case While node -> VISITOR.visit(node);
            case CatchBlock node -> VISITOR.visit(node);
            case TryCatchBlock node -> VISITOR.visit(node);
            default -> throw new Error("Unsupported node: " + nodde);
        };
    }

}
