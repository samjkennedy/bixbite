package com.skennedy.rasna.simulation;

import com.skennedy.rasna.Rasna;
import com.skennedy.rasna.lowering.JVMLowerer;
import com.skennedy.rasna.parsing.Parser;
import com.skennedy.rasna.parsing.Program;
import com.skennedy.rasna.typebinding.Binder;
import com.skennedy.rasna.typebinding.BoundProgram;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatorIntegrationTest {

    @ParameterizedTest
    @MethodSource("getFilesToTest")
    void runFile_producesCorrectOutput(String filename) throws IOException {

        PrintStream console = System.out;
        //Set up output stream
        File outputFile = new File("src/test/resources/results/simulation/" + filename.split("\\.")[0] + "_result.txt");
        outputFile.createNewFile();
        PrintStream out = new PrintStream(outputFile);
        // Store current System.out before assigning a new value
        //Set output to write to file
        System.setOut(out);

        String code = read("tests", filename);

        Parser parser = new Parser();
        Program program = parser.parse(Path.of(filename).toAbsolutePath(), code);

        Binder binder = new Binder();
        BoundProgram boundProgram = binder.bind(program);

        JVMLowerer lowerer = new JVMLowerer();
        boundProgram = lowerer.rewrite(boundProgram);

        Simulator simulator = new Simulator(System.out);
        simulator.simulate(boundProgram);
        //Reset console
        System.setOut(console);
        //Do it again so I can see the output
        simulator.simulate(boundProgram);

        String expectedResult = read("results/expected", filename.split("\\.")[0] + "_result.txt");
        String actualResult = read("results/simulation", filename.split("\\.")[0] + "_result.txt");

        assertEquals(expectedResult, actualResult);
    }

    private String read(String folder, String filename) throws IOException {
        String path = "src/test/resources/" + folder + "/" + filename;
        File file = new File(path);
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    private static Stream<String> getFilesToTest() {
        File folder = new File("src/test/resources/tests/");
        return Arrays.stream(folder.listFiles())
                .filter(file -> file.getName().endsWith(Rasna.FILE_EXT))
                .map(File::getName);
    }

}