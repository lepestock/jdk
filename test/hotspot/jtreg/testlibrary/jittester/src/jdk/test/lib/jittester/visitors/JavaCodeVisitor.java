/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester.visitors;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Break;
import jdk.test.lib.jittester.CastOperator;
import jdk.test.lib.jittester.CatchBlock;
import jdk.test.lib.jittester.Continue;
import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.If;
import jdk.test.lib.jittester.Initialization;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.Operator;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.PrintVariables;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Switch;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.TernaryOperator;
import jdk.test.lib.jittester.Throw;
import jdk.test.lib.jittester.TryCatchBlock;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.UnaryOperator;
import jdk.test.lib.jittester.VariableBase;
import jdk.test.lib.jittester.VariableDeclaration;
import jdk.test.lib.jittester.VariableDeclarationBlock;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.arrays.ArrayCreation;
import jdk.test.lib.jittester.arrays.ArrayElement;
import jdk.test.lib.jittester.arrays.ArrayExtraction;
import jdk.test.lib.jittester.classes.ClassDefinitionBlock;
import jdk.test.lib.jittester.classes.Interface;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.classes.MainKlass;
import jdk.test.lib.jittester.classes.ValueKlass;
import jdk.test.lib.jittester.functions.ArgumentDeclaration;
import jdk.test.lib.jittester.functions.ConstructorDefinition;
import jdk.test.lib.jittester.functions.ConstructorDefinitionBlock;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.functions.FunctionDeclaration;
import jdk.test.lib.jittester.functions.FunctionDeclarationBlock;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionDefinitionBlock;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.functions.FunctionRedefinition;
import jdk.test.lib.jittester.functions.FunctionRedefinitionBlock;
import jdk.test.lib.jittester.functions.Return;
import jdk.test.lib.jittester.functions.StaticConstructorDefinition;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.DoWhile;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.loops.While;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.FixedTrees;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PrintingUtils;

public class JavaCodeVisitor implements Visitor<String> {
    private Set<String> debugWrapGeneFilter = null;

    public static String funcAttributes(FunctionInfo fi) {
        String attrs = attributes(fi);
        if (fi.isSynchronized()) {
            attrs += "synchronized ";
        }
        return attrs;
    }

    public static String attributes(Symbol s) {
        String attrs = "";
        if (s.isPrivate()) {
            attrs += "private ";
        }
        if (s.isProtected()) {
            attrs += "protected ";
        }
        if (s.isPublic()) {
            attrs += "public ";
        }
        if (s.isFinal()) {
            attrs += "final ";
        }
        if (s.isStatic()) {
            attrs += "static ";
        }
        return attrs;
    }

    private String operatorToJaveCode(OperatorKind operationKind) {
       switch (operationKind) {
           case COMPOUND_ADD:
               return  "+=";
           case COMPOUND_SUB:
               return "-=";
           case COMPOUND_MUL:
               return "*=";
           case COMPOUND_DIV:
               return "/=";
           case COMPOUND_MOD:
               return "%=";
           case COMPOUND_AND:
               return "&=";
           case COMPOUND_OR:
               return "|=";
           case COMPOUND_XOR:
               return "^=";
           case COMPOUND_SHR:
               return ">>=";
           case COMPOUND_SHL:
               return "<<=";
           case COMPOUND_SAR:
               return ">>>=";
           case ASSIGN:
               return "=";
           case OR:
               return "||";
           case BIT_OR:
               return "|";
           case BIT_XOR:
               return "^";
           case AND:
               return "&&";
           case BIT_AND:
               return "&";
           case EQ:
               return "==";
           case NE:
               return "!=";
           case GT:
               return ">";
           case LT:
               return "<";
           case GE:
               return ">=";
           case LE:
               return "<=";
           case SHR:
               return ">>";
           case SHL:
               return "<<";
           case SAR:
               return ">>>";
           case ADD:
           case STRADD:
               return "+";
           case SUB:
               return "-";
           case MUL:
               return "*";
           case DIV:
               return "/";
           case MOD:
               return "%";
           case NOT:
               return "!";
           case BIT_NOT:
               return "~";
           case UNARY_PLUS:
               return "+";
           case UNARY_MINUS:
               return "-";
           case PRE_DEC:
           case POST_DEC:
               return "--";
           case PRE_INC:
           case POST_INC:
               return "++";
           default:
               throw new IllegalArgumentException("Unkown operator kind " + operationKind);
       }
    }

    private String expressionToJavaCode(Operator t, IRNode p, Operator.Order o) {
        String result;
        try {
            if ((o == Operator.Order.LEFT && ((Operator) p).getPriority() < t.getPriority())
                    || (o == Operator.Order.RIGHT && ((Operator) p).getPriority() <= t.getPriority())) {
                result = "(" + p.accept(this)+ ")";
            } else {
                result = p.accept(this);
            }
        } catch (Exception e) {
            result = p.accept(this);
        }
        return result;
    }

