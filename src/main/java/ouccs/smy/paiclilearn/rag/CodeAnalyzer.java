package ouccs.smy.paiclilearn.rag;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 代码分析器 —— 基于 JavaParser AST 提取源码中的结构关系，构建代码关系图谱。
 *
 * <p>分析流程：
 * <ol>
 *   <li>用 JavaParser 将 Java 文件解析为 AST（CompilationUnit）</li>
 *   <li>从 import 声明中提取非 JDK 导入，生成 {@code imports} 关系</li>
 *   <li>遍历类/接口声明，提取 {@code extends}、{@code implements}、{@code contains} 关系</li>
 *   <li>遍历方法调用表达式，向上查找所属方法，生成 {@code calls} 关系</li>
 * </ol>
 *
 * <p>本章只做 AST 分析与关系提取；在后续章节中，这些关系会存入 VectorStore
 * 供 {@code CodeRetriever} 做语义召回与关系图谱联合查询。
 *
 * @since s14
 */
public class CodeAnalyzer {

    /** JavaParser 实例：配置为 Java 17 语言级别，支持 record、sealed class、text block 等语法。 */
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    /**
     * 分析单个 Java 文件，提取所有代码关系。
     *
     * @param filePath 待分析文件的路径
     * @return 该文件中发现的所有代码关系列表；解析失败时返回空列表
     * @throws IOException 读取文件失败时抛出
     */
    public List<CodeRelation> analyzeFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String relativePath = filePath.toString();
        List<CodeRelation> relations = new ArrayList<>();

        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return relations;
        }

        CompilationUnit cu = result.getResult().get();

        // 步骤 1：提取导入关系（imports）
        extractImports(relativePath, cu, relations);

        // 步骤 2：提取类级别关系（extends / implements / contains / calls）
        extractClassRelations(relativePath, cu, relations);

        return relations;
    }

    /**
     * 提取非 JDK 的 import 声明，生成 {@code imports} 关系。
     * 跳过 {@code java.*} 和 {@code javax.*}，作为项目内依赖的近似判断。
     */
    private void extractImports(String filePath, CompilationUnit cu, List<CodeRelation> relations) {
        for (ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();
            String simpleName = importName.substring(importName.lastIndexOf('.') + 1);
            if (!importName.startsWith("java.") && !importName.startsWith("javax.")) {
                relations.add(new CodeRelation(
                        filePath, "file", null, simpleName, "imports"));
            }
        }
    }

    /**
     * 遍历所有类/接口声明，提取 extends、implements、contains 关系，
     * 以及类内方法间的 calls 关系。
     */
    private void extractClassRelations(String filePath, CompilationUnit cu,
                                       List<CodeRelation> relations) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String className = clazz.getNameAsString();

            // extends：父类/抽象类继承
            clazz.getExtendedTypes().forEach(ext ->
                    relations.add(new CodeRelation(
                            filePath, className, null, ext.getNameAsString(), "extends")));

            // implements：接口实现
            clazz.getImplementedTypes().forEach(impl ->
                    relations.add(new CodeRelation(
                            filePath, className, null, impl.getNameAsString(), "implements")));

            // contains：类 → 方法 的包含关系
            clazz.getMethods().forEach(method -> {
                String methodName = method.getNameAsString();
                relations.add(new CodeRelation(
                        filePath, className, filePath,
                        className + "." + methodName, "contains"));
            });

            // calls：方法间调用关系（在 AST 中向上查找所属方法作为调用者）
            clazz.findAll(MethodCallExpr.class).forEach(call -> {
                String callee = call.getNameAsString();
                Optional<MethodDeclaration> parentMethod = findParentMethod(call);
                if (parentMethod.isPresent()) {
                    String caller = className + "." + parentMethod.get().getNameAsString();
                    relations.add(new CodeRelation(
                            filePath, caller, null, callee, "calls"));
                }
            });
        });
    }

    /**
     * 从当前 AST 节点向上遍历，找到最近的方法声明节点作为调用者。
     */
    private Optional<MethodDeclaration> findParentMethod(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof MethodDeclaration method) {
                return Optional.of(method);
            }
            current = current.getParentNode().orElse(null);
        }
        return Optional.empty();
    }
}
