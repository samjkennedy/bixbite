package com.skennedy.rasna.typebinding;

import java.util.Collections;
import java.util.LinkedHashMap;

import static com.skennedy.rasna.typebinding.SymbolType.VARIABLE;

public class ArrayTypeSymbol extends TypeSymbol {

    private final TypeSymbol type;

    public ArrayTypeSymbol(TypeSymbol type) {
        super(type.getName(),
                Collections.emptyMap(),    //TODO: split, reduce, etc etc
                new LinkedHashMap<>()
        );
        this.type = type;
    }

    //private static final VariableSymbol arrayLen = new VariableSymbol("size", TypeSymbol.INT, null, false, declaration);

//    private static final TreeMap<String, VariableSymbol> intrinsicMemberVariables = TreeMap.of(
//            "size", arrayLen
//    );

    public TypeSymbol getType() {
        return type;
    }

    @Override
    public SymbolType getSymbolType() {
        return VARIABLE;
    }

    @Override
    public String toString() {
        return getName() + "[]";
    }
}