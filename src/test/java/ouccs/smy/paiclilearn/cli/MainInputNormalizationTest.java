package ouccs.smy.paiclilearn.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** [s06 新增] 验证输入规范化与未知命令拒绝行为。 */
class MainInputNormalizationTest {

    /** 未知 /xxx 在 CLI 层被标记为 UNKNOWN_COMMAND，不进入 Agent。 */
    @Test void unknownSlashCommandsRejectedAtCliLayer() {
        String[] unknowns = {"/plan", "/team", "/mcp", "/skill", "/snapshot", "/task"};
        for (String cmd : unknowns) {
            var parsed = CliCommandParser.parse(cmd);
            assertEquals(CliCommandParser.CommandType.UNKNOWN_COMMAND, parsed.type(),
                    cmd + " 应在当前章节被拒绝（后续章节逐步实现）");
        }
    }

    /** 只有行首以 / 开头的输入才被检查是否为命令。 */
    @Test void slashInMiddleNotACcommand() {
        assertNotEquals(CliCommandParser.CommandType.UNKNOWN_COMMAND,
                CliCommandParser.parse("请执行 /bin/ls 命令").type());
        assertNotEquals(CliCommandParser.CommandType.CLEAR,
                CliCommandParser.parse("执行 clear 操作").type());
    }

    /** 空白和空输入返回 NONE。 */
    @Test void blankInputReturnsNone() {
        assertEquals(CliCommandParser.CommandType.NONE, CliCommandParser.parse("  ").type());
        assertEquals(CliCommandParser.CommandType.NONE, CliCommandParser.parse("\t").type());
    }

    /** /clear 变体测试。 */
    @Test void clearVariantsAllWork() {
        assertEquals(CliCommandParser.CommandType.CLEAR, CliCommandParser.parse("/clear").type());
        assertEquals(CliCommandParser.CommandType.CLEAR, CliCommandParser.parse("clear").type());
        assertEquals(CliCommandParser.CommandType.CLEAR, CliCommandParser.parse("/CLEAR").type());
        assertEquals(CliCommandParser.CommandType.CLEAR, CliCommandParser.parse("CLEAR").type());
    }

    /** /model 大小写不敏感。 */
    @Test void modelCommandCaseInsensitive() {
        assertEquals(CliCommandParser.CommandType.SWITCH_MODEL,
                CliCommandParser.parse("/MODEL glm").type());
        assertEquals(CliCommandParser.CommandType.SWITCH_MODEL,
                CliCommandParser.parse("/Model").type());
    }
}
