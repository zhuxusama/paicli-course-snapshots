package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeAnalyzer 教学测试 —— 展示代码分析器提取的五种 AST 结构关系。
 */
class CodeAnalyzerTest {

    private final CodeAnalyzer analyzer = new CodeAnalyzer();

    @Test
    void demoAnalyzeSampleService() throws Exception {
        System.out.println("【输入】SampleService.java（extends BaseService, implements ServiceInterface）");

        Path path = Paths.get("src/test/resources/rag/SampleService.java").toAbsolutePath();
        List<CodeRelation> relations = analyzer.analyzeFile(path);

        System.out.println("【转换】JavaParser AST 分析，提取到 " + relations.size() + " 条关系：");
        for (CodeRelation r : relations) {
            System.out.printf("  %-12s %s → %s%n", r.relationType(), r.fromName(), r.toName());
        }

        System.out.println("【输出】验证五种关键关系的存在");

        // extends 关系：SampleService extends BaseService
        assertTrue(relations.stream().anyMatch(r ->
                r.relationType().equals("extends")
                        && r.fromName().equals("SampleService")
                        && r.toName().equals("BaseService")),
                "应存在 extends -> BaseService 关系");

        // implements 关系：SampleService implements ServiceInterface
        assertTrue(relations.stream().anyMatch(r ->
                r.relationType().equals("implements")
                        && r.fromName().equals("SampleService")
                        && r.toName().equals("ServiceInterface")),
                "应存在 implements -> ServiceInterface 关系");

        // contains 关系：类包含方法
        assertTrue(relations.stream().anyMatch(r ->
                r.relationType().equals("contains")
                        && r.fromName().equals("SampleService")),
                "应存在 contains 关系（类 → 方法）");

        // imports 关系：非 JDK 的 import（UserRepository）
        assertTrue(relations.stream().anyMatch(r ->
                r.relationType().equals("imports")
                        && r.fromName().equals("file")
                        && r.toName().equals("UserRepository")),
                "应存在 imports -> UserRepository 关系");
    }
}
