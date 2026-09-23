package dev.aof.questqueen;

import com.mojang.logging.LogUtils;
import dev.aof.questqueen.command.QuestQueenCommands;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.net.QuestNetwork;
import dev.aof.questqueen.progress.ProgressDatabase;
import dev.aof.questqueen.progress.ProgressService;
import dev.aof.questqueen.task.TaskHooks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(QuestQueen.MODID)
public class QuestQueen {
    public static final String MODID = "questqueen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public QuestQueen(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, QuestConfig.SPEC);
        modEventBus.addListener(QuestNetwork::register);
        NeoForge.EVENT_BUS.register(QuestDefinitions.class);
        NeoForge.EVENT_BUS.register(ProgressDatabase.class);
        NeoForge.EVENT_BUS.register(ProgressService.class);
        NeoForge.EVENT_BUS.register(dev.aof.questqueen.progress.EditorSessions.class);
        NeoForge.EVENT_BUS.register(TaskHooks.class);
        NeoForge.EVENT_BUS.register(QuestQueenCommands.class);
        dev.aof.questqueen.compat.ProgressiveStagesCompat.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
