# s15锛氫唬鐮佸垏鍧椼€丄ST 鍏崇郴涓庣储寮?鈥斺€?涓轰唬鐮佸簱瑁呬笂"缁撴瀯鎰熺煡"

[涓婁竴绔狅細s13 骞惰宸ュ叿涓庨绠梋(../s13_parallel_budget/) 鈫?`s15` 鈫?[s15锛欵mbedding 涓庡悜閲忓瓨鍌紙寰呬骇鍑猴級](../s16_embedding_vector_search/)

> "鍏堢湅娓呯粨鏋勶紝鍐嶅姞閫熸悳绱? 鈥斺€?鍦ㄧ簿纭枃鏈尮閰嶄箣鍓嶏紝鍏堢悊瑙ｄ唬鐮佺殑楠ㄦ灦銆?>
> **绯荤粺灞?*: RAG 鍩虹璁炬柦 鈥斺€?浠ｇ爜鍒嗗潡鍜屽叧绯诲浘璋辨槸璇箟妫€绱㈢殑鍓嶆彁銆?
---

## 鍘熼」鐩噷瀹冨湪鍝噷

婧愰」鐩?PaiCLI 鐨?`com.paicli.rag` 鍖呭寘鍚?10 涓枃浠讹紝鍒嗕负涓夊眰锛?
| 灞?| 鏂囦欢 | 鑱岃矗 |
|---|---|---|
| 鏁版嵁妯″瀷 | `CodeChunk.java`, `CodeRelation.java` | 瀹氫箟"浠ｇ爜鍧?鍜?浠ｇ爜鍏崇郴"鐨勭粨鏋?|
| 鍒嗘瀽/鍒嗗潡 | `CodeAnalyzer.java`, `CodeChunker.java` | 鍩轰簬 JavaParser AST 鍋氳涔夊垎鍧楀拰鍏崇郴鎻愬彇 |
| 绱㈠紩/妫€绱?| `CodeIndex.java`, `CodeRetriever.java`, `VectorStore.java`, `EmbeddingClient.java`, `RagQueryTokenizer.java`, `SearchResultFormatter.java` | 鎵弿鐩綍銆佺敓鎴?Embedding銆佸瓨鍏?SQLite銆佸仛璇箟鍙洖 |

鏈珷锛坰14锛夎鐩栧墠涓ゅ眰锛?*鏁版嵁妯″瀷 + 鍒嗘瀽/鍒嗗潡 + 绱㈠紩缂栨帓**銆傜涓夊眰鐨?Embedding / 鍚戦噺瀛樺偍 / 璇箟鍙洖鐣欑粰 s15 鍜?s16銆?
---

## 涓轰粈涔堢幇鍦ㄥ疄鐜板畠

s13 鐨?Agent 鍙互鐢?`glob_files` 鍙戠幇鏂囦欢銆佺敤 `grep_code` 鎼滅储鍏抽敭瀛椼€佺敤 `read_file` 鏌ョ湅鍐呭銆備絾杩欎笁绉嶅伐鍏烽兘渚濊禆**绮剧‘鏂囨湰鍖归厤**锛?
- "杩欎釜椤圭洰閲屽摢浜涚被瀹炵幇浜?Command 鎺ュ彛锛? 鈫?grep 鍙兘鎼?`implements Command`锛屼細婕忔帀闂存帴瀹炵幇銆?- "鐢ㄦ埛楠岃瘉鐩稿叧鐨勪唬鐮佸垎甯冨湪鍝簺妯″潡锛? 鈫?鑷劧璇█鏃犳硶鐩存帴杞负 grep 鏌ヨ銆?- "杩欎釜鏂规硶琚皝璋冪敤锛? 鈫?grep 鍙兘鎼滄柟娉曞悕瀛楃涓诧紝涓嶇煡閬撶湡姝ｇ殑璋冪敤閾俱€?
RAG 妯″潡缁欎唬鐮佸簱瑁呬笂**缁撴瀯鎰熺煡**锛欽avaParser 瑙ｆ瀽 AST 鍚庯紝鍙互鐢ㄧ被/鏂规硶/鍏崇郴绮掑害鍥炵瓟闂锛岃€屼笉鍙槸"鍖呭惈鏌愪釜瀛楃涓茬殑鏂囦欢"銆?
---

## 鏈妭浣犺浜叉墜瀹炵幇浠€涔?
鏈珷缁撴潫锛屼綘鐨勯」鐩柊澧炰簡浠ヤ笅鐪熷疄鑳藉姏锛?
- **浠ｇ爜鍒嗗潡锛圕odeChunker锛?*: 灏?Java 鏂囦欢鎸?"绫荤骇 + 鏂规硶绾? 鍒囧垎涓鸿涔夊潡锛涢潪 Java 鏂囦欢鎸夊ぇ灏忓垎娈点€?- **鍏崇郴鍥捐氨锛圕odeAnalyzer锛?*: 鎻愬彇 extends / implements / imports / calls / contains 浜旂缁撴瀯鍏崇郴銆?- **绱㈠紩缂栨帓锛圕odeIndex锛?*: 鎵弿椤圭洰鐩綍 鈫?鏀堕泦鏂囦欢 鈫?鍒嗗潡 鈫?鍒嗘瀽 鈫?閫氳繃 ProgressListener 鎶ュ憡杩涘害銆?- **3 涓暀瀛?DemoTest**: 灞曠ず杈撳叆 鈫?杞崲 鈫?杈撳嚭鐨勫畬鏁存暀瀛︽祦绋嬨€?
---

## 绔犲墠瑙勫垝

鏈珷鐨?`chapter-plan.md` 宸插湪浠ｇ爜缂栧啓鍓嶅畬鎴愶紝鍏朵腑鍒楀嚭浜嗭細
- Feature ID 鍜?File ID 鏄犲皠
- Per-file Mechanism Contract锛堟瘡鏂囦欢涓庢簮椤圭洰鐨勪竴鑷存€у悎绾︼級
- 鏂板绫诲瀷鎺ュ叆鐐规敞閲婅鍒?- DemoTest 鏁欏璁″垝
- 楠屾敹鍛戒护涓庡欢鏈熼闄?
---

## 鏈珷婧愮爜瑕嗙洊濂戠害

| Feature ID | 鏈珷澶勭悊 | 鎺ㄨ繜鍒?| 鍘熷洜 |
|---|---|---|---|
| RAG-001 | partial | s16 | 鍒嗗潡 + 鍒嗘瀽 + 缂栨帓宸插畬鎴愶紱Embedding/璇箟鍙洖寰?s16 |
| RAG-002 | partial | s16 | 鍚屼笂 |

| File ID | 鍘熼」鐩枃浠?| 鏈珷鏂囦欢 | 鏈珷鐘舵€?| 琛ュ叏绔?| 鍓╀綑鑱岃矗 |
|---|---|---|---|---|---|
| RAG-F104 | `rag/CodeAnalyzer.java` | `rag/CodeAnalyzer.java` | complete | - | 鏃?|
| RAG-F105 | `rag/CodeChunk.java` | `rag/CodeChunk.java` | complete | - | 鏃?|
| RAG-F106 | `rag/CodeChunker.java` | `rag/CodeChunker.java` | complete | - | 鏃?|
| RAG-F107 | `rag/CodeIndex.java` | `rag/CodeIndex.java` | partial | s16 | 涓嶅惈 EmbeddingClient/VectorStore |
| RAG-F108 | `rag/CodeRelation.java` | `rag/CodeRelation.java` | complete | - | 鏃?|
| RAG-F273 | `rag/CodeAnalyzerTest.java` | `rag/CodeAnalyzerTest.java` | complete | - | 鏃?|
| RAG-F274 | `rag/CodeChunkerTest.java` | `rag/CodeChunkerTest.java` | complete | - | 鏃?|
| RAG-F275 | `rag/CodeIndexTest.java` | `rag/CodeIndexTest.java` | complete | - | 鏃?|

---

## 鐪熷疄渚濊禆涓庨厤缃?
| 绫诲瀷 | 鍚嶇О | 鐗堟湰 | 鐢ㄩ€?|
|---|---|---|---|
| Maven 渚濊禆 | `com.github.javaparser:javaparser-core` | 3.28.0 | Java 17 AST 瑙ｆ瀽銆佸垎鍧椼€佸叧绯绘彁鍙?|

鏃犻渶棰濆鐜鍙橀噺鎴?API Key銆?
---

## 浠庨浂瀹炵幇姝ラ

### 1. 鏁版嵁妯″瀷锛欳odeChunk 鍜?CodeRelation

`CodeChunk` 鏄竴涓?record锛屾妸婧愪唬鐮佹媶鍒嗕负涓夌绮掑害鐨?鍧?锛?
```java
// 鏂囦欢绾у潡 鈥斺€?闈?Java 鏂囦欢鎴栧ぇ瀛楀垎娈?CodeChunk.fileChunk("README.md", content);

// 绫荤骇鍧?鈥斺€?绫荤鍚?+ 寮€澶村嚑琛岀殑瀛楁/鎴愬憳
CodeChunk.classChunk("Agent.java", "Agent", header, 15, 120);

// 鏂规硶绾у潡 鈥斺€?瀹屾暣鏂规硶浣?CodeChunk.methodChunk("Agent.java", "Agent.run", methodBody, 42, 80);
```

`CodeRelation` 璁板綍涓や釜绗﹀彿涔嬮棿鐨勭粨鏋勪緷璧栵細

```java
new CodeRelation("Agent.java", "Agent", null, "BaseAgent", "extends");
new CodeRelation("Agent.java", "Agent.run", null, "executeTools", "calls");
```

### 2. AST 鍒嗘瀽锛欳odeAnalyzer

`CodeAnalyzer` 鐢?JavaParser 鎶?Java 鏂囦欢瑙ｆ瀽鎴?AST锛圕ompilationUnit锛夛紝鐒跺悗閬嶅巻鑺傜偣锛?
1. 鎻愬彇 import 澹版槑 鈫?`imports` 鍏崇郴锛堣烦杩?`java.*` / `javax.*`锛?2. 閬嶅巻 ClassOrInterfaceDeclaration 鈫?`extends` / `implements` 鍏崇郴
3. 閬嶅巻绫荤殑姣忎釜鏂规硶 鈫?`contains` 鍏崇郴
4. 閬嶅巻 MethodCallExpr 鈫?鍚戜笂鏌ユ壘鎵€灞炴柟娉?鈫?`calls` 鍏崇郴

JavaParser 閰嶇疆涓?JAVA_17 璇█绾у埆锛屾敮鎸?record銆乻ealed class銆乼ext block銆?
### 3. 璇箟鍒嗗潡锛欳odeChunker

`CodeChunker` 瀵?Java 鏂囦欢鍋?绫荤骇 + 鏂规硶绾?鍒囧垎锛?
- 姣忎釜绫荤敓鎴愪竴涓?class chunk锛堢鍚?+ 鍓?5 琛屾垚鍛橈級
- 姣忎釜鏂规硶鐢熸垚涓€涓?method chunk锛堝畬鏁存柟娉曚綋锛?- 闈?Java 鏂囦欢鍥為€€鍒版寜 2000 瀛楃鍒嗘

```java
CodeChunker chunker = new CodeChunker();
List<CodeChunk> chunks = chunker.chunkFile(Path.of("src/main/java/.../Agent.java"));
// 鈫?[class:Agent, method:Agent.run(), method:Agent.executeTools(), ...]
```

### 4. 绱㈠紩缂栨帓锛欳odeIndex

`CodeIndex` 鎶婁笂闈㈢殑姝ラ涓叉垚涓€鏉″畬鏁寸殑绠￠亾锛?
```java
CodeIndex indexer = new CodeIndex(msg -> System.out.println(msg));
CodeIndex.IndexResult result = indexer.index("src/main/java");

// 馃攳 寮€濮嬬储寮? /project/src/main/java
// 馃搧 鍙戠幇 67 涓枃浠跺緟绱㈠紩
//    杩涘害: 10/67 (Agent.java)
//    ...
// 鉁?绱㈠紩瀹屾垚锛?45 涓唬鐮佸潡锛?9 鏉″叧绯?```

`ProgressListener` 鏄嚱鏁板紡鎺ュ彛锛岃皟鐢ㄦ柟鍙互娉ㄥ叆浠绘剰杈撳嚭娴佹潵鎺ユ敹杩涘害娑堟伅銆?
> **涓庢簮椤圭洰鐨勫樊寮?*: 婧愰」鐩殑 `CodeIndex` 鐨勬瀯閫犲嚱鏁版寔鏈?`EmbeddingClient` 鍜?`VectorStore`锛?> 浼氱珛鍗冲皢姣忎釜 chunk 鍚戦噺鍖栧苟瀛樺叆 SQLite銆傛湰绔犵殑鏁欏鐗堟殏涓嶅紩鍏ヨ繖涓や釜渚濊禆锛堝畠浠湪 s16 鍔犲叆锛夛紝
> 鍙仛鎵弿 + 鍒嗗潡 + 鍏崇郴缁熻銆?
### 5. 楠岃瘉锛氳繍琛?DemoTest

```sh
$env:JAVA_HOME='C:\Users\86182\.jdks\temurin-21'
$env:JAVA_TOOL_OPTIONS=''
cd course_full\chapters\s15_rag_index\project
mvn test -Dtest="ouccs.smy.paiclilearn.rag.*" -DskipTests=false
```

棰勬湡: 6 涓祴璇曞叏閮ㄩ€氳繃锛屾瘡涓祴璇曠敤 `System.out.println` 鎵撳嵃杈撳叆 鈫?杞崲 鈫?杈撳嚭涓変釜鐜妭銆?
---

## 鍜屽師椤圭洰杩樺樊浠€涔?
| 鏂归潰 | 鍘熼」鐩?| 鏈珷瀹炵幇 |
|---|---|---|
| 鍒嗗潡涓庡垎鏋?| CodeChunker + CodeAnalyzer 瀹屾暣 | 鉁?瀹屾暣 |
| Embedding 鐢熸垚 | EmbeddingClient 璋冪敤杩滅▼ API | 鉂?s16 |
| 鍚戦噺瀛樺偍 | VectorStore SQLite 鎸佷箙鍖?| 鉂?s16 |
| 璇箟鍙洖 | CodeRetriever 鐩镐技搴︽悳绱?| 鉂?s16 |
| 鍒嗚瘝 | RagQueryTokenizer jieba 鍒嗚瘝 | 鉂?s16 |
| CLI 鍛戒护 | `/index` 鍛戒护 + ProgressListener 缁堢杈撳嚭 | 鉂?s16 |

---

## 鐩稿涓婁竴绔犵殑鍙樺寲

| 缁勪欢 | s13 | s15 |
|---|---|---|
| 鍙繍琛岃兘鍔?| Agent 棰勭畻/鍙栨秷/骞惰宸ュ叿鎵ц | + 浠ｇ爜鍒嗗潡 / AST 鍏崇郴鍒嗘瀽 / 鐩綍鎵弿 |
| 鏂板妯″潡 | 鏃?| `rag/` 鍖咃紙5 涓簮鏂囦欢 + 3 涓祴璇?+ 1 涓祴璇曡祫婧愶級 |
| 鏂板渚濊禆 | 鏃?| `javaparser-core:3.28.0` |
| 瀵瑰簲鍘熼」鐩?| 鏃?| `com.paicli.rag` 鐨勭浜屽眰锛堝垎鏋愬眰锛?|
| 浠嶆湭瀹炵幇 | 鏃?| Embedding / 鍚戦噺瀛樺偍 / 璇箟鎼滅储 |

---

## 浠ｇ爜娉ㄩ噴瑕佹眰

鏈珷鎵€鏈夋柊澧炰唬鐮佹敞閲婂潎涓轰腑鏂囷紙琛屾敞閲娿€佸潡娉ㄩ噴銆丣avadoc锛夈€?
涓昏鏁欏娉ㄩ噴鐐癸細

- `CodeChunk`: 涓夌宸ュ巶鏂规硶鐨勪娇鐢ㄥ満鏅拰 `toEmbeddingText()` 鐨勫悗缁敤閫斻€?- `CodeRelation`: 浜旂鍏崇郴绫诲瀷鐨勫惈涔夊拰閫傜敤鏉′欢銆?- `CodeAnalyzer`: AST 鍒嗘瀽鐨勫洓姝ユ祦绋嬶紙imports 鈫?extends/implements 鈫?contains 鈫?calls锛夈€?- `CodeChunker`: AST 鍒嗗潡绛栫暐锛堢被绾?vs 鏂规硶绾э級鍜屽洖閫€璺緞锛堝ぇ鏂囨湰鍒嗘锛夈€?- `CodeIndex`: s15 涓庢簮椤圭洰鐨勫樊寮傦紙鏆備笉鍚?Embedding 鍜屾寔涔呭寲锛夛紝ProgressListener 鐨勬敞鍏ユ柟寮忋€?
---

## 璇曚竴涓?
### 杩愯 RAG 娴嬭瘯

```powershell
$env:JAVA_HOME='C:\Users\86182\.jdks\temurin-21'
$env:JAVA_TOOL_OPTIONS=''
cd course_full\chapters\s15_rag_index\project

mvn test -Dtest="ouccs.smy.paiclilearn.rag.*" -DskipTests=false
```

### 杩愯鍏ㄩ噺鍥炲綊

```powershell
mvn test -DskipTests=false
```

瑙傚療閲嶇偣锛?
- **CodeChunkerTest.demoJavaFileChunking**: SampleService.java 琚?AST 瑙ｆ瀽涓虹被绾?chunk + 4 涓柟娉曠骇 chunk銆?- **CodeAnalyzerTest.demoAnalyzeSampleService**: 浠?AST 涓彁鍙栧埌 extends BaseService銆乮mplements ServiceInterface銆乮mports UserRepository銆乧ontains 鏂规硶 鐨勫叧绯汇€?- **CodeIndexTest.demoProgressListener**: 杩涘害鍥炶皟渚濇鏀跺埌 馃攳 寮€濮嬬储寮?鈫?馃搧 鍙戠幇 N 涓枃浠?鈫?杩涘害 x/N 鈫?鉁?绱㈠紩瀹屾垚銆?
---

## 鏈珷椤圭洰蹇収

```text
course_full/chapters/s15_rag_index/
鈹溾攢鈹€ README.md
鈹溾攢鈹€ chapter-plan.md
鈹溾攢鈹€ s15_scope.md
鈹溾攢鈹€ diff-from-previous.patch
鈹斺攢鈹€ project/
    鈹溾攢鈹€ pom.xml                              (+ javaparser-core:3.28.0)
    鈹斺攢鈹€ src/
        鈹溾攢鈹€ main/java/.../rag/
        鈹?  鈹溾攢鈹€ CodeChunk.java               (+ 鏂板 54 琛?
        鈹?  鈹溾攢鈹€ CodeRelation.java            (+ 鏂板 24 琛?
        鈹?  鈹溾攢鈹€ CodeAnalyzer.java            (+ 鏂板 138 琛?
        鈹?  鈹溾攢鈹€ CodeChunker.java             (+ 鏂板 150 琛?
        鈹?  鈹斺攢鈹€ CodeIndex.java               (+ 鏂板 174 琛?
        鈹斺攢鈹€ test/
            鈹溾攢鈹€ java/.../rag/
            鈹?  鈹溾攢鈹€ CodeChunkerTest.java     (+ 鏂板 64 琛?
            鈹?  鈹溾攢鈹€ CodeAnalyzerTest.java    (+ 鏂板 59 琛?
            鈹?  鈹斺攢鈹€ CodeIndexTest.java       (+ 鏂板 64 琛?
            鈹斺攢鈹€ resources/rag/
                鈹斺攢鈹€ SampleService.java       (+ 鏂板 31 琛?
```

- 涓婁竴绔犲揩鐓? `course_full/chapters/s13_parallel_budget/project/`
- 褰撳墠蹇収: `course_full/chapters/s15_rag_index/project/`
- 鏈珷 diff: `course_full/chapters/s15_rag_index/diff-from-previous.patch`
- 涓昏鍙樻洿鏂囦欢: `pom.xml`銆乣rag/CodeChunk.java`銆乣rag/CodeRelation.java`銆乣rag/CodeAnalyzer.java`銆乣rag/CodeChunker.java`銆乣rag/CodeIndex.java`銆? 涓祴璇曟枃浠躲€? 涓祴璇曡祫婧?- 鏂板绫诲瀷鎺ュ叆鐐? 鏆傛棤澶栭儴鎺ュ叆鐐癸紙CodeIndex 鐨?Embedding/鎸佷箙鍖栨帴鍏ユ帹杩熷埌 s16锛?
---

## 鎺ヤ笅鏉?
鐜板湪浠ｇ爜搴撳凡缁忓彲浠ラ€氳繃 JavaParser AST 鐞嗚В鑷韩缁撴瀯锛氬摢浜涚被缁ф壙浜嗕粈涔堛€佸疄鐜颁簡浠€涔堟帴鍙ｃ€佹柟娉曢棿璋佽皟鐢ㄨ皝銆?
浣嗗拰婧愰」鐩?PaiCLI 鐨勫畬鏁?RAG 鑳藉姏鐩告瘮锛岃繕缂轰袱涓叧閿満鍒讹細

1. **Embedding 鍚戦噺鍖?*: 鎶婃瘡涓?chunk 鐨勬枃鏈浆鎴愭诞鐐瑰悜閲忋€?2. **璇箟鎼滅储**: 鐢ㄦ埛闂?鍝簺绫昏礋璐ｆ潈闄愭牎楠?鏃讹紝鐢ㄨ嚜鐒惰瑷€鏌ヨ鍚戦噺鐩镐技搴︼紝鑰屼笉鏄簿纭枃鏈尮閰嶃€?
s15 _Embedding 涓庡悜閲忓瓨鍌╛ 鈫?灏嗗紩鍏?`EmbeddingClient`锛堣皟鐢ㄨ繙绋?Embedding API锛夊拰 `VectorStore`锛圫QLite 鍚戦噺瀛樺偍锛夛紝骞跺崌绾?`CodeIndex` 闆嗘垚鍚戦噺鐢熸垚涓庢寔涔呭寲銆?
<details>
<summary>娣卞叆鍘熼」鐩疄鐜?/summary>

婧愰」鐩殑 `CodeIndex.index()` 鍦ㄩ亶鍘嗘瘡涓枃浠舵椂浼氳皟鐢?`EmbeddingClient.embed()` 鐢熸垚鍚戦噺锛?鐒跺悗閫氳繃 `VectorStore.insertChunks()` 鍐欏叆 SQLite銆俙VectorStore` 浣跨敤 sqlite-vec 鎵╁睍鍦?SQLite
鍐呴儴鍋氫綑寮︾浉浼煎害鎼滅储锛岄伩鍏嶅皢鎵€鏈夊悜閲忓姞杞藉埌鍐呭瓨銆?
`s16` 灏嗗紩鍏?`CodeRetriever`锛屽畠缁勫悎 `VectorStore.search()`锛堣涔夊彫鍥烇級鍜?`CodeAnalyzer` 鐨勫叧绯诲浘璋?鍋氳仈鍚堟煡璇細鍏堟寜 Embedding 鐩镐技搴﹀彫鍥炲€欓€?chunk锛屽啀鐢ㄥ叧绯诲浘璋辨墿灞曠浉鍏充唬鐮併€?
</details>
