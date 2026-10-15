package dev.mosaicobot

/** Generation-only structural lint. Never executes Lua or retroactively rejects stored works.
 * The model declares the app kind; references do not prove reachable code or playable behavior. */
internal object GeneratedGamePolicy {
    private val declaration=Regex("-- interaction: (game-dual|game-touch|app)")
    // Keep comments and strings whole so quoted examples cannot satisfy code checks.
    private val lexer=Regex("""(?s)--\[(=*)\[.*?\]\1\]|--[^\r\n]*|\[(=*)\[.*?\]\2\]|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_][A-Za-z_0-9]*|[^\s]""")

    fun validate(lua:String) {
        val mode=declaration.matchEntire(lua.trimStart().lineSequence().first().trim())?.groupValues?.get(1)
        require(mode!=null) {
            "源码首行须声明 -- interaction: game-dual（默认游戏）、game-touch（用户明确仅触屏）或 app（非游戏）；按真实玩法分类"
        }
        val tokens=lexer.findAll(lua).map {it.value}.filterNot {it.startsWith("--")}.toList()
        fun has(vararg sequence:String)=tokens.windowed(sequence.size).any {it==sequence.toList()}
        fun literal(value:String)=tokens.any {it=="\"$value\"" || it=="'$value'" || it=="[[$value]]"}
        fun field(receiver:String,name:String)=has(receiver,".",name) ||
            has(receiver,"[","\"$name\"","]") || has(receiver,"[","'$name'","]")
        val importsPad=has("require","(","\"gamepad\"",")") || has("require","(","'gamepad'",")")
        if(mode=="app") {
            require(!importsPad) { "使用 gamepad 的游戏须声明 game-dual，并保留完整触屏玩法" }
            return
        }
        require(has("screen",":","touch","(",")") && literal("EXIT")) {
            "游戏须保留 screen:touch() 与可见 EXIT 入口；无手柄时也能开始、游玩、暂停和重试"
        }
        if(mode=="game-touch") {
            require(!importsPad) { "仅触屏游戏不应导入 gamepad；除非用户明确只用触屏，否则改为 game-dual 并补齐双操作" }
            return
        }
        require(has("local","pad","=","require","(","\"gamepad\"",")") ||
            has("local","pad","=","require","(","'gamepad'",")")) {
            "默认游戏缺少手机手柄：使用 local pad = require(\"gamepad\")，不要改成仅触屏来规避修复"
        }
        require(listOf("open","poll","close").all {has("pad",".",it,"(",")")}) {
            "双操作游戏须接入 pad.open()/pad.poll()/pad.close()，逐帧读取并在正常/异常退出时释放"
        }
        require(has("input","=","pad",".","poll","(",")") && field("input","connected") && field("input","input_active")) {
            "每帧 input = pad.poll()；区分 input.connected 会话与 input.input_active 时效，短暂过期只临时冻结、不设正式暂停；真断线才暂停，无手柄可触屏玩"
        }
        // Discrete/turn games need edges, but not artificial held/axis reads.
        require(listOf("pressed","events").any {field("input",it)} &&
            (has(".","start") || has("[","\"start\"","]") || has("[","'start'","]") || literal("start"))) {
            "双操作游戏须实际读取 input 的方向/动作输入及 pressed/events 边沿，接入 Start 暂停/继续，避免只连接不控制"
        }
        require(!has("pad",".","approve","(")) { "手机手柄自动连接，不要生成 pad.approve 或设备授权流程" }
    }
}
