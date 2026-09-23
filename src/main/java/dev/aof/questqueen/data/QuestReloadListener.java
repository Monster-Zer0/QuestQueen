package dev.aof.questqueen.data;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public class QuestReloadListener extends SimplePreparableReloadListener<QuestPack> {
    @Override
    protected QuestPack prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return QuestDefinitions.loadFrom(resourceManager);
    }

    @Override
    protected void apply(QuestPack object, ResourceManager resourceManager, ProfilerFiller profiler) {
        QuestDefinitions.apply(object);
    }
}