    @Override
    public String visit(ArgumentDeclaration node) {
        VariableInfo vi = node.variableInfo;
        return attributes(vi) + vi.type.accept(this) + " " + vi.name;
    }

    @Override
    public String visit(ArrayCreation node) {
        Type arrayElemType = node.getArrayType().type;
        String type = arrayElemType.accept(this);
        String name = node.getVariable().getName();
        StringBuilder code = new StringBuilder()
                .append(node.getVariable().accept(this))
                .append(";\n")
                .append(PrintingUtils.align(node.getParent().getLevel()))
                .append(name)
                .append(" = new ")
                .append(type);
        code.append(node.getChildren().stream()
                .map(p -> p.accept(this))
                .collect(Collectors.joining("][", "[", "]")));
        code.append(";\n");
        if (!TypeList.isBuiltIn(arrayElemType)) {
            code.append(PrintingUtils.align(node.getParent().getLevel()))
                .append("java.util.Arrays.fill(")
                .append(name)
                .append(", new ")
                .append(type)
                .append("());\n");
        }
        return code.toString();
    }

    @Override
    public String visit(ArrayElement node) {
        IRNode array = node.getChild(0);
        StringBuilder code = new StringBuilder();
        if (array instanceof VariableBase || array instanceof Function) {
            code.append(array.accept(this));
        } else {
            code.append("(")
                .append(array.accept(this))
                .append(")");
        }
        code.append(node.getChildren().stream()
                .skip(1)
                .map(c -> c.accept(this))
                .collect(Collectors.joining("][", "[", "]")));
        return code.toString();
    }

    @Override
    public String visit(ArrayExtraction node) {
        IRNode array = node.getChild(0);
        StringBuilder code = new StringBuilder();
        if (array instanceof VariableBase || array instanceof Function) {
            code.append(array.accept(this));
        } else {
            code.append("(")
                .append(array.accept(this))
                .append(")");
        }
        code.append(node.getChildren().stream()
                .skip(1)
                .map(c -> c.accept(this))
                .collect(Collectors.joining("][", "[", "]")));
        return code.toString();
    }

    @Override
    public String visit(BinaryOperator node) {
        IRNode left = node.getChild(Operator.Order.LEFT.ordinal());
        IRNode right = node.getChild(Operator.Order.RIGHT.ordinal());
        if (left == null || right == null) {
            return "null";
        }
        return expressionToJavaCode(node, left, Operator.Order.LEFT)
               + " " + operatorToJaveCode(node.getOperationKind()) + " "
               + expressionToJavaCode(node, right, Operator.Order.RIGHT);
    }

