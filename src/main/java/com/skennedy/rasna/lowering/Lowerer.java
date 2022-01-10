package com.skennedy.rasna.lowering;

import com.skennedy.rasna.parsing.model.OpType;
import com.skennedy.rasna.typebinding.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Lowerer extends BoundProgramRewriter {

    private static int labelCount = 0;

    @Override
    protected BoundExpression rewriteMapExpression(BoundMapExpression mapExpression) {

        BoundExpression expression = super.rewriteMapExpression(mapExpression);
        if (expression instanceof BoundNoOpExpression) {
            //Not sure if this can happen, empty body?
            return expression;
        }

        VariableSymbol iterator = new VariableSymbol("iterator-" + generateInternalVariableName(), TypeSymbol.INT, null, false, null);

        BoundBlockExpression body = new BoundBlockExpression(
                new BoundArrayAssignmentExpression(
                        new BoundPositionalAccessExpression(mapExpression.getOperand(), new BoundVariableExpression(iterator)),
                        mapExpression.getMapperFunction()
                )
        );

        BoundForExpression boundForExpression = new BoundForExpression(
                iterator,
                new BoundRangeExpression(new BoundLiteralExpression(0), new BoundArrayLengthExpression(mapExpression.getOperand()), new BoundLiteralExpression(1)),
                null,
                body
        );
        return rewriteForExpression(boundForExpression);
    }

    @Override
    protected BoundExpression rewriteWhileExpression(BoundWhileExpression boundWhileExpression) {

        BoundExpression expression = super.rewriteWhileExpression(boundWhileExpression);

        if (expression instanceof BoundNoOpExpression) {
            return expression;
        }

        BoundLabel checkLabel = generateLabel();
        BoundLabel endLabel = generateLabel();

        BoundGotoExpression gotoCheck = new BoundGotoExpression(checkLabel);
        BoundLabelExpression checkLabelExpression = new BoundLabelExpression(checkLabel);
        BoundLabelExpression endLabelExpression = new BoundLabelExpression(endLabel);
        BoundConditionalGotoExpression gotoFalse = new BoundConditionalGotoExpression(endLabel, boundWhileExpression.getCondition(), true);

        return rewriteBlockExpression(new BoundBlockExpression(
                checkLabelExpression,
                gotoFalse,
                boundWhileExpression.getBody(),
                gotoCheck,
                endLabelExpression
        ));
    }

    @Override
    protected BoundExpression rewriteForExpression(BoundForExpression boundForExpression) {

        BoundExpression expression = super.rewriteForExpression(boundForExpression);

        if (expression instanceof BoundNoOpExpression) {
            return expression;
        }
        if (!(expression instanceof BoundForExpression)) {
            return expression;
        }

        BoundForExpression rewrittenForExpression = (BoundForExpression) expression;

        if (rewrittenForExpression.getBody() instanceof BoundNoOpExpression) {
            return new BoundNoOpExpression();
        }

        BoundVariableDeclarationExpression variableDeclarationExpression = new BoundVariableDeclarationExpression(rewrittenForExpression.getIterator(), null, rewrittenForExpression.getRangeExpression().getLowerBound(), false);

        BoundVariableExpression variableExpression = new BoundVariableExpression(rewrittenForExpression.getIterator());

        BoundExpression step;
        //TODO: Make a BoundIncrementExpression that converts to the instruction iinc
        if (rewrittenForExpression.getRangeExpression().getStep() == null) {
            step = new BoundIncrementExpression(rewrittenForExpression.getIterator(), new BoundLiteralExpression(1));
        } else {
            step = new BoundAssignmentExpression(rewrittenForExpression.getIterator(),
                    null,
                    new BoundBinaryExpression(
                            variableExpression,
                            BoundBinaryOperator.bind(OpType.ADD, TypeSymbol.INT, TypeSymbol.INT),
                            rewrittenForExpression.getRangeExpression().getStep()
                    )
            );
        }

        BoundBlockExpression whileBody;
        if (rewrittenForExpression.getGuard() == null) {
            whileBody = new BoundBlockExpression(rewrittenForExpression.getBody(), step);
        } else {
            BoundExpression guardClause = new BoundIfExpression(rewrittenForExpression.getGuard(), rewrittenForExpression.getBody(), null);
            whileBody = new BoundBlockExpression(guardClause, step);
        }

        BoundBinaryExpression condition = new BoundBinaryExpression(variableExpression, BoundBinaryOperator.bind(OpType.LT, TypeSymbol.INT, TypeSymbol.INT), boundForExpression.getRangeExpression().getUpperBound());

        BoundWhileExpression whileExpression = new BoundWhileExpression(condition, whileBody);
        BoundBlockExpression boundBlockExpression = new BoundBlockExpression(variableDeclarationExpression, whileExpression);

        return rewriteBlockExpression(boundBlockExpression);
    }

    @Override
    protected BoundExpression rewriteForInExpression(BoundForInExpression forInExpression) {
        BoundExpression expression = super.rewriteForInExpression(forInExpression);

        if (expression instanceof BoundNoOpExpression) {
            return expression;
        }
        if (!(expression instanceof BoundForInExpression)) {
            return expression;
        }
        BoundForInExpression rewrittenForInExpression = (BoundForInExpression) expression;

        if (rewrittenForInExpression.getBody() instanceof BoundNoOpExpression) {
            return new BoundNoOpExpression();
        }
        VariableSymbol arrayLength = new VariableSymbol("array-length-" + generateInternalVariableName(), TypeSymbol.INT, null, false, null);
        BoundVariableExpression arrayLengthExpression = new BoundVariableExpression(arrayLength);
        VariableSymbol iterationCounter = new VariableSymbol("iteration-counter-" + generateInternalVariableName(), TypeSymbol.INT, null, false, null);
        BoundVariableExpression iterationCounterExpression = new BoundVariableExpression(iterationCounter);

        BoundBlockExpression preLoop = new BoundBlockExpression(
                new BoundVariableDeclarationExpression( //Store array length
                        arrayLength,
                        null,
                        new BoundArrayLengthExpression(forInExpression.getIterable()),
                        false
                ),
                new BoundVariableDeclarationExpression(//Initialise internal counter
                        iterationCounter,
                        null,
                        new BoundLiteralExpression(0),
                        false
                ),
                new BoundVariableDeclarationExpression( //Initialise iterator
                        ((BoundForInExpression) expression).getVariable(),
                        ((BoundForInExpression) expression).getGuard(),
                        new BoundPositionalAccessExpression(((BoundForInExpression) expression).getIterable(), new BoundLiteralExpression(0)),
                        false
                )
        );

        BoundBlockExpression loopAssign = new BoundBlockExpression(
                new BoundAssignmentExpression(
                        ((BoundForInExpression) expression).getVariable(),
                        ((BoundForInExpression) expression).getGuard(),
                        new BoundPositionalAccessExpression(((BoundForInExpression) expression).getIterable(), iterationCounterExpression)
                )
        );

        BoundExpression step = new BoundIncrementExpression(iterationCounter, new BoundLiteralExpression(1));

        BoundBlockExpression whileBody;
        if (rewrittenForInExpression.getGuard() == null) {
            whileBody = new BoundBlockExpression(loopAssign, rewrittenForInExpression.getBody(), step);
        } else {
            BoundBlockExpression body = new BoundBlockExpression(rewrittenForInExpression.getBody());
            BoundExpression guardClause = new BoundIfExpression(rewrittenForInExpression.getGuard(), body, null);
            whileBody = new BoundBlockExpression(loopAssign, guardClause, step);
        }

        BoundBinaryExpression condition = new BoundBinaryExpression(iterationCounterExpression, BoundBinaryOperator.bind(OpType.LT, TypeSymbol.INT, TypeSymbol.INT), arrayLengthExpression);
        BoundWhileExpression whileExpression = new BoundWhileExpression(condition, whileBody);

        BoundBlockExpression boundBlockExpression = new BoundBlockExpression(preLoop, whileExpression);

        return flatten(rewriteBlockExpression(boundBlockExpression));
    }

    @Override
    protected BoundExpression rewriteIfExpression(BoundIfExpression boundIfExpression) {
        BoundExpression expression = super.rewriteIfExpression(boundIfExpression);

        if (expression instanceof BoundNoOpExpression) {
            return expression;
        }

        BoundIfExpression rewrittenBoundIfExpression = (BoundIfExpression) expression;

        BoundLabel endLabel = blockEndLabel == null ? generateLabel() : blockEndLabel;
        BoundLabel tmp = blockEndLabel;
        blockEndLabel = endLabel;
        BoundLabelExpression endLabelExpression = new BoundLabelExpression(endLabel);


        if (rewrittenBoundIfExpression.getElseBody() == null) {
            BoundExpression condition = rewriteCondition(rewrittenBoundIfExpression.getCondition(), endLabel);

            BoundBlockExpression result = new BoundBlockExpression(
                    condition,
                    rewrittenBoundIfExpression.getBody(),
                    endLabelExpression
            );
            blockEndLabel = tmp;
            return flatten(rewriteBlockExpression(result));

        } else {

            BoundLabel elseLabel = generateLabel();

            BoundGotoExpression gotoEndStatement = new BoundGotoExpression(endLabel);
            BoundLabelExpression elseLabelStatement = new BoundLabelExpression(elseLabel);

            BoundExpression condition = rewriteCondition(rewrittenBoundIfExpression.getCondition(), elseLabel);

            BoundBlockExpression result = new BoundBlockExpression(
                    condition,
                    rewrittenBoundIfExpression.getBody(),
                    gotoEndStatement,
                    elseLabelStatement,
                    rewrittenBoundIfExpression.getElseBody(),
                    endLabelExpression
            );
            blockEndLabel = tmp;
            return flatten(rewriteBlockExpression(result));
        }
    }

    private BoundExpression rewriteCondition(BoundExpression condition, BoundLabel endLabel) {

        if (condition instanceof BoundBinaryExpression) {

            BoundBinaryExpression binaryExpression = (BoundBinaryExpression) condition;
            switch (binaryExpression.getOperator().getBoundOpType()) {

                case BOOLEAN_OR: {
                    BoundExpression left = rewriteCondition(binaryExpression.getLeft(), endLabel);
                    BoundExpression right = rewriteCondition(binaryExpression.getRight(), endLabel);

                    BoundLabel bodyStart = generateLabel();
                    BoundLabelExpression bodyStartExpression = new BoundLabelExpression(bodyStart);

                    BoundConditionalGotoExpression gotoBody = new BoundConditionalGotoExpression(bodyStart, left, false);
                    BoundConditionalGotoExpression gotoEnd = new BoundConditionalGotoExpression(endLabel, right, true);

                    return new BoundBlockExpression(gotoBody, gotoEnd, bodyStartExpression);
                }
                case BOOLEAN_AND: {
                    BoundExpression left = binaryExpression.getLeft();
                    BoundExpression right = binaryExpression.getRight();

                    BoundConditionalGotoExpression gotoEndFirst = new BoundConditionalGotoExpression(endLabel, left, true);
                    BoundConditionalGotoExpression gotoEndSecond = new BoundConditionalGotoExpression(endLabel, right, true);

                    return new BoundBlockExpression(gotoEndFirst, gotoEndSecond);
                }
                default:
                    return new BoundConditionalGotoExpression(endLabel, condition, true);
            }
        }
        return new BoundConditionalGotoExpression(endLabel, condition, true);
    }

    @Override
    protected BoundExpression rewriteMatchExpression(BoundMatchExpression matchExpression) {

        BoundExpression expression = super.rewriteMatchExpression(matchExpression);

        if (expression instanceof BoundNoOpExpression) {
            return expression;
        }
        BoundMatchExpression rewrittenMatchExpression = (BoundMatchExpression) expression;


        BoundLabel endLabel = generateLabel();
        BoundLabelExpression endLabelExpression = new BoundLabelExpression(endLabel);
        BoundGotoExpression gotoEnd = new BoundGotoExpression(endLabel);

        List<BoundExpression> expressions = new ArrayList<>();
        for (BoundMatchCaseExpression matchCaseExpression : rewrittenMatchExpression.getMatchCaseExpressions()) {

            if (matchCaseExpression.getCaseExpression() == null) { //base case
                expressions.add(matchCaseExpression.getThenExpression());
                continue;
            }

            BoundLabel elseLabel = generateLabel();
            BoundLabelExpression elseLabelStatement = new BoundLabelExpression(elseLabel);

            BoundExpression condition;
            if (matchCaseExpression.getCaseExpression().getType() == TypeSymbol.BOOL) {
                condition = matchCaseExpression.getCaseExpression();
            } else {
                condition = new BoundBinaryExpression(
                        matchExpression.getOperand(),
                        BoundBinaryOperator.bind(OpType.EQ, TypeSymbol.INT, TypeSymbol.INT),
                        matchCaseExpression.getCaseExpression());
            }

            BoundConditionalGotoExpression gotoFalse = new BoundConditionalGotoExpression(elseLabel, condition, true);

            BoundBlockExpression caseExpression = new BoundBlockExpression(
                    gotoFalse,
                    matchCaseExpression.getThenExpression(),
                    gotoEnd,
                    elseLabelStatement
            );
            expressions.add(caseExpression);
        }

        expressions.add(endLabelExpression);
        BoundBlockExpression result = new BoundBlockExpression(expressions);

        return flatten(rewriteBlockExpression(result));
    }

    private static BoundLabel generateLabel() {
        String name = "label_" + labelCount++;
        return new BoundLabel(name);
    }

    private static String generateInternalVariableName() {
        return UUID.randomUUID().toString();
    }

}
