package ouccs.smy.paiclilearn.rag;

/**
 * 浠ｇ爜鍏崇郴鏁版嵁妯″瀷 鈥斺€?璁板綍婧愮爜涓袱涓鍙蜂箣闂寸殑缁撴瀯渚濊禆銆? *
 * <p>浜旂鍏崇郴绫诲瀷锛? * <ul>
 *   <li><b>extends</b>锛氱被缁ф壙</li>
 *   <li><b>implements</b>锛氭帴鍙ｅ疄鐜?/li>
 *   <li><b>imports</b>锛氶潪 JDK 瀵煎叆渚濊禆锛堣繎浼奸」鐩唴渚濊禆锛?/li>
 *   <li><b>calls</b>锛氭柟娉曡皟鐢紙鍚屼竴绫诲唴鐨勬柟娉曢棿璋冪敤锛?/li>
 *   <li><b>contains</b>锛氱被鍖呭惈鏂规硶</li>
 * </ul>
 *
 * @param fromFile     婧愭枃浠惰矾寰? * @param fromName     婧愮鍙峰悕绉帮紙绫诲悕銆佹柟娉曞悕鎴?"file"锛? * @param toFile       鐩爣鏂囦欢璺緞锛堝彲涓?null 琛ㄧず灏氭湭瑙ｆ瀽鍒板叿浣撴枃浠讹級
 * @param toName       鐩爣绗﹀彿鍚嶇О
 * @param relationType 鍏崇郴绫诲瀷
 * @since s15
 */
public record CodeRelation(String fromFile, String fromName,
                           String toFile, String toName, String relationType) {
}
