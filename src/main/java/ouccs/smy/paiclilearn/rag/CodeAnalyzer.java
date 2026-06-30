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
 * 浠ｇ爜鍒嗘瀽鍣?鈥斺€?鍩轰簬 JavaParser AST 鎻愬彇婧愮爜涓殑缁撴瀯鍏崇郴锛屾瀯寤轰唬鐮佸叧绯诲浘璋便€? *
 * <p>鍒嗘瀽娴佺▼锛? * <ol>
 *   <li>鐢?JavaParser 灏?Java 鏂囦欢瑙ｆ瀽涓?AST锛圕ompilationUnit锛?/li>
 *   <li>浠?import 澹版槑涓彁鍙栭潪 JDK 瀵煎叆锛岀敓鎴?{@code imports} 鍏崇郴</li>
 *   <li>閬嶅巻绫?鎺ュ彛澹版槑锛屾彁鍙?{@code extends}銆亄@code implements}銆亄@code contains} 鍏崇郴</li>
 *   <li>閬嶅巻鏂规硶璋冪敤琛ㄨ揪寮忥紝鍚戜笂鏌ユ壘鎵€灞炴柟娉曪紝鐢熸垚 {@code calls} 鍏崇郴</li>
 * </ol>
 *
 * <p>鏈珷鍙仛 AST 鍒嗘瀽涓庡叧绯绘彁鍙栵紱鍦ㄥ悗缁珷鑺備腑锛岃繖浜涘叧绯讳細瀛樺叆 VectorStore
 * 渚?{@code CodeRetriever} 鍋氳涔夊彫鍥炰笌鍏崇郴鍥捐氨鑱斿悎鏌ヨ銆? *
 * @since s15
 */
public class CodeAnalyzer {

    /** JavaParser 瀹炰緥锛氶厤缃负 Java 17 璇█绾у埆锛屾敮鎸?record銆乻ealed class銆乼ext block 绛夎娉曘€?*/
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    /**
     * 鍒嗘瀽鍗曚釜 Java 鏂囦欢锛屾彁鍙栨墍鏈変唬鐮佸叧绯汇€?     *
     * @param filePath 寰呭垎鏋愭枃浠剁殑璺緞
     * @return 璇ユ枃浠朵腑鍙戠幇鐨勬墍鏈変唬鐮佸叧绯诲垪琛紱瑙ｆ瀽澶辫触鏃惰繑鍥炵┖鍒楄〃
     * @throws IOException 璇诲彇鏂囦欢澶辫触鏃舵姏鍑?     */
    public List<CodeRelation> analyzeFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String relativePath = filePath.toString();
        List<CodeRelation> relations = new ArrayList<>();

        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return relations;
        }

        CompilationUnit cu = result.getResult().get();

        // 姝ラ 1锛氭彁鍙栧鍏ュ叧绯伙紙imports锛?        extractImports(relativePath, cu, relations);

        // 姝ラ 2锛氭彁鍙栫被绾у埆鍏崇郴锛坋xtends / implements / contains / calls锛?        extractClassRelations(relativePath, cu, relations);

        return relations;
    }

    /**
     * 鎻愬彇闈?JDK 鐨?import 澹版槑锛岀敓鎴?{@code imports} 鍏崇郴銆?     * 璺宠繃 {@code java.*} 鍜?{@code javax.*}锛屼綔涓洪」鐩唴渚濊禆鐨勮繎浼煎垽鏂€?     */
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
     * 閬嶅巻鎵€鏈夌被/鎺ュ彛澹版槑锛屾彁鍙?extends銆乮mplements銆乧ontains 鍏崇郴锛?     * 浠ュ強绫诲唴鏂规硶闂寸殑 calls 鍏崇郴銆?     */
    private void extractClassRelations(String filePath, CompilationUnit cu,
                                       List<CodeRelation> relations) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String className = clazz.getNameAsString();

            // extends锛氱埗绫?鎶借薄绫荤户鎵?            clazz.getExtendedTypes().forEach(ext ->
                    relations.add(new CodeRelation(
                            filePath, className, null, ext.getNameAsString(), "extends")));

            // implements锛氭帴鍙ｅ疄鐜?            clazz.getImplementedTypes().forEach(impl ->
                    relations.add(new CodeRelation(
                            filePath, className, null, impl.getNameAsString(), "implements")));

            // contains锛氱被 鈫?鏂规硶 鐨勫寘鍚叧绯?            clazz.getMethods().forEach(method -> {
                String methodName = method.getNameAsString();
                relations.add(new CodeRelation(
                        filePath, className, filePath,
                        className + "." + methodName, "contains"));
            });

            // calls锛氭柟娉曢棿璋冪敤鍏崇郴锛堝湪 AST 涓悜涓婃煡鎵炬墍灞炴柟娉曚綔涓鸿皟鐢ㄨ€咃級
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
     * 浠庡綋鍓?AST 鑺傜偣鍚戜笂閬嶅巻锛屾壘鍒版渶杩戠殑鏂规硶澹版槑鑺傜偣浣滀负璋冪敤鑰呫€?     */
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
