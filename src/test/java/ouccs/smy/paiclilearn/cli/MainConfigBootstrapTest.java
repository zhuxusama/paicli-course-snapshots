package ouccs.smy.paiclilearn.cli;

import ouccs.smy.paiclilearn.llm.LlmClientFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** [s06 新增] 验证 Main 的配置引导与 Provider 选择逻辑。 */
class MainConfigBootstrapTest {

    /** 验证 CliCommandParser 的 /model 命令解析正确提取 provider 名称。 */
    @Test void modelSwitchExtractsProviderName() {
        var cmd = CliCommandParser.parse("/model glm");
        assertEquals("glm", cmd.payload());
        assertEquals(CliCommandParser.CommandType.SWITCH_MODEL, cmd.type());
    }

    /** /model 不带参数时 payload 为 null（表示查询当前模型）。 */
    @Test void modelWithoutPayloadQueriesCurrent() {
        var cmd = CliCommandParser.parse("/model");
        assertNull(cmd.payload());
    }

    /** LlmClientFactory 能处理已知 provider 名称。 */
    @Test void llmFactoryNormalizesProviderNames() {
        assertEquals("glm", LlmClientFactory.normalizeProvider("GLM"));
        assertEquals("deepseek", LlmClientFactory.normalizeProvider("DEEPSEEK"));
        assertEquals("step", LlmClientFactory.normalizeProvider("stepfun"));
        assertEquals("kimi", LlmClientFactory.normalizeProvider("moonshot"));
        assertEquals("freellmapi", LlmClientFactory.normalizeProvider("free-llm-api"));
    }

    /** LlmClientFactory 对未知 provider 原样返回。 */
    @Test void llmFactoryPassesUnknownProvider() {
        assertEquals("unknown-prov", LlmClientFactory.normalizeProvider("unknown-prov"));
    }
}
