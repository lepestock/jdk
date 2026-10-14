/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester.factories;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.classes.RecordKlass;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.morph.MorphTemplate;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;
import jdk.test.lib.jittester.utils.PseudoRandom;

class RecordKlassFactory extends Factory<RecordKlass> {
    private static final double COMPONENT_ARRAY_TYPE_BASE_PROBABILITY = 0.10;
    private final String name;
    private final int memberFunctionsLimit;
    private final int memberFunctionsArgLimit;
    private final int statementsInFunctionLimit;
    private final int operatorLimit;
    private final int level;

    RecordKlassFactory(String name, int memberFunctionsLimit, int memberFunctionsArgLimit,
            int statementsInFunctionLimit, int operatorLimit, int level) {
        this.name = name;
        this.memberFunctionsLimit = memberFunctionsLimit;
        this.memberFunctionsArgLimit = memberFunctionsArgLimit;
        this.statementsInFunctionLimit = statementsInFunctionLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
    }

    @Override
    public RecordKlass produce() throws ProductionFailedException {
        GenerationState.Checkpoint classStateCheckpoint = GenerationState.checkpoint();
        TypeKlass thisKlass = new TypeKlass(name);
        thisKlass.setRecord();
        thisKlass.setFinal();
        thisKlass.addParent(TypeList.OBJECT.getName());
        thisKlass.setParent(TypeList.OBJECT);
        TypeList.OBJECT.addChild(name);
        ArrayList<TypeKlass> interfaces = new ArrayList<>();
        try {
            ArrayList<VariableInfo> components = produceComponents(thisKlass);
            VariableInfo thizzVariable = new VariableInfo("this", thisKlass, thisKlass,
                    VariableInfo.FINAL | VariableInfo.LOCAL | VariableInfo.INITIALIZED);
            SymbolTable.add(thizzVariable);

            IRNode abstractFunctionRedefinitions = null;
            IRNode functionDefinitions = null;
            IRNode functionDeclarations = null;
            IRNodeBuilder builder = new IRNodeBuilder().setOwnerKlass(thisKlass)
                    .setExceptionSafe(true);
            List<MorphTemplate> classTemplates = List.of();
            try {
                registerRecordFunctions(thisKlass, components);
                builder.setLevel(level + 1)
                        .withOperatorLimit(operatorLimit)
                        .withStatementLimit(statementsInFunctionLimit)
                        .setMemberFunctionsArgLimit(memberFunctionsArgLimit);
                classTemplates = builder.createClassMorphTemplates();
                for (MorphTemplate classTemplate : classTemplates) {
                    GenerationState.setCurrentMorphContext(
                            GenerationState.currentMorphContext().withAdded(classTemplate));
                }
                LinkedHashSet<Symbol> abstractSet = new LinkedHashSet<>();
                if (!ProductionParams.disableInheritance.value()) {
                    inheritInterfaces(thisKlass, interfaces);
                    abstractSet.addAll(abstractFunctions(thisKlass));
                    for (Symbol symbol : abstractSet) {
                        SymbolTable.remove(symbol);
                    }
                    abstractFunctionRedefinitions = builder
                            .getFunctionRedefinitionBlockFactory(abstractSet)
                            .produce();
                    if (thisKlass.isAbstract()) {
                        throw new ProductionFailedException();
                    }
                }
                if (!ProductionParams.disableFunctions.value()
                        && memberFunctionsLimit > 0 && PseudoRandom.randomBoolean()) {
                    functionDefinitions = builder.setMemberFunctionsLimit(
                                    (int) (memberFunctionsLimit * 0.6 * PseudoRandom.random()))
                            .setFlags(FunctionInfo.NONE)
                            .getFunctionDefinitionBlockFactory()
                            .produce();
                }
            } finally {
                for (MorphTemplate classTemplate : classTemplates) {
                    GenerationState.setCurrentMorphContext(
                            GenerationState.currentMorphContext().withoutId(classTemplate.id()));
                }
                SymbolTable.remove(new Symbol("this", thisKlass, thisKlass, VariableInfo.NONE));
            }
            TypeList.add(thisKlass);
            return new RecordKlass(thisKlass, interfaces, name, level,
                    components, abstractFunctionRedefinitions, functionDefinitions, functionDeclarations, null);
        } catch (ProductionFailedException e) {
            GenerationState.rollbackTo(classStateCheckpoint);
            throw e;
        } catch (RuntimeException e) {
            GenerationState.rollbackTo(classStateCheckpoint);
            throw e;
        }
    }

