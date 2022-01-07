package com.skennedy.rasna.compilation;

import com.skennedy.rasna.Rasna;
import com.skennedy.rasna.lowering.Lowerer;
import com.skennedy.rasna.parsing.Parser;
import com.skennedy.rasna.parsing.Program;
import com.skennedy.rasna.typebinding.Binder;
import com.skennedy.rasna.typebinding.BoundProgram;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaBytecodeCompilerIntegrationTest {


    @ParameterizedTest
    @MethodSource("getFilesToTest")
    void runFile_producesCorrectOutput(String filename) throws IOException {

        PrintStream console = System.out;
        //Set up output stream
        //TODO: figure out how to delete this
        File outputFile = new File("src/test/resources/results/compilation/" + filename.split("\\.")[0] + "_result.txt");
        outputFile.createNewFile();
        PrintStream out = new PrintStream(outputFile);
        // Store current System.out before assigning a new value
        //Set output to write to file
        System.setOut(out);

        String code = read("tests", filename);

        Parser parser = new Parser();
        Program program = parser.parse(Path.of(getFullPath("tests", filename)).toAbsolutePath(), code);

        Binder binder = new Binder();
        BoundProgram boundProgram = binder.bind(program);

        Lowerer lowerer = new Lowerer();
        boundProgram = lowerer.rewrite(boundProgram);

        JavaBytecodeCompiler compiler = new JavaBytecodeCompiler();
        compiler.compile(boundProgram, filename.split("\\.")[0]);

        Process process = Runtime.getRuntime().exec("java " + filename.split("\\.")[0]);
        InputStream inputStream = process.getInputStream();
        char c = (char) inputStream.read();
        while (c != '\uFFFF') {
            System.out.print(c);
            c = (char) inputStream.read();
        }
        InputStream errorStream = process.getErrorStream();
        c = (char) errorStream.read();
        while (c != '\uFFFF') {
            System.out.print(c);
            c = (char) errorStream.read();
        }
        //Reset console
        System.setOut(console);

        String expectedResult = read("results/expected", filename.split("\\.")[0] + "_result.txt");
        String actualResult = read("results/compilation", filename.split("\\.")[0] + "_result.txt");

        assertEquals(expectedResult, actualResult);

        File classFile = new File(filename.split("\\.")[0] + ".class");
        assertTrue(classFile.delete(), "Could not delete classfile");
        File bytecodeFile = new File(filename.split("\\.")[0] + "_bytecode.txt");
        assertTrue(bytecodeFile.delete(), "Could not delete bytecode file");
    }


    private String read(String folder, String filename) throws IOException {
        File file = new File(getFullPath(folder, filename));
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    private String getFullPath(String folder, String filename) {
        return "src/test/resources/" + folder + "/" + filename;
    }

    private static Stream<String> getFilesToTest() {
        File folder = new File("src/test/resources/tests/");
        File[] listOfFiles = folder.listFiles();

        return Arrays.stream(listOfFiles)
                .filter(file -> file.getName().endsWith(Rasna.FILE_EXT))
                .map(File::getName);
    }
}