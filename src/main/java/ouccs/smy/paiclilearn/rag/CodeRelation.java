package ouccs.smy.paiclilearn.rag;

/**
 * 代码关系数据模型 —— 记录源码中两个符号之间的结构依赖。
 *
 * <p>五种关系类型：
 * <ul>
 *   <li><b>extends</b>：类继承</li>
 *   <li><b>implements</b>：接口实现</li>
 *   <li><b>imports</b>：非 JDK 导入依赖（近似项目内依赖）</li>
 *   <li><b>calls</b>：方法调用（同一类内的方法间调用）</li>
 *   <li><b>contains</b>：类包含方法</li>
 * </ul>
 *
 * @param fromFile     源文件路径
 * @param fromName     源符号名称（类名、方法名或 "file"）
 * @param toFile       目标文件路径（可为 null 表示尚未解析到具体文件）
 * @param toName       目标符号名称
 * @param relationType 关系类型
 * @since s14
 */
public record CodeRelation(String fromFile, String fromName,
                           String toFile, String toName, String relationType) {
}
