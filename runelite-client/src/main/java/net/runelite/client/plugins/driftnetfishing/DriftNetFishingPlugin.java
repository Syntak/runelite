/*
 * Copyright (c) 2018, Syntak <syntaktv@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.driftnetfishing;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

@PluginDescriptor(
        name = "Drift Net Fishing",
        description = "Show helpful information while drift net fishing",
        tags = {"overlay", "anchors", "underwater", "skilling", "timers"}
)
@Slf4j
public class DriftNetFishingPlugin extends Plugin {
    // Name of fish for overlay
    private static final String FISH_NAME = "Fish shoal";

    // Drift net fishing regions
    private static final Set<Integer> DRIFT_FISHING_REGIONS = ImmutableSet.of(15008, 15009);

    @Inject
    private Client client;

    @Inject
    private DriftNetFishingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DriftNetFishingOverlay overlay;

    @Inject
    private ClientThread clientThread;


    @Getter(AccessLevel.PACKAGE)
    private final Set<GameObject> driftAnchors = new HashSet<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<NPC> fishies = new HashSet<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<Actor> activeFishies = new HashSet<>();

    @Getter(AccessLevel.PACKAGE)
    private final Map<Actor, Instant> activeTimers = new HashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Map<GameObject, Integer> netFishCount = new HashMap<>();


    @Provides
    DriftNetFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DriftNetFishingConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);
        clientThread.invoke(() -> rebuildAllNpcs());
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        fishies.clear();
        driftAnchors.clear();
        netFishCount.clear();
        activeFishies.clear();
        activeTimers.clear();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN ||
                event.getGameState() == GameState.HOPPING) {
            fishies.clear();
            driftAnchors.clear();
            netFishCount.clear();
            activeFishies.clear();
            activeTimers.clear();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (!configChanged.getGroup().equals("driftnetfishing")) {
            return;
        }

        rebuildAllNpcs();
    }


    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned) {
        if (!inRegion()) return;
        final NPC npc = npcSpawned.getNpc();
        final String npcName = npc.getName();

        if (npcName != null && npcName.equals(FISH_NAME)) {
            fishies.add(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        if (!inRegion()) return;
        final NPC npc = npcDespawned.getNpc();

        for (GameObject net : driftAnchors) {
            ObjectComposition impostor = client.getObjectDefinition(net.getId()).getImpostor();
            switch (impostor.getId()) {
                case ObjectID.DRIFT_NET_ANCHORS: // Not set
                case ObjectID.DRIFT_NET_ANCHORS_30953: // Set but no fish yet
                    netFishCount.put(net, 0);
                    break;
                case ObjectID.DRIFT_NET_FULL: // Set with 10 fish (full)
                    netFishCount.put(net, 10);
                    break;
                case ObjectID.DRIFT_NET_ANCHORS_30954: // Set with unknown amount of fish
                    if (netFishCount.get(net) > -1 && net.getWorldLocation().distanceTo(npc.getWorldLocation()) < 3) {
                        log.info(net.getWorldLocation().distanceTo(npc.getWorldLocation()) + " distance");
                        netFishCount.put(net, netFishCount.get(net) + 1);
                    }
                    break;
            }
        }

        fishies.remove(npc);
        activeFishies.remove(npc);
        activeTimers.remove(npc);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (!inRegion()) return;
        GameObject gameObject = event.getGameObject();
        ObjectComposition comp = client.getObjectDefinition(gameObject.getId());
        if (comp.getImpostorIds() != null && comp.getImpostor() != null) {
            switch (comp.getImpostor().getId()) {
                case ObjectID.DRIFT_NET_ANCHORS: // Not set
                case ObjectID.DRIFT_NET_ANCHORS_30953: // Set but no fish yet
                    driftAnchors.add(gameObject);
                    netFishCount.put(gameObject, 0);
                    break;
                case ObjectID.DRIFT_NET_FULL: // Set with 10 fish (full)
                    driftAnchors.add(gameObject);
                    netFishCount.put(gameObject, 10);
                    break;
                case ObjectID.DRIFT_NET_ANCHORS_30954: // Set with unknown amount of fish
                    driftAnchors.add(gameObject);
                    netFishCount.put(gameObject, -1);
                    break;
            }
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (!inRegion()) return;

        GameObject gameObject = event.getGameObject();
        driftAnchors.remove(gameObject);
        netFishCount.remove(gameObject);
    }


    @Subscribe
    public void onGameTick(GameTick event) {
        if (!inRegion()) return;

        List<Actor> toRemove = new ArrayList<>();
        activeTimers.forEach((k, v) -> {
            if (Instant.now().getEpochSecond() - v.getEpochSecond() >= 60) {
                toRemove.add(k);
            }
        });

        for (Actor item : toRemove) {
            activeTimers.remove(item);
            activeFishies.remove(item);
        }


        Player me = client.getLocalPlayer();
        if (me.getInteracting() != null) {
            if (me.getInteracting().getWorldLocation().distanceTo(me.getWorldLocation()) <= 1) {
                activeFishies.add(me.getInteracting());
                activeTimers.put(me.getInteracting(), Instant.now());
            }
        }

       /* Tile tile = client.getSelectedSceneTile();
        if (tile != null) {
            GameObject[] go = tile.getGameObjects();

            for (GameObject i : go) {


                if (i != null) {
                    ObjectComposition ob = client.getObjectDefinition(i.getId());
                    log.info(ob.getName() + " " + ob.getId());
                    if (ob.getName() != null && ob.getName().equals("null")) {
                        ob = ob.getImpostor();
                        log.info(ob.getName() + " " + ob.getId() + " fixed");
                    }

                }


            }

        }*/

    }

    private void rebuildAllNpcs() {
        fishies.clear();
        driftAnchors.clear();
        netFishCount.clear();

        if (client.getGameState() != GameState.LOGGED_IN &&
                client.getGameState() != GameState.LOADING) {
            // NPCs are still in the client after logging out,
            // but we don't want to highlight those.
            return;
        }

        for (NPC npc : client.getNpcs()) {
            final String npcName = npc.getName();
            if (npcName != null && npcName.equals(FISH_NAME)) {
                fishies.add(npc);
            }
        }
    }

    private boolean inRegion() {
        for (int region : client.getMapRegions()) {
            if (!DRIFT_FISHING_REGIONS.contains(region)) {
                fishies.clear();
                driftAnchors.clear();
                netFishCount.clear();
                activeFishies.clear();
                activeTimers.clear();
                return false;
            }
        }
        return true;
    }
}