    @Override
    public String visit(Block node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            String s = i.accept(this);
            if (!s.isEmpty()) {
                int level = node.getLevel();
                if (i instanceof Block) {
                    code.append(openBraceWithGene(level + 1, i))
                        .append(s)
                        .append(closeBraceWithGene(level + 1, i));
                } else {
                    code.append(PrintingUtils.align(level + 1))
                        .append(s);
                }
                code.append(addComplexityInfo(i));
                code.append("\n");
            }
        }
        return code.toString();
    }

    private static String geneComment(IRNode node) {
        if (Genome.isSourceDebugEnabled()
                && node instanceof Block
                && ((Block) node).hasBlockRngSeed()) {
            String token = Genome.formatBlockDebugToken((Block) node);
            if (!token.isEmpty()) {
                return "  // gene: " + token;
            }
        }
        return "";
    }

    private static String openBraceWithGene(int level, IRNode body) {
        return PrintingUtils.align(level) + "{" + geneComment(body) + "\n";
    }

    private static String closeBraceWithGene(int level, IRNode body) {
        return PrintingUtils.align(level) + "}" + geneComment(body);
    }

    private static String openBraceSuffixWithGene(IRNode body) {
        return "{" + geneComment(body) + "\n";
    }

    private static String closeBraceSuffixWithGene(IRNode body) {
        return "}" + geneComment(body);
    }

    private String addComplexityInfo(IRNode node) {
        if (ProductionParams.printComplexity.value()) {
            return " /* " + node.complexity() + " */";
        }
        return "";
    }

    @Override
    public String visit(Break node) {
        return "break;";
    }

    @Override
    public String visit(CastOperator node) {
        return "(" + node.getResultType().accept(this)+ ")"
                + expressionToJavaCode(node, node.getChild(0), Operator.Order.LEFT);
    }

    @Override
    public String visit(ClassDefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append("\n");
        }

        return code.toString();
    }

    @Override
    public String visit(ConstructorDefinition node) {
        String args = node.getChildren().stream()
                .skip(1)
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));
        IRNode body = node.getChild(0);
        StringBuilder code = new StringBuilder();
        code.append(funcAttributes(node.getFunctionInfo()))
            .append(node.getFunctionInfo().name)
            .append("(")
            .append(args)
            .append(")\n")
            .append(openBraceWithGene(node.getLevel() + 1, body))
            .append(body != null ? body.accept(this) : "")
            .append(closeBraceWithGene(node.getLevel() + 1, body));
        return code.toString();
    }

    @Override
    public String visit(ConstructorDefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(Continue node) {
        return "continue;";
    }

    @Override
    public String visit(CounterInitializer node) {
        VariableInfo vi = node.getVariableInfo();
        return vi.type.accept(this) + " " + vi.name + " = " + node.getChild(0).accept(this)+ ";";
    }

    @Override
    public String visit(CounterManipulator node) {
        return node.getChild(0).accept(this);
    }

    @Override
    public String visit(Declaration node) {
        return node.getChild(0).accept(this)+ ";";
    }

    @Override
    public String visit(DoWhile node) {
        IRNode header = node.getChild(DoWhile.DoWhilePart.HEADER.ordinal());
        IRNode body1 = node.getChild(DoWhile.DoWhilePart.BODY1.ordinal());
        IRNode body2 = node.getChild(DoWhile.DoWhilePart.BODY2.ordinal());
        StringBuilder code = new StringBuilder();
        Loop loop = node.getLoop();
        int level = node.getLevel();
        code.append(loop.initialization.accept(this))
            .append("\n")
            .append(header.accept(this))
            .append(PrintingUtils.align(level))
            .append("do\n")
            .append(openBraceWithGene(level, body1))
            .append(body1.accept(this))
            .append(PrintingUtils.align(level + 1))
            .append(loop.manipulator.accept(this))
            .append(";\n")
            .append(body2.accept(this))
            .append(closeBraceWithGene(level, body1))
            .append(" while (")
            .append(loop.condition.accept(this))
            .append(");");
        return code.toString();
    }

    @Override
    public String visit(For node) {
        IRNode header = node.getChild(For.ForPart.HEADER.ordinal());
        IRNode statement1 = node.getChild(For.ForPart.STATEMENT1.ordinal());
        IRNode statement2 = node.getChild(For.ForPart.STATEMENT2.ordinal());
        IRNode body1 = node.getChild(For.ForPart.BODY1.ordinal());
        IRNode body2 = node.getChild(For.ForPart.BODY2.ordinal());
        IRNode body3 = node.getChild(For.ForPart.BODY3.ordinal());
        Loop loop = node.getLoop();
        StringBuilder code = new StringBuilder();
        int level = node.getLevel();
        code.append(loop.initialization.accept(this))
            .append("\n")
            .append(header.accept(this))
            .append(PrintingUtils.align(level))
            .append("for (")
            .append(statement1.accept(this))
            .append("; ")
            .append(loop.condition.accept(this))
            .append("; ")
            .append(statement2.accept(this))
            .append(")\n")
            .append(openBraceWithGene(level, body1))
            .append(body1.accept(this))
            .append(PrintingUtils.align(level + 1))
            .append(loop.manipulator.accept(this))
            .append(";\n")
            .append(body2.accept(this))
            .append(body3.accept(this))
            .append(closeBraceWithGene(level, body1));
        return code.toString();
    }

    @Override
    public String visit(Function node) {
        FunctionInfo value = node.getValue();
        List<String> args = node.getChildren().stream()
                .skip(value.isStatic() || value.isConstructor() ? 0 : 1)
                .map(c -> c.accept(this))
                .collect(Collectors.toCollection(ArrayList::new));
        maybeWrapDebugMethodCallArg(node, value, args);
        String nameAndArgs = value.name + "(" + String.join(", ", args) + ")";
        String prefix = "";
        if (value.isStatic()) {
            if(!node.getOwner().equals(value.owner)) {
                prefix = value.owner.getName() + ".";
            }
        } else if (value.isConstructor()) {
            prefix = "new ";
        } else {
            IRNode object = node.getChild(0);
            String objectString = object.accept(this);
            if (!objectString.equals("this")) {
                 if (object instanceof VariableBase || object instanceof Function
                            || object instanceof Literal) {
                     prefix = objectString + ".";
                 } else {
                     prefix = "(" + objectString + ")" + ".";
                 }
            }
        }
        return prefix + nameAndArgs;
    }

    private void maybeWrapDebugMethodCallArg(Function node, FunctionInfo value, List<String> args) {
        if (!ProductionParams.debugMethodCallWrapEnabled.value()) {
            return;
        }
        String expressionGene = node.getExpressionGeneToken();
        if (!isDebugWrapGeneSelected(expressionGene)) {
            return;
        }
        if (!value.isStatic() || value.isConstructor() || args.isEmpty()) {
            return;
        }
        String target = ProductionParams.debugMethodCallWrapTarget.value();
        if (target == null || target.isBlank()) {
            return;
        }
        String ownerName = value.owner == null ? "" : value.owner.getName();
        String fullName = ownerName + "." + value.name;
        if (!target.equals(fullName)) {
            return;
        }
        int callArgStart = value.isStatic() || value.isConstructor() ? 0 : 1;
        int wrappedArgPos = args.size() >= 2 ? 1 : 0;
        int wrappedChildIndex = callArgStart + wrappedArgPos;
        if (wrappedChildIndex >= node.getChildren().size()) {
            return;
        }
        IRNode wrappedArgNode = node.getChild(wrappedChildIndex);
        if (!isAllowedWrappedArgKind(wrappedArgNode)) {
            return;
        }
        String debugMethod = args.size() >= 2 ? "debugLong" : "debugString";
        String wrapped = "jdk.test.lib.jittester.jtreg.Printer." + debugMethod + "(\""
                + escapeJavaString(expressionGene) + "\", " + args.get(wrappedArgPos) + ")";
        args.set(wrappedArgPos, wrapped);
    }

    private boolean isDebugWrapGeneSelected(String expressionGene) {
        if (expressionGene == null || expressionGene.isBlank()) {
            return false;
        }
        if (debugWrapGeneFilter == null) {
            debugWrapGeneFilter = parseDebugWrapGenes();
        }
        if (debugWrapGeneFilter.isEmpty()) {
            return false;
        }
        return debugWrapGeneFilter.contains(expressionGene);
    }

    private static Set<String> parseDebugWrapGenes() {
        Set<String> result = new HashSet<>();
        String raw = ProductionParams.debugMethodCallWrapGenes.value();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        int limit = Math.max(1, ProductionParams.debugMethodCallWrapGenesMax.value());
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            if (result.size() >= limit) {
                break;
            }
            String t = token == null ? "" : token.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.charAt(0) != 'E') {
                t = "E" + t;
            }
            result.add(t);
        }
        return result;
    }

    private boolean isAllowedWrappedArgKind(IRNode wrappedArgNode) {
        String kinds = ProductionParams.debugMethodCallWrapArgKinds.value();
        if (kinds == null || kinds.isBlank()) {
            return true;
        }
        String normalized = kinds.toLowerCase();
        if (normalized.contains("all")) {
            return true;
        }
        if (wrappedArgNode instanceof Literal && normalized.contains("literal")) {
            return true;
        }
        if (wrappedArgNode instanceof Operator && normalized.contains("operator")) {
            return true;
        }
        if (wrappedArgNode instanceof Function && normalized.contains("function")) {
            return true;
        }
        return wrappedArgNode instanceof VariableBase && normalized.contains("variable");
    }

    private static String escapeJavaString(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(c);
                    break;
            }
        }
        return escaped.toString();
    }

    @Override
    public String visit(FunctionDeclaration node) {
        String args = node.getChildren().stream()
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));

        FunctionInfo functionInfo = node.getFunctionInfo();
        return (functionInfo.owner.isInterface() ? "" : "abstract ")
                + funcAttributes(functionInfo) + functionInfo.type.accept(this)+ " "
                + functionInfo.name + "(" + args + ");";
    }

    @Override
    public String visit(FunctionDeclarationBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(FunctionDefinition node) {
        String args = node.getChildren().stream()
                .skip(2)
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));
        IRNode body = node.getChild(0);
        IRNode ret = node.getChild(1);
        FunctionInfo functionInfo = node.getFunctionInfo();
        return funcAttributes(functionInfo) + functionInfo.type.accept(this) + " " + functionInfo.name + "(" + args + ")" + "\n"
                + openBraceWithGene(node.getLevel() + 1, body)
                + body.accept(this)
                + (ret != null ? PrintingUtils.align(node.getLevel() + 2) + ret.accept(this) + "\n" : "")
                + closeBraceWithGene(node.getLevel() + 1, body) + "\n";
    }

    @Override
    public String visit(FunctionDefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(FunctionRedefinition node) {
        String args = node.getChildren().stream()
                .skip(2)
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));

        IRNode body = node.getChild(0);
        IRNode ret = node.getChild(1);
        int level = node.getLevel();
        FunctionInfo functionInfo = node.getFunctionInfo();
        return funcAttributes(functionInfo) + functionInfo.type.accept(this) + " " + functionInfo.name + "(" + args + ")" + "\n"
                + openBraceWithGene(level + 1, body)
                + body.accept(this)
                + (ret != null ? PrintingUtils.align(level + 2) + ret.accept(this) + "\n" : "")
                + closeBraceWithGene(level + 1, body);
    }

    @Override
    public String visit(FunctionRedefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(If node) {
        int level = node.getLevel();
        IRNode thenBody = node.getChild(If.IfPart.THEN.ordinal());
        String thenBlockString = openBraceWithGene(level, thenBody)
                                 + thenBody.accept(this)
                                 + closeBraceWithGene(level, thenBody);

        String elseBlockString = null;
        IRNode elseBody = node.getChild(If.IfPart.ELSE.ordinal());
        if (elseBody != null) {
            elseBlockString = openBraceWithGene(level, elseBody)
                              + elseBody.accept(this)
                              + closeBraceWithGene(level, elseBody);
        }

        return "if (" + node.getChild(If.IfPart.CONDITION.ordinal()).accept(this)+ ")\n"
            + thenBlockString + (elseBlockString != null ? "\n"
            + PrintingUtils.align(level) + "else\n" + elseBlockString : "");
    }

    @Override
    public String visit(Initialization node) {
        VariableInfo vi = node.getVariableInfo();
        return attributes(vi) + vi.type.accept(this)+ " " + vi.name + " = "
                + node.getChild(0).accept(this);
    }

    @Override
    public String visit(Interface node) {
        return "interface " + node.getName() + (node.getParentKlass() != null ? " extends "
                + node.getParentKlass().getName() : "") + " {\n"
                + (node.getChildren().size() > 0 ? node.getChild(0).accept(this) : "")
                + "}\n";
    }

    @Override
    public String visit(Klass node) {
        TypeKlass thisKlass = node.getThisKlass();
        String r = (ProductionParams.enableStrictFP.value() ? "strictfp " : "")
                + (thisKlass.isFinal() ? "final " : "")
                + (thisKlass.isAbstract() ? "abstract " : "")
                + "class " + node.getName()
                + (node.getParentKlass() != null && !node.getParentKlass().equals(TypeList.OBJECT)
                ? " extends " + node.getParentKlass().getName() : "");
        List<TypeKlass> interfaces = node.getInterfaces();
        r += interfaces.stream()
                .map(Type::getName)
                .collect(Collectors.joining(", ", (interfaces.isEmpty() ? "" : " implements "), ""));
        IRNode dataMembers = node.getChild(Klass.KlassPart.DATA_MEMBERS.ordinal());
        IRNode constructors = node.getChild(Klass.KlassPart.CONSTRUCTORS.ordinal());
        IRNode redefinedFunctions = node.getChild(Klass.KlassPart.REDEFINED_FUNCTIONS.ordinal());
        IRNode overridenFunctions = node.getChild(Klass.KlassPart.OVERRIDEN_FUNCTIONS.ordinal());
        IRNode memberFunctions = node.getChild(Klass.KlassPart.MEMBER_FUNCTIONS.ordinal());
        IRNode memberFunctionDecls = node.getChild(Klass.KlassPart.MEMBER_FUNCTIONS_DECLARATIONS.ordinal());
        IRNode printVariables = node.getChild(Klass.KlassPart.PRINT_VARIABLES.ordinal());
        r += " {\n"
             + (dataMembers != null ? (dataMembers.accept(this)+ "\n") : "")
             + (constructors != null ? (constructors.accept(this)+ "\n") : "")
             + (redefinedFunctions != null ? (redefinedFunctions.accept(this)+ "\n") : "")
             + (overridenFunctions != null ? (overridenFunctions.accept(this)+ "\n") : "")
             + (memberFunctionDecls != null ? (memberFunctionDecls.accept(this)+ "\n") : "")
             + (memberFunctions != null ? (memberFunctions.accept(this)+ "\n") : "")
             + printVariables.accept(this)
             + "}\n";
        return r;
    }

    @Override
    public String visit(ValueKlass node) {
        TypeKlass thisKlass = node.getThisKlass();
        String r = (ProductionParams.enableStrictFP.value() ? "strictfp " : "")
                + (thisKlass.isAbstract() ? "abstract " : "")
                + "value class " + node.getName()
                + (node.getParentKlass() != null && !node.getParentKlass().equals(TypeList.OBJECT)
                ? " extends " + node.getParentKlass().getName() : "");
        List<TypeKlass> interfaces = node.getInterfaces();
        r += interfaces.stream()
                .map(Type::getName)
                .collect(Collectors.joining(", ", (interfaces.isEmpty() ? "" : " implements "), ""));
        IRNode dataMembers = node.getChild(Klass.KlassPart.DATA_MEMBERS.ordinal());
        IRNode constructors = node.getChild(Klass.KlassPart.CONSTRUCTORS.ordinal());
        IRNode redefinedFunctions = node.getChild(Klass.KlassPart.REDEFINED_FUNCTIONS.ordinal());
        IRNode overridenFunctions = node.getChild(Klass.KlassPart.OVERRIDEN_FUNCTIONS.ordinal());
        IRNode memberFunctions = node.getChild(Klass.KlassPart.MEMBER_FUNCTIONS.ordinal());
        IRNode memberFunctionDecls = node.getChild(Klass.KlassPart.MEMBER_FUNCTIONS_DECLARATIONS.ordinal());
        IRNode printVariables = node.getChild(Klass.KlassPart.PRINT_VARIABLES.ordinal());
        r += " {\n"
             + (dataMembers != null ? (dataMembers.accept(this)+ "\n") : "")
             + (constructors != null ? (constructors.accept(this)+ "\n") : "")
             + (redefinedFunctions != null ? (redefinedFunctions.accept(this)+ "\n") : "")
             + (overridenFunctions != null ? (overridenFunctions.accept(this)+ "\n") : "")
             + (memberFunctionDecls != null ? (memberFunctionDecls.accept(this)+ "\n") : "")
             + (memberFunctions != null ? (memberFunctions.accept(this)+ "\n") : "")
             + printVariables.accept(this)
             + "}\n";
        return r;
    }

    @Override
    public String visit(Literal node) {
        Type resultType = node.getResultType();
        String typeName = resultType.getName();
        Object value = node.getValue();
        boolean boxedLong = "java.lang.Long".equals(typeName);
        if (resultType.equals(TypeList.LONG) || boxedLong) {
            String lit = value.toString() + "L";
            return boxedLong ? "java.lang.Long.valueOf(" + lit + ")" : lit;
        }
        boolean boxedFloat = "java.lang.Float".equals(typeName);
        if (resultType.equals(TypeList.FLOAT) || boxedFloat) {
            String lit = formatFloatLiteral(value);
            return boxedFloat ? "java.lang.Float.valueOf(" + lit + ")" : lit;
        }
        boolean boxedDouble = "java.lang.Double".equals(typeName);
        if (resultType.equals(TypeList.DOUBLE) || boxedDouble) {
            String lit = formatDoubleLiteral(value);
            return boxedDouble ? "java.lang.Double.valueOf(" + lit + ")" : lit;
        }
        boolean boxedChar = "java.lang.Character".equals(typeName);
        if (resultType.equals(TypeList.CHAR) || boxedChar) {
            String lit = "'" + escapeCharLiteral((Character) value) + "'";
            return boxedChar ? "java.lang.Character.valueOf(" + lit + ")" : lit;
        }
        boolean boxedShort = "java.lang.Short".equals(typeName);
        if (resultType.equals(TypeList.SHORT) || boxedShort) {
            String lit = "(short) " + value.toString();
            return boxedShort ? "java.lang.Short.valueOf(" + lit + ")" : lit;
        }
        boolean boxedByte = "java.lang.Byte".equals(typeName);
        if (resultType.equals(TypeList.BYTE) || boxedByte) {
            String lit = "(byte) " + value.toString();
            return boxedByte ? "java.lang.Byte.valueOf(" + lit + ")" : lit;
        }
        if ("java.lang.Integer".equals(typeName)) {
            return "java.lang.Integer.valueOf(" + value.toString() + ")";
        }
        if ("java.lang.Boolean".equals(typeName)) {
            return "java.lang.Boolean.valueOf(" + value.toString() + ")";
        }
        if (isType(resultType, TypeList.STRING, "java.lang.String")) {
            return "\"" + escapeStringLiteral(value.toString()) + "\"";
        }
        return value.toString();
    }

    private static boolean isType(Type actualType, Type primitiveType, String boxedTypeName) {
        return actualType.equals(primitiveType) || boxedTypeName.equals(actualType.getName());
    }

    private static String formatFloatLiteral(Object value) {
        float f = ((Number) value).floatValue();
        if (Float.isFinite(f)) {
            // Use Java parser-friendly decimal float literals for finite values.
            return Float.toString(f) + "F";
        }
        // Preserve NaN payload/sign and Infinity sign exactly (no canonicalization).
        int rawBits = Float.floatToRawIntBits(f);
        return String.format((Locale) null, "java.lang.Float.intBitsToFloat(0x%08X)", rawBits);
    }

    private static String formatDoubleLiteral(Object value) {
        double d = ((Number) value).doubleValue();
        if (Double.isNaN(d)) {
            // Preserve NaN payload/sign bit exactly.
            return "java.lang.Double.longBitsToDouble(0x"
                    + Long.toHexString(Double.doubleToRawLongBits(d)).toUpperCase()
                    + "L)";
        }
        if (d == Double.POSITIVE_INFINITY) {
            return "java.lang.Double.POSITIVE_INFINITY";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "java.lang.Double.NEGATIVE_INFINITY";
        }
        if (Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0.0d)) {
            return "-0.0";
        }
        return Double.toString(d);
    }

    private static String escapeCharLiteral(char c) {
        return switch (c) {
            case '\\' -> "\\\\";
            case '\'' -> "\\'";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> isPrintableAscii(c) ? String.valueOf(c) : unicodeEscape(c);
        };
    }

    private static String escapeStringLiteral(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (isPrintableAscii(c)) {
                        sb.append(c);
                    } else {
                        sb.append(unicodeEscape(c));
                    }
                }
            }
        }
        return sb.toString();
    }

    private static boolean isPrintableAscii(char c) {
        return c >= 0x20 && c <= 0x7E;
    }

    private static String unicodeEscape(char c) {
        return String.format((Locale) null, "\\u%04X", (int) c);
    }

    @Override
    public String visit(LocalVariable node) {
        return node.getVariableInfo().name;
    }

    @Override
    public String visit(LoopingCondition node) {
        return node.getCondition().accept(this);
    }

    @Override
    public String visit(MainKlass node) {
        String name = node.getName();
        IRNode dataMembers = node.getChild(MainKlass.MainKlassPart.DATA_MEMBERS.ordinal());
        IRNode memberFunctions = node.getChild(MainKlass.MainKlassPart.MEMBER_FUNCTIONS.ordinal());
        IRNode testFunction = node.getChild(MainKlass.MainKlassPart.TEST_FUNCTION.ordinal());
        IRNode printVariables = node.getChild(MainKlass.MainKlassPart.PRINT_VARIABLES.ordinal());

        return (ProductionParams.enableStrictFP.value() ? "strictfp " : "")
                + "public class " + name + " {\n"
                + dataMembers.accept(this)+ "\n"
                + (memberFunctions != null ? memberFunctions.accept(this): "") + "\n"
                + "    private void test()\n"
                + "    {\n"
                + testFunction.accept(this)
                + "    }" + addComplexityInfo(testFunction) + "\n"
                + printVariables.accept(this)
                + "}\n\n";
    }

    @Override
    public String visit(NonStaticMemberVariable node) {
        IRNode object = node.getChild(0);
        String objectString = object.accept(this);
        VariableInfo value = node.getVariableInfo();
        if (objectString.equals("this")) {
            return value.name;
        } else {
            if (object instanceof VariableBase || object instanceof Function || object instanceof Literal) {
                return objectString + "." + value.name;
            } else {
                return "(" + objectString + ")" + "." + value.name;
            }
        }
    }

    @Override
    public String visit(Nothing node) {
        return "";
    }

    @Override
    public String visit(PrintVariables node) {
        return FixedTrees.printVariablesAsFunction(node).accept(this)
                + "\n"
                + FixedTrees.printFinalVariablesAsFunction(node).accept(this);
    }

    @Override
    public String visit(Return node) {
        return "return " + node.getExpression().accept(this) + ";";
    }

    @Override
    public String visit(Throw node) {
        return "throw " + node.getThowable().accept(this) + ";";
    }

    @Override
    public String visit(Statement node) {
        return node.getChild(0).accept(this)+ (node.isSemicolonNeeded() ? ";" : "");
    }

    @Override
    public String visit(StaticConstructorDefinition node) {
        IRNode body = node.getChild(0);
        return "static {\n"
                + (body != null ? body.accept(this): "")
                + PrintingUtils.align(node.getLevel()) + "}";
    }

    @Override
    public String visit(StaticMemberVariable node) {
        IRNode owner = node.getOwner();
        VariableInfo info = node.getVariableInfo();
        if (owner.equals(info.owner)) {
            return info.name;
        } else {
            return info.owner.getName() + "." + info.name;
        }
    }

    @Override
    public String visit(Switch node) {
        int level = node.getLevel();
        int caseBlockIdx = node.getCaseBlockIndex();
        String cases = "";
        for (int i = 0; i < caseBlockIdx - 1; ++i) {
            cases += PrintingUtils.align(level + 1);
            if (node.getChild(i + 1) instanceof Nothing) {
                cases += "default:\n";
            } else {
                cases += "case " + node.getChild(i + 1).accept(this)+ ":\n";
            }

            cases += node.getChild(i + caseBlockIdx).accept(this)+ "\n";
        }
        return "switch (" + node.getChild(0).accept(this)+ ")\n"
               + openBraceWithGene(level, node.getChild(caseBlockIdx))
               + cases
               + closeBraceWithGene(level, node.getChild(caseBlockIdx));
    }

    @Override
    public String visit(TernaryOperator node) {
        IRNode conditionalExp = node.getChild(TernaryOperator.TernaryPart.CONDITION.ordinal());
        IRNode leftExp = node.getChild(TernaryOperator.TernaryPart.TRUE.ordinal());
        IRNode rightExp = node.getChild(TernaryOperator.TernaryPart.FALSE.ordinal());
        if (Objects.isNull(conditionalExp) || Objects.isNull(leftExp) || Objects.isNull(rightExp)) {
            return "null";
        }
        return expressionToJavaCode(node, conditionalExp, Operator.Order.RIGHT) + " ? "
                + expressionToJavaCode(node, leftExp, Operator.Order.RIGHT) + " : "
                + expressionToJavaCode(node, rightExp, Operator.Order.RIGHT);
    }

    @Override
    public String visit(Type node) {
        return node.getName();
    }

    @Override
    public String visit(TypeArray node) {
        String r = node.getType().accept(this);
        for (int i = 0; i < node.getDimensions(); i++) {
            r += "[]";
        }
        return r;
    }

    @Override
    public String visit(UnaryOperator node) {
        IRNode exp = node.getChild(0);
        if (node.isPrefix()) {
            String op = operatorToJaveCode(node.getOperationKind());
            // Keep unary +/- separated from signed numeric literals to avoid lexical ambiguity:
            // without a gap, "- -10" can be emitted as "--10", which javac parses as pre-decrement.
            boolean needsUnaryGap = node.getOperationKind() == OperatorKind.UNARY_MINUS
                    || node.getOperationKind() == OperatorKind.UNARY_PLUS;
            return op
                    + (needsUnaryGap || exp instanceof Operator ? " " : "")
                    + expressionToJavaCode(node, exp, Operator.Order.LEFT);
        } else {
            return expressionToJavaCode(node, exp, Operator.Order.RIGHT)
                    + (exp instanceof Operator ? " " : "")
                    + operatorToJaveCode(node.getOperationKind());
        }
    }

    @Override
    public String visit(VariableDeclaration node) {
        VariableInfo vi = node.getVariableInfo();
        return attributes(vi) + vi.type.accept(this)+ " " + vi.name;
    }

    @Override
    public String visit(VariableDeclarationBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(While node) {
        IRNode header = node.getChild(While.WhilePart.HEADER.ordinal());
        IRNode body1 = node.getChild(While.WhilePart.BODY1.ordinal());
        IRNode body2 = node.getChild(While.WhilePart.BODY2.ordinal());
        IRNode body3 = node.getChild(While.WhilePart.BODY3.ordinal());
        int level = node.getLevel();
        Loop loop = node.getLoop();
        return loop.initialization.accept(this)+ "\n"
                + header.accept(this)
                + PrintingUtils.align(level) + "while (" + loop.condition.accept(this)+ ")\n"
                + openBraceWithGene(level, body1)
                + body1.accept(this)
                + PrintingUtils.align(level + 1) + loop.manipulator.accept(this)+ ";\n"
                + body2.accept(this)
                + body3.accept(this)
                + closeBraceWithGene(level, body1);
    }

    @Override
    public String visit(CatchBlock node) {
        StringBuilder result = new StringBuilder();
        int level = node.getLevel();
        result.append(PrintingUtils.align(level)).append("catch(");
        result.append(node.throwables.get(0).accept(this));
        for (int i = 1; i < node.throwables.size(); i++) {
            result.append(" | ").append(node.throwables.get(i).accept(this));
        }
        result.append(" ex) ").append(openBraceSuffixWithGene(node.getChild(0)));
        result.append(node.getChild(0).accept(this));
        result.append(PrintingUtils.align(level)).append(closeBraceSuffixWithGene(node.getChild(0))).append("\n");
        return result.toString();
    }

    @Override
    public String visit(TryCatchBlock node) {
        StringBuilder result = new StringBuilder();
        List<? extends IRNode> childs = node.getChildren();
        IRNode body = childs.get(0);
        IRNode finallyBody = childs.get(1);
        int level = node.getLevel();
        result.append("try ").append(openBraceSuffixWithGene(body))
                .append(body.accept(this)).append("\n")
                .append(PrintingUtils.align(level))
                .append(closeBraceSuffixWithGene(body)).append("\n");
        for (int i = 2; i < childs.size(); i++) {
            result.append(childs.get(i).accept(this));
        }
        if (finallyBody != null) {
            String finallyContent = finallyBody.accept(this);
                if (!finallyContent.isEmpty()) {
                result.append(PrintingUtils.align(level)).append("finally ").append(openBraceSuffixWithGene(finallyBody))
                        .append(finallyContent).append("\n")
                        .append(PrintingUtils.align(level)).append(closeBraceSuffixWithGene(finallyBody)).append("\n");
            }
        }
        return result.toString();
    }
}
