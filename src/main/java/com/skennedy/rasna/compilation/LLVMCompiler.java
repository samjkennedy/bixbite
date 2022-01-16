package com.skennedy.rasna.compilation;

import com.skennedy.rasna.lowering.BoundArrayLengthExpression;
import com.skennedy.rasna.lowering.BoundDoWhileExpression;
import com.skennedy.rasna.typebinding.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.skennedy.rasna.typebinding.TypeSymbol.INT;
import static com.skennedy.rasna.typebinding.TypeSymbol.STRING;
import static org.bytedeco.llvm.global.LLVM.LLVMAddFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMAddIncoming;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMArrayType;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAdd;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAlloca;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAnd;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCall;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildGlobalStringPtr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildICmp;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildMul;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildOr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildPhi;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRet;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildSDiv;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildSRem;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStore;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildSub;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildXor;
import static org.bytedeco.llvm.global.LLVM.LLVMCCallConv;
import static org.bytedeco.llvm.global.LLVM.LLVMConstArray;
import static org.bytedeco.llvm.global.LLVM.LLVMConstInt;
import static org.bytedeco.llvm.global.LLVM.LLVMContextCreate;
import static org.bytedeco.llvm.global.LLVM.LLVMContextDispose;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeModule;
import static org.bytedeco.llvm.global.LLVM.LLVMDumpModule;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMGetArrayLength;
import static org.bytedeco.llvm.global.LLVM.LLVMGetElementAsConstant;
import static org.bytedeco.llvm.global.LLVM.LLVMGetElementPtr;
import static org.bytedeco.llvm.global.LLVM.LLVMGetGlobalPassRegistry;
import static org.bytedeco.llvm.global.LLVM.LLVMGetInsertBlock;
import static org.bytedeco.llvm.global.LLVM.LLVMGetNumOperands;
import static org.bytedeco.llvm.global.LLVM.LLVMInitializeCore;
import static org.bytedeco.llvm.global.LLVM.LLVMInitializeNativeAsmParser;
import static org.bytedeco.llvm.global.LLVM.LLVMInitializeNativeAsmPrinter;
import static org.bytedeco.llvm.global.LLVM.LLVMInitializeNativeTarget;
import static org.bytedeco.llvm.global.LLVM.LLVMInt1TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt32TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt8TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMIntEQ;
import static org.bytedeco.llvm.global.LLVM.LLVMIntNE;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSGE;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSGT;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSLE;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSLT;
import static org.bytedeco.llvm.global.LLVM.LLVMLinkInMCJIT;
import static org.bytedeco.llvm.global.LLVM.LLVMModuleCreateWithNameInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPointerType;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;
import static org.bytedeco.llvm.global.LLVM.LLVMPrintMessageAction;
import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToFile;
import static org.bytedeco.llvm.global.LLVM.LLVMSetFunctionCallConv;
import static org.bytedeco.llvm.global.LLVM.LLVMVerifyFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMVerifyModule;
import static org.bytedeco.llvm.global.LLVM.LLVMVoidType;

public class LLVMCompiler implements Compiler {

    private static final Logger log = LogManager.getLogger(LLVMCompiler.class);

    public static final BytePointer error = new BytePointer();

    private LLVMTypeRef i32Type;
    private LLVMTypeRef i1Type;
    private LLVMValueRef printf;
    private LLVMValueRef formatStr; //"%d\n"

    private Map<VariableSymbol, LLVMValueRef> variables;
    private Map<VariableSymbol, LLVMTypeRef> types;

