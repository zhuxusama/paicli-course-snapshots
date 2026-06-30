package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAnalyzerTest {

    private final CodeAnalyzer analyzer = new CodeAnalyzer();

    @Test
    void extractsImportsInheritanceContainmentAndCalls() throws Exception {
        Path path = Paths.get("src/test/resources/rag/SampleService.java").toAbsolutePath();
        List<CodeRelation> relations = analyzer.analyzeFile(path);

        assertTrue(relations.stream().anyMatch(r ->
                        r.relationType().equals("extends")
                                && r.fromName().equals("SampleService")
                                && r.toName().equals("BaseService")),
                "should extract extends relation");

        assertTrue(relations.stream().anyMatch(r ->
                        r.relationType().equals("implements")
                                && r.fromName().equals("SampleService")
                                && r.toName().equals("ServiceInterface")),
                "should extract implements relation");

        assertTrue(relations.stream().anyMatch(r ->
                        r.relationType().equals("contains")
                                && r.fromName().equals("SampleService")
                                && r.toName().contains("findUserById")),
                "should extract class contains method relation");

        assertTrue(relations.stream().anyMatch(r ->
                        r.relationType().equals("imports")
                                && r.fromName().equals("file")
                                && r.toName().equals("UserRepository")),
                "should extract non-JDK imports");

        assertTrue(relations.stream().anyMatch(r ->
                        r.relationType().equals("calls")
                                && r.fromName().contains("findUserById")
                                && r.toName().equals("findById")),
                "should extract method calls");
    }
}
