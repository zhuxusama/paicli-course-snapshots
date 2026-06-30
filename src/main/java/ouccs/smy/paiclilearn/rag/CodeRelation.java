package ouccs.smy.paiclilearn.rag;

/**
 * 代码关系模型：描述两个符号之间的结构关系。
 *
 * @param fromFile 来源文件
 * @param fromName 来源符号
 * @param toFile 目标文件，暂未解析时可为空
 * @param toName 目标符号
 * @param relationType 关系类型：imports / extends / implements / contains / calls
 */
public record CodeRelation(String fromFile, String fromName,
                           String toFile, String toName, String relationType) {
}