    private ArrayList<VariableInfo> produceComponents(TypeKlass ownerClass) throws ProductionFailedException {
        List<Type> types = componentTypes();
        int maxComponents = Math.max(0, memberFunctionsArgLimit);
        int limit = PseudoRandom.randomNotNegative(maxComponents + 1);
        if (limit > 0 && types.isEmpty()) {
            throw new ProductionFailedException();
        }
        ArrayList<VariableInfo> components = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Type type = selectComponentType(types);
            components.add(VariableInfo.symbolArgument("component_" + i, ownerClass, type,
                    VariableInfo.FINAL | VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        }
        return components;
    }

    private static Type selectComponentType(List<Type> types) {
        Type type = PseudoRandom.randomElement(types);
        if (TypeList.isBuiltIn(type) && !type.equals(TypeList.VOID)
                && PseudoRandom.randomBoolean(0.30)) {
            Type boxed = TypeBoxingUtil.toWrapperType(type);
            if (boxed != null && isUsableComponentType(boxed)) {
                type = boxed;
            }
        }
        return maybeWrapComponentTypeAsArray(type);
    }

    private static Type maybeWrapComponentTypeAsArray(Type type) {
        if (ProductionParams.disableArrays.value()
                || type instanceof TypeArray
                || type.equals(TypeList.VOID)
                || !TypeArray.isElementTypeAllowed(type)) {
            return type;
        }
        double bonusScale = 1.0
                + Math.max(0, ProductionParams.arrayFieldDefinitionWeightBonus.value()) / 100.0;
        double probability = Math.max(0.0, Math.min(1.0,
                COMPONENT_ARRAY_TYPE_BASE_PROBABILITY * bonusScale));
        if (!PseudoRandom.randomBoolean(probability)) {
            return type;
        }
        return CollectionCreationFactory.withSelectedStorageKind(new TypeArray(type, 1));
    }

    private static List<Type> componentTypes() {
        ArrayList<Type> result = new ArrayList<>();
        for (Type type : TypeList.getAll()) {
            if (isUsableComponentType(type)) {
                result.add(type);
            }
        }
        return result;
    }

    private static boolean isUsableComponentType(Type type) {
        if (type.equals(TypeList.VOID)) {
            return false;
        }
        if (TypeList.isBuiltIn(type) || type.equals(TypeList.STRING)
                || TypeBoxingUtil.isWrapperType(type)) {
            return true;
        }
        if (!(type instanceof TypeKlass typeKlass)
                || typeKlass.isAbstract()
                || typeKlass.isInterface()) {
            return false;
        }
        for (Symbol symbol : SymbolTable.get(typeKlass, FunctionInfo.class)) {
            FunctionInfo functionInfo = (FunctionInfo) symbol;
            if (functionInfo.isConstructor()
                    && !functionInfo.owner.isAbstract()
                    && !functionInfo.owner.isInterface()) {
                return true;
            }
        }
        return false;
    }

    private static void inheritInterfaces(TypeKlass thisKlass, ArrayList<TypeKlass> interfaces) {
        LinkedList<Type> probableInterfaces = new LinkedList<>(TypeList.getAll());
        for (Iterator<Type> i = probableInterfaces.iterator(); i.hasNext();) {
            Type type = i.next();
            if (!(type instanceof TypeKlass typeKlass) || !typeKlass.isInterface()) {
                i.remove();
            }
        }
        PseudoRandom.shuffle(probableInterfaces);
        int implLimit = (int) (ProductionParams.implementationLimit.value() * PseudoRandom.random());
        compatibility_check:
        for (Iterator<Type> i = probableInterfaces.iterator(); i.hasNext() && implLimit > 0; implLimit--) {
            TypeKlass iface = (TypeKlass) i.next();
            ArrayList<Symbol> ifaceFuncSet = SymbolTable.getAllCombined(iface, FunctionInfo.class);
            for (Symbol symbol : SymbolTable.getAllCombined(thisKlass, FunctionInfo.class)) {
                if (FunctionDefinition.isInvalidOverride((FunctionInfo) symbol, ifaceFuncSet)) {
                    continue compatibility_check;
                }
            }
            interfaces.add(iface);
            iface.addChild(thisKlass.getName());
            thisKlass.addParent(iface.getName());
            for (Symbol symbol : SymbolTable.getAllCombined(iface, FunctionInfo.class)) {
                FunctionInfo functionInfo = (FunctionInfo) symbol.deepCopy();
                functionInfo.owner = thisKlass;
                if (!functionInfo.isStatic()) {
                    functionInfo.argTypes.get(0).type = thisKlass;
                }
                SymbolTable.add(functionInfo);
            }
        }
    }

    private static LinkedHashSet<Symbol> abstractFunctions(TypeKlass thisKlass) {
        LinkedHashSet<Symbol> abstractSet = new LinkedHashSet<>();
        LinkedHashSet<Symbol> nonAbstractSet = new LinkedHashSet<>();
        ArrayList<Symbol> combined = SymbolTable.getAllCombined(thisKlass, FunctionInfo.class);
        combined.sort(RecordKlassFactory::compareSymbolsDeterministically);
        for (Symbol symbol : combined) {
            FunctionInfo functionInfo = (FunctionInfo) symbol;
            if ((functionInfo.flags & FunctionInfo.ABSTRACT) > 0) {
                abstractSet.add(functionInfo);
            } else {
                nonAbstractSet.add(functionInfo);
            }
        }
        abstractSet.removeAll(nonAbstractSet);
        return abstractSet;
    }

    private static int compareSymbolsDeterministically(Symbol a, Symbol b) {
        int byName = String.valueOf(a.name).compareTo(String.valueOf(b.name));
        if (byName != 0) {
            return byName;
        }
        int byOwner = String.valueOf(a.owner == null ? "" : a.owner.getName())
                .compareTo(String.valueOf(b.owner == null ? "" : b.owner.getName()));
        if (byOwner != 0) {
            return byOwner;
        }
        int byType = String.valueOf(a.type == null ? "" : a.type.getName())
                .compareTo(String.valueOf(b.type == null ? "" : b.type.getName()));
        if (byType != 0) {
            return byType;
        }
        return Integer.compare(a.flags, b.flags);
    }

    private static void registerRecordFunctions(TypeKlass ownerClass, ArrayList<VariableInfo> components) {
        SymbolTable.add(new FunctionInfo(ownerClass.getName(), ownerClass, ownerClass,
                0, FunctionInfo.PUBLIC, new ArrayList<>(components)));
        VariableInfo thizz = new VariableInfo("this", ownerClass, ownerClass,
                VariableInfo.FINAL | VariableInfo.LOCAL | VariableInfo.INITIALIZED);
        for (VariableInfo component : components) {
            registerRecordComponentField(ownerClass, component);
            ArrayList<VariableInfo> args = new ArrayList<>();
            args.add(thizz);
            SymbolTable.add(new FunctionInfo(component.name, ownerClass, component.type,
                    0, FunctionInfo.PUBLIC | FunctionInfo.FINAL | FunctionInfo.NONRECURSIVE, args));
        }
        ArrayList<VariableInfo> equalsArgs = new ArrayList<>();
        equalsArgs.add(thizz);
        equalsArgs.add(VariableInfo.symbolArgument("obj", ownerClass, TypeList.OBJECT,
                VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        SymbolTable.add(new FunctionInfo("equals", ownerClass, TypeList.BOOLEAN,
                0, FunctionInfo.PUBLIC | FunctionInfo.FINAL | FunctionInfo.NONRECURSIVE, equalsArgs));
        if (hasStableToString(components)) {
            ArrayList<VariableInfo> toStringArgs = new ArrayList<>();
            toStringArgs.add(thizz);
            SymbolTable.add(new FunctionInfo("toString", ownerClass, TypeList.STRING,
                    0, FunctionInfo.PUBLIC | FunctionInfo.FINAL | FunctionInfo.NONRECURSIVE, toStringArgs));
        }
    }

    private static void registerRecordComponentField(TypeKlass ownerClass, VariableInfo component) {
        VariableInfo field = new VariableInfo(component.name, ownerClass, component.type,
                VariableInfo.PUBLIC | VariableInfo.FINAL | VariableInfo.INITIALIZED
                        | VariableInfo.RECORD_COMPONENT);
        SymbolTable.add(field);
        ownerClass.addSymbol(field);
    }

    private static boolean hasStableToString(ArrayList<VariableInfo> components) {
        for (VariableInfo component : components) {
            Type type = component.type;
            if (!TypeList.isBuiltIn(type) && !type.equals(TypeList.STRING)
                    && !TypeBoxingUtil.isWrapperType(type)) {
                return false;
            }
        }
        return true;
    }
}
