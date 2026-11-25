package com.flightring;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /sfr ambience …}：环境特效的调试/试玩命令（用户 2026-11-25）。
 * <p>
 * 落雷原本是"纯概率"的，等起来太难受，这里给两条：
 * <ul>
 *   <li>{@code check} —— 打印落雷条件（群系 / 站立 / 地表 / 冷却），条件都满足就<b>立刻劈一次</b>；</li>
 *   <li>{@code strike} —— 什么条件都不看，直接劈（纯看效果用）。</li>
 * </ul>
 * 环境音/粒子不需要命令：原版 {@code /playsound}、{@code /particle} 就能试。
 */
public final class AmbienceCommand {

    private AmbienceCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("simpleflightring")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ambience")
                        .then(Commands.literal("check").executes(AmbienceCommand::check))
                        .then(Commands.literal("strike").executes(AmbienceCommand::strike))));
        dispatcher.register(Commands.literal("sfr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ambience")
                        .then(Commands.literal("check").executes(AmbienceCommand::check))
                        .then(Commands.literal("strike").executes(AmbienceCommand::strike))));
    }

    private static int check(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("simpleflightring.command.ambience.no_player"));
            return 0;
        }

        ThunderheadStrikes.Report report = ThunderheadStrikes.check(player);
        Component line = Component.translatable("simpleflightring.command.ambience.check",
                mark(report.inBiome()),
                mark(report.aboveSurface()),
                report.surfaceY(),
                report.cooldownTicks() / 20L);
        source.sendSuccess(() -> line, false);

        if (report.ready()) {
            ThunderheadStrikes.strikeNow(player);
            source.sendSuccess(() -> Component.translatable("simpleflightring.command.ambience.struck"), true);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.ambience.not_ready"), false);
        return 0;
    }

    private static int strike(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("simpleflightring.command.ambience.no_player"));
            return 0;
        }
        ThunderheadStrikes.strikeNow(player);
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.ambience.struck_forced"), true);
        return 1;
    }

    private static Component mark(boolean ok) {
        return Component.literal(ok ? "✓" : "✗");
    }
}
