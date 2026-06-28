package ouccs.smy.paiclilearn.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** [s06 新增] 验证 CliCommandParser 的命令解析与分流逻辑。 */
class CliCommandParserTest {

    @Test void normalInputReturnsNone() {
        var cmd = CliCommandParser.parse("hello world");
        assertEquals(CliCommandParser.CommandType.NONE, cmd.type());
    }

    @Test void emptyInputReturnsNone() {
        assertEquals(CliCommandParser.CommandType.NONE, CliCommandParser.parse("").type());
        assertEquals(CliCommandParser.CommandType.NONE, CliCommandParser.parse(null).type());
    }

    @Test void parsesClearCommand() {
        var cmd = CliCommandParser.parse("/clear");
        assertEquals(CliCommandParser.CommandType.CLEAR, cmd.type());

        cmd = CliCommandParser.parse("clear");
        assertEquals(CliCommandParser.CommandType.CLEAR, cmd.type());

        cmd = CliCommandParser.parse("/CLEAR");
        assertEquals(CliCommandParser.CommandType.CLEAR, cmd.type());
    }

    @Test void parsesExitCommand() {
        assertEquals(CliCommandParser.CommandType.EXIT, CliCommandParser.parse("/exit").type());
        assertEquals(CliCommandParser.CommandType.EXIT, CliCommandParser.parse("exit").type());
    }

    @Test void parsesModelCommand() {
        var cmd = CliCommandParser.parse("/model");
        assertEquals(CliCommandParser.CommandType.SWITCH_MODEL, cmd.type());
        assertNull(cmd.payload());

        cmd = CliCommandParser.parse("/model deepseek");
        assertEquals(CliCommandParser.CommandType.SWITCH_MODEL, cmd.type());
        assertEquals("deepseek", cmd.payload());

        cmd = CliCommandParser.parse("/model    kimi  ");
        assertEquals(CliCommandParser.CommandType.SWITCH_MODEL, cmd.type());
        assertEquals("kimi", cmd.payload());
    }

    @Test void unknownSlashCommandRejected() {
        var cmd = CliCommandParser.parse("/unknown");
        assertEquals(CliCommandParser.CommandType.UNKNOWN_COMMAND, cmd.type());
        assertEquals("/unknown", cmd.payload());

        cmd = CliCommandParser.parse("/plan");
        assertEquals(CliCommandParser.CommandType.UNKNOWN_COMMAND, cmd.type(),
                "s06 未实现的命令应返回 UNKNOWN_COMMAND, s11 起 /plan 变为有效");

        // [s12 修改] /team 在 s12 起成为有效命令 (SWITCH_TEAM)，不再是 UNKNOWN_COMMAND
        cmd = CliCommandParser.parse("/team");
        assertEquals(CliCommandParser.CommandType.SWITCH_TEAM, cmd.type(),
                "s12 起 /team 变为 SWITCH_TEAM");
    }

    @Test void plainTextWithSlashInsideNotTreatedAsCommand() {
        var cmd = CliCommandParser.parse("请帮我读 /tmp/config 文件");
        assertEquals(CliCommandParser.CommandType.NONE, cmd.type(),
                "非行首的 / 不视为命令");
    }

    @Test void parsesMemoryCommands() {
        var save = CliCommandParser.parse("/save global 用户偏好中文");
        assertEquals(CliCommandParser.CommandType.SAVE_MEMORY, save.type());
        assertEquals("global 用户偏好中文", save.payload());

        var memory = CliCommandParser.parse("/memory search 中文");
        assertEquals(CliCommandParser.CommandType.MEMORY, memory.type());
        assertEquals("search 中文", memory.payload());
    }
}
