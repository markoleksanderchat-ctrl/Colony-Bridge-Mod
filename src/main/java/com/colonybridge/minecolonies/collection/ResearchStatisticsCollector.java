package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.*;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class ResearchStatisticsCollector {
    private static final int MAX_STATISTIC_TYPES = 256;

    public Result collect(ColonyCollectionContext context) {
        context.requireServerThread();
        return new Result(research(context), statistics(context));
    }

    private ResearchData research(ColonyCollectionContext context) {
        try {
            var tree = context.colony().getResearchManager().getResearchTree();
            List<ResourceLocation> completedSource = List.copyOf(tree.getCompletedList());
            List<String> completed = completedSource.stream().map(ResourceLocation::toString).sorted().toList();
            List<ResearchProjectData> inProgress = tree.getResearchInProgress().stream()
                    .map(project -> new ResearchProjectData(project.getId().toString(), project.getBranch().toString(),
                            project.getDepth(), project.getProgress(), CollectionSupport.stringValue(project.getState())))
                    .sorted(Comparator.comparing(ResearchProjectData::id)).toList();
            Map<String, ResearchEffectData> effects = new TreeMap<>();
            for (ResourceLocation researchId : completedSource) {
                for (IResearchEffect effect : IGlobalResearchTree.getInstance().getEffectsForResearch(researchId)) {
                    String id = effect.getId().toString();
                    effects.put(id, new ResearchEffectData(id, translationKey(effect.getName()), translationKey(effect.getSubtitle()),
                            CollectionSupport.safe(() -> context.colony().getResearchManager().getResearchEffects()
                                    .getEffectStrength(effect.getId()), null)));
                }
            }
            return new ResearchData(completed, inProgress, List.copyOf(effects.values()));
        } catch (Exception exception) {
            context.errors().add(CollectionSupport.error("research", context.colonyId(), "RESEARCH_READ_FAILED", exception));
            return new ResearchData(List.of(), List.of(), List.of());
        }
    }

    private StatisticsCollection statistics(ColonyCollectionContext context) {
        Map<String, Integer> lifetime = new TreeMap<>();
        Map<String, Integer> today = new TreeMap<>();
        Map<String, Integer> recent = new TreeMap<>();
        int windowDays = 7;
        try {
            List<String> types = context.colony().getStatisticsManager().getStatTypes().stream().sorted().toList();
            int exported = Math.min(types.size(), MAX_STATISTIC_TYPES);
            for (int index = 0; index < exported; index++) {
                String type = types.get(index);
                lifetime.put(type, context.colony().getStatisticsManager().getStatTotal(type));
                if (context.currentDay() != null) {
                    today.put(type, context.colony().getStatisticsManager().getStatsInPeriod(type, context.currentDay(), context.currentDay()));
                    recent.put(type, context.colony().getStatisticsManager().getStatsInPeriod(type,
                            Math.max(0, context.currentDay() - windowDays + 1), context.currentDay()));
                }
            }
            if (types.size() > MAX_STATISTIC_TYPES) context.warnings().add(new BridgeMessage("statistics", context.colonyId(),
                    "STATISTICS_TRUNCATED", "Exported the first " + MAX_STATISTIC_TYPES + " of " + types.size() + " statistic types."));
        } catch (Exception exception) {
            context.warnings().add(CollectionSupport.error("statistics", context.colonyId(), "STATISTICS_READ_FAILED", exception));
        }
        return new StatisticsCollection(lifetime, new RecentStatisticsData(context.currentDay(), windowDays, today, recent));
    }

    private String translationKey(Object contents) {
        return contents instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    public record Result(ResearchData research, StatisticsCollection statistics) {
    }

    public record StatisticsCollection(Map<String, Integer> lifetime, RecentStatisticsData recent) {
    }
}
