package com.skennedy.lazuli.parsing;

import com.skennedy.lazuli.parsing.model.ExpressionType;
import com.skennedy.lazuli.parsing.model.IdentifierExpression;
import com.skennedy.lazuli.parsing.model.SyntaxNode;

import java.util.Arrays;
import java.util.Iterator;

public class IncrementExpression extends Expression {

    private final IdentifierExpression identifier;
    private final IdentifierExpression operator;

    public IncrementExpression(IdentifierExpression identifier, IdentifierExpression operator) {
        this.identifier = identifier;
        this.operator = operator;
    }

    public IdentifierExpression getIdentifier() {
        return identifier;
    }

    public IdentifierExpression getOperator() {
        return operator;
    }

    @Override
    public ExpressionType getExpressionType() {
        return ExpressionType.INCREMENT_EXPR;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Arrays.asList((SyntaxNode)identifier, operator).iterator();
    }
}