    @Override
    public void compile(BoundProgram program, String outputFileName) throws IOException {

        // Stage 1: Initialize LLVM components
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();

        variables = new HashMap<>();
        types = new HashMap<>();

        LLVMContextRef context = LLVMContextCreate();
        LLVMModuleRef module = LLVMModuleCreateWithNameInContext(outputFileName, context);
        LLVMBuilderRef builder = LLVMCreateBuilderInContext(context);

        i32Type = LLVMInt32TypeInContext(context);
        i1Type = LLVMInt1TypeInContext(context);

        //Declare printf function and string formatter once
        printf = LLVMAddFunction(module, "printf", LLVMFunctionType(i32Type, LLVMPointerType(LLVMInt8TypeInContext(context), 0), 1, 1));//No idea what AddressSpace is for yet

        for (BoundExpression expression : program.getExpressions()) {
            if (expression instanceof BoundFunctionDeclarationExpression) {

                BoundFunctionDeclarationExpression functionDeclarationExpression = (BoundFunctionDeclarationExpression) expression;

                if (functionDeclarationExpression.getFunctionSymbol().getName().equals("main")) {
                    //TODO: main args
                    LLVMTypeRef mainType = LLVMFunctionType(i32Type, LLVMVoidType(), /* argumentCount */ 0, /* isVariadic */ 0);

                    LLVMValueRef main = LLVMAddFunction(module, "main", mainType);
                    LLVMSetFunctionCallConv(main, LLVMCCallConv);

                    LLVMBasicBlockRef entry = LLVMAppendBasicBlockInContext(context, main, "entry");
                    LLVMPositionBuilderAtEnd(builder, entry);

                    visitMainMethod((BoundFunctionDeclarationExpression) expression, builder, context, main);

                    LLVMValueRef returnCode = LLVMConstInt(i32Type, 0, 0);
                    LLVMBuildRet(builder, returnCode);

                    if (LLVMVerifyFunction(main, LLVMPrintMessageAction) != 0) {
                        log.error("Error when validating main function:");
                        LLVMDumpModule(module);
                        System.exit(1);
                    }
                }
            }
        }

        if (LLVMVerifyModule(module, LLVMPrintMessageAction, error) != 0) {
            log.error("Failed to validate module: " + error.getString());
            LLVMDumpModule(module);
            System.exit(1);
        }

        BytePointer llFile = new BytePointer("./" + outputFileName + ".ll");
        if (LLVMPrintModuleToFile(module, llFile, error) != 0) {
            log.error("Failed to write module to file");
            LLVMDisposeMessage(error);
            System.exit(1);
        }
        log.info("Wrote IR to " + outputFileName + ".ll");
        Process process = Runtime.getRuntime().exec("C:\\Program Files\\LLVM\\bin\\clang " + outputFileName + ".ll -o " + outputFileName + ".exe");
        InputStream inputStream = process.getInputStream();
        StringBuilder stdout = new StringBuilder();
        char c = (char) inputStream.read();
        while (c != '\uFFFF') {
            stdout.append(c);
            c = (char) inputStream.read();
        }
        if (stdout.length() != 0) {
            log.info(stdout.toString());
        }

        StringBuilder stderr = new StringBuilder();
        InputStream errorStream = process.getErrorStream();
        c = (char) errorStream.read();
        while (c != '\uFFFF') {
            stderr.append(c);
            c = (char) errorStream.read();
        }
        if (stderr.length() != 0) {
            log.warn(stderr.toString()); //gcc likes to put warnings in stderr, if there was an error we probably wouldn't have gotten this far
        }
        log.info("Compiled IR to " + outputFileName + ".exe");

        // Stage 5: Dispose of allocated resources
        LLVMDisposeModule(module);
        LLVMDisposeBuilder(builder);
        LLVMContextDispose(context);
    }

