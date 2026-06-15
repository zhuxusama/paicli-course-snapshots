package ouccs.smy.paiclilearn.tool;

/**
 * 工具注册表空壳。
 * <p>
 * Chapter 01 的 MiniAgent 不具备工具调用能力，
 * 此类为 Chapter 02 预留扩展点。
 */
public class ToolRegistry {

    /**
     * @return 当前是否注册了可用工具，本章始终返回 {@code false}
     */
    public boolean hasTools() {
        return false;
    }
}