    private LLVMValueRef visit(BoundExpression expression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {
        switch (expression.getBoundExpressionType()) {
            case NOOP:
                return LLVMConstInt(i32Type, 0, 0);
            case LITERAL:
                return visit((BoundLiteralExpression) expression, builder, context, function);
            case PRINT_INTRINSIC:
                return visit((BoundPrintExpression) expression, builder, context, function);
            case BINARY_EXPRESSION:
                return visit((BoundBinaryExpression) expression, builder, context, function);
            case VARIABLE_DECLARATION:
                return visit((BoundVariableDeclarationExpression) expression, builder, context, function);
            case VARIABLE_EXPRESSION:
                return visit((BoundVariableExpression) expression, builder, context, function);
            case IF:
                return visit((BoundIfExpression) expression, builder, context, function);
            case BLOCK:
                return visit((BoundBlockExpression) expression, builder, context, function);
            case INCREMENT:
                return visit((BoundIncrementExpression) expression, builder, context, function);
            case DO_WHILE:
                return visit((BoundDoWhileExpression) expression, builder, context, function);
            case ASSIGNMENT_EXPRESSION:
                return visit((BoundAssignmentExpression) expression, builder, context, function);
            case ARRAY_LITERAL_EXPRESSION:
                return visit((BoundArrayLiteralExpression) expression, builder, context, function);
            case ARRAY_LENGTH_EXPRESSION:
                return visit((BoundArrayLengthExpression) expression, builder, context, function);
            case POSITIONAL_ACCESS_EXPRESSION:
                return visit((BoundPositionalAccessExpression) expression, builder, context, function);
            default:
                throw new UnsupportedOperationException("Compilation for `" + expression.getBoundExpressionType() + "` is not yet implemented in LLVM");
        }
    }

    private LLVMValueRef visit(BoundPositionalAccessExpression positionalAccessExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        LLVMValueRef array = visit(positionalAccessExpression.getArray(), builder, context, function);

        if (!positionalAccessExpression.getIndex().isConstExpression()) {
            throw new IllegalStateException("Cannot get element of array with non const index");
        }
        return LLVMGetElementAsConstant(array, (int) positionalAccessExpression.getIndex().getConstValue());
    }

    private LLVMValueRef visit(BoundArrayLengthExpression arrayLengthExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        if (!(arrayLengthExpression.getIterable() instanceof BoundVariableExpression)) {
            LLVMValueRef visit = visit(arrayLengthExpression.getIterable(), builder, context, function);

            LLVMGetNumOperands(visit);
        }
        BoundVariableExpression variableExpression = (BoundVariableExpression) arrayLengthExpression.getIterable();
        if (!types.containsKey(variableExpression.getVariable())) {
            throw new IllegalStateException("Variable `" + variableExpression.getVariable().getName() + "` has not been declared");
        }

        return LLVMConstInt(i32Type, LLVMGetArrayLength(types.get(variableExpression.getVariable())), 0);
    }

    private LLVMValueRef visit(BoundArrayLiteralExpression arrayLiteralExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        List<BoundExpression> elements = arrayLiteralExpression.getElements();
        int size = elements.size();

        PointerPointer<Pointer> els = new PointerPointer<>(size);
        for (int i = 0; i < size; i++) {
            els.put(i, visit(elements.get(i), builder, context, function));
        }
        return LLVMConstArray(getLlvmTypeRef(((ArrayTypeSymbol)arrayLiteralExpression.getType()).getType()), els, size);
    }

    private LLVMValueRef visit(BoundAssignmentExpression assignmentExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        VariableSymbol variableSymbol = assignmentExpression.getVariable();
        if (!variables.containsKey(variableSymbol)) {
            throw new IllegalStateException("Variable `" + variableSymbol.getName() + "` has not been declared");
        }
        LLVMValueRef ptr = variables.get(variableSymbol);

        LLVMValueRef val = visit(assignmentExpression.getExpression(), builder, context, function);

        return LLVMBuildStore(builder, val, ptr);
    }

    private LLVMValueRef visit(BoundIncrementExpression incrementExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {
        VariableSymbol variableSymbol = incrementExpression.getVariableSymbol();
        if (!variables.containsKey(variableSymbol)) {
            throw new IllegalStateException("Variable `" + variableSymbol.getName() + "` has not been declared");
        }
        LLVMValueRef ptr = variables.get(variableSymbol);

        return LLVMBuildStore(builder, LLVMBuildAdd(builder, LLVMBuildLoad(builder, ptr, variableSymbol.getName()), LLVMConstInt(i32Type, (int) incrementExpression.getAmount().getValue(), 1), "incrtmp"), ptr);
    }

    private LLVMValueRef visit(BoundBlockExpression blockExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {
        LLVMValueRef lastVal = null;
        for (BoundExpression expression : blockExpression.getExpressions()) {
            lastVal = visit(expression, builder, context, function);
        }
        return lastVal;
    }

    private LLVMValueRef visit(BoundDoWhileExpression doWhileExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        LLVMBasicBlockRef bodyBlock = LLVMGetInsertBlock(builder);
        LLVMBasicBlockRef latchBlock = LLVMAppendBasicBlockInContext(context, function, "latch");
        LLVMBasicBlockRef exitBlock = LLVMAppendBasicBlockInContext(context, function, "exit-loop");

        LLVMPositionBuilderAtEnd(builder, latchBlock);
        LLVMValueRef condition = visit(doWhileExpression.getCondition(), builder, context, function);
        LLVMBuildCondBr(builder, condition, bodyBlock, exitBlock);

        LLVMPositionBuilderAtEnd(builder, bodyBlock);
        LLVMValueRef bodyVal = visit(doWhileExpression.getBody(), builder, context, function);
        LLVMBuildBr(builder, latchBlock);

        LLVMPositionBuilderAtEnd(builder, exitBlock);
        return bodyVal;
    }

    private LLVMValueRef visit(BoundIfExpression ifExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        if (ifExpression.getElseBody() == null) {
            LLVMBasicBlockRef thenBlock = LLVMAppendBasicBlockInContext(context, function, "then");
            LLVMBasicBlockRef exitBlock = LLVMAppendBasicBlockInContext(context, function, "exit-if");

            LLVMValueRef condition = visit(ifExpression.getCondition(), builder, context, function);
            LLVMBuildCondBr(builder, condition, thenBlock, exitBlock);

            LLVMPositionBuilderAtEnd(builder, thenBlock);
            LLVMValueRef thenVal = visit(ifExpression.getBody(), builder, context, function);
            if (thenVal == null) {
                throw new IllegalStateException("ThenVal must not be null");
            }
            LLVMBuildBr(builder, exitBlock);
            LLVMPositionBuilderAtEnd(builder, exitBlock);
            return thenVal;
        } else {

            LLVMBasicBlockRef thenBlock = LLVMAppendBasicBlockInContext(context, function, "then");
            LLVMBasicBlockRef elseBlock = LLVMAppendBasicBlockInContext(context, function, "else");
            LLVMBasicBlockRef exitBlock = LLVMAppendBasicBlockInContext(context, function, "exit-if-else");

            LLVMValueRef condition = visit(ifExpression.getCondition(), builder, context, function);
            LLVMBuildCondBr(builder, condition, thenBlock, elseBlock);

            LLVMPositionBuilderAtEnd(builder, thenBlock);
            LLVMValueRef thenVal = visit(ifExpression.getBody(), builder, context, function);
            if (thenVal == null) {
                throw new IllegalStateException("ThenVal must not be null");
            }
            LLVMBuildBr(builder, exitBlock);
            thenBlock = LLVMGetInsertBlock(builder);

            LLVMPositionBuilderAtEnd(builder, elseBlock);
            LLVMValueRef elseVal = visit(ifExpression.getElseBody(), builder, context, function);
            if (elseVal == null) {
                throw new IllegalStateException("ElseVal must not be null");
            }
            LLVMBuildBr(builder, exitBlock);
            elseBlock = LLVMGetInsertBlock(builder);

            LLVMPositionBuilderAtEnd(builder, exitBlock);
            LLVMValueRef phi = LLVMBuildPhi(builder, i32Type, "result");
            PointerPointer<Pointer> phiValues = new PointerPointer<>(2)
                    .put(0, thenVal)
                    .put(1, elseVal);
            PointerPointer<Pointer> phiBlocks = new PointerPointer<>(2)
                    .put(0, thenBlock)
                    .put(1, elseBlock);
            LLVMAddIncoming(phi, phiValues, phiBlocks, /* pairCount */ 2);
            return phi;
        }
    }

    private LLVMValueRef visit(BoundVariableDeclarationExpression variableDeclarationExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        LLVMTypeRef type;
        if (variableDeclarationExpression instanceof BoundArrayVariableDeclarationExpression) {
            BoundArrayVariableDeclarationExpression arrayVariableDeclarationExpression = (BoundArrayVariableDeclarationExpression) variableDeclarationExpression;
            type = getArrayLlvmTypeRef((ArrayTypeSymbol) arrayVariableDeclarationExpression.getType(), arrayVariableDeclarationExpression.getElementCount());

            types.put(variableDeclarationExpression.getVariable(), type);
        } else {
            type = getLlvmTypeRef(variableDeclarationExpression.getType());
        }

        LLVMValueRef val = visit(variableDeclarationExpression.getInitialiser(), builder, context, function);

        LLVMValueRef ptr = LLVMBuildAlloca(builder, type, variableDeclarationExpression.getVariable().getName());

        variables.put(variableDeclarationExpression.getVariable(), ptr);

        return LLVMBuildStore(builder, val, ptr);
    }

    private LLVMTypeRef getArrayLlvmTypeRef(ArrayTypeSymbol arrayTypeSymbol, int elementCount) {

        return LLVMArrayType(getLlvmTypeRef(arrayTypeSymbol.getType()), elementCount);
    }

    private LLVMTypeRef getLlvmTypeRef(TypeSymbol typeSymbol) {
        if (typeSymbol == INT) {
            return i32Type;
        } else {
            throw new UnsupportedOperationException("Variables of type `" + typeSymbol + "` are not yet implemented in LLVM");
        }
    }

    private LLVMValueRef visit(BoundVariableExpression variableExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        if (!variables.containsKey(variableExpression.getVariable())) {
            throw new IllegalStateException("Variable `" + variableExpression.getVariable().getName() + "` has not been declared");
        }
        LLVMValueRef ptr = variables.get(variableExpression.getVariable());
        return LLVMBuildLoad(builder, ptr, variableExpression.getVariable().getName());
    }

    private LLVMValueRef visit(BoundBinaryExpression binaryExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        LLVMValueRef lhs = visit(binaryExpression.getLeft(), builder, context, function);
        LLVMValueRef rhs = visit(binaryExpression.getRight(), builder, context, function);

        switch (binaryExpression.getOperator().getBoundOpType()) {

            case ADDITION:
                return LLVMBuildAdd(builder, lhs, rhs, "saddtmp");
            case SUBTRACTION:
                return LLVMBuildSub(builder, lhs, rhs, "ssubtmp");
            case MULTIPLICATION:
                return LLVMBuildMul(builder, lhs, rhs, "smultmp");
            case DIVISION:
                return LLVMBuildSDiv(builder, lhs, rhs, "sdivtmp");
            case REMAINDER:
                return LLVMBuildSRem(builder, lhs, rhs, "sremtmp");
            case GREATER_THAN:
                return LLVMBuildICmp(builder, LLVMIntSGT, lhs, rhs, "sgttmp");
            case LESS_THAN:
                return LLVMBuildICmp(builder, LLVMIntSLT, lhs, rhs, "slttmp");
            case GREATER_THAN_OR_EQUAL:
                return LLVMBuildICmp(builder, LLVMIntSGE, lhs, rhs, "sgetmp");
            case LESS_THAN_OR_EQUAL:
                return LLVMBuildICmp(builder, LLVMIntSLE, lhs, rhs, "sletmp");
            case EQUALS:
                return LLVMBuildICmp(builder, LLVMIntEQ, lhs, rhs, "eqtmp");
            case NOT_EQUALS:
                return LLVMBuildICmp(builder, LLVMIntNE, lhs, rhs, "netmp");
            case BOOLEAN_OR:
                return LLVMBuildOr(builder, lhs, rhs, "ortmp");
            case BOOLEAN_AND:
                return LLVMBuildAnd(builder, lhs, rhs, "andtmp");
            case BOOLEAN_XOR:
                return LLVMBuildXor(builder, lhs, rhs, "xortmp");
            case ERROR:
                throw new IllegalStateException("Unexpected binary operation ERROR");
            default:
                throw new UnsupportedOperationException("Compilation for binary operation `" + binaryExpression.getOperator().getBoundOpType() + "` is not yet supported for LLVM");
        }
    }

    private LLVMValueRef visit(BoundLiteralExpression literalExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        if (literalExpression.getType() == INT) {
            return LLVMConstInt(i32Type, (int) literalExpression.getValue(), 0);
        }
        if (literalExpression.getType() == TypeSymbol.BOOL) {
            return LLVMConstInt(i32Type, (boolean) literalExpression.getValue() ? 1 : 0, 0);
        }
        if (literalExpression.getType() == TypeSymbol.STRING) {
            String value = (String) literalExpression.getValue();
            return LLVMBuildGlobalStringPtr(builder, value, value); //TODO: This needs to be a pointer but not global
        }
        throw new UnsupportedOperationException("Literals of type `" + literalExpression.getType() + "` are not yet supported in LLVM");
    }

    private LLVMValueRef visit(BoundPrintExpression printExpression, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {

        if (formatStr == null) {
            formatStr = LLVMBuildGlobalStringPtr(builder, "%d\n", "formatStr");
        }

        LLVMValueRef res = visit(printExpression.getExpression(), builder, context, function);

        PointerPointer<Pointer> printArgs;
        if (printExpression.getExpression().getType() == STRING) {
            printArgs = new PointerPointer<>(2)
                    .put(0, LLVMBuildGlobalStringPtr(builder, "%s\n", "str"))
                    .put(1, res);
        } else {
            printArgs = new PointerPointer<>(2)
                    .put(0, formatStr)
                    .put(1, res);
        }

        return LLVMBuildCall(builder, printf, printArgs, 2, "printcall");
    }

    private void visitMainMethod(BoundFunctionDeclarationExpression mainMethodDeclaration, LLVMBuilderRef builder, LLVMContextRef context, LLVMValueRef function) {
        List<TypeSymbol> argumentTypes = mainMethodDeclaration.getArguments().stream()
                .map(BoundFunctionArgumentExpression::getType)
                .collect(Collectors.toList());

        if (argumentTypes.size() == 1) {
            throw new UnsupportedOperationException("Main method args are not yet implemented in LLVM");
        }

        for (BoundExpression expression : mainMethodDeclaration.getBody().getExpressions()) {
            visit(expression, builder, context, function);
        }
    }
}
