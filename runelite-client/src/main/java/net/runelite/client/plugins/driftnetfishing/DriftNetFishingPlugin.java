/*
 * Copyright (c) 2019, Syntak <dontspam@hotmail.com.au>
 * Copyright (c) 2018, James Swindle <wilingua@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
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

    // Drift net anchor ids
    private static final Set<Integer> DRIFT_FISHING_ANCHOR_IDS = ImmutableSet.of(ObjectID.DRIFT_NET_ANCHORS,
            ObjectID.DRIFT_NET_ANCHORS_30953, ObjectID.DRIFT_NET_ANCHORS_30954, ObjectID.DRIFT_NET_FULL);

    @Inject
    private Client client;

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

    private Actor lastInteractedFishy;


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
    public void onNpcSpawned(NpcSpawned npcSpawned) {
        if (incorrectArea()) return;
        final NPC npc = npcSpawned.getNpc();
        final String npcName = npc.getName();

        if (npcName != null && npcName.equals(FISH_NAME))
            fishies.add(npc);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        if (incorrectArea()) return;
        final NPC npc = npcDespawned.getNpc();

        for (GameObject net : driftAnchors) {
            if (net.getWorldLocation().distanceTo(npc.getWorldLocation()) > 3) continue;
            log.info("netted a fish, net counter: " + netFishCount.get(net));

            ObjectComposition impostor = client.getObjectDefinition(net.getId()).getImpostor();

            switch (impostor.getId()) {
                case ObjectID.DRIFT_NET_FULL:
                    netFishCount.put(net, 10); // Set fish to 10 (full)
                    break;
                case ObjectID.DRIFT_NET_ANCHORS_30954:
                    if (netFishCount.get(net) > -1) // If fish counter is known
                        netFishCount.put(net, netFishCount.get(net) + 1); // Increment fish +1
                    break;
            }
        }

        fishies.remove(npc);
        activeFishies.remove(npc);
        activeTimers.remove(npc);
    }


    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (incorrectArea()) return;

        GameObject gameObject = event.getGameObject();
        ObjectComposition comp = client.getObjectDefinition(gameObject.getId());

        if (comp.getImpostorIds() != null && comp.getImpostor() != null) {

            if (DRIFT_FISHING_ANCHOR_IDS.contains(comp.getImpostor().getId()))
                driftAnchors.add(gameObject);

            switch (comp.getImpostor().getId()) {
                case ObjectID.DRIFT_NET_ANCHORS: // No net yet
                case ObjectID.DRIFT_NET_ANCHORS_30953: // Net but no fish yet
                    netFishCount.put(gameObject, 0);
                    break;
                case ObjectID.DRIFT_NET_FULL: // Net with 10 fish (full)
                    netFishCount.put(gameObject, 10);
                    break;
                case ObjectID.DRIFT_NET_ANCHORS_30954: // Net with 1-9 fish (not sure how to determine amount)
                    netFishCount.put(gameObject, -1);
                    break;
            }
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (incorrectArea()) return;

        GameObject gameObject = event.getGameObject();
        driftAnchors.remove(gameObject);
        netFishCount.remove(gameObject);
    }


    @Subscribe
    public void onGameTick(GameTick event) {
        if (incorrectArea()) return;

        // Remove "activated" fish after 60 seconds (arbitrary)
        // todo: determine how long a fish is actually activated for
        List<Actor> toRemove = new ArrayList<>();
        activeTimers.forEach((k, v) -> {
            if (Instant.now().getEpochSecond() - v.getEpochSecond() >= 60) {
                toRemove.add(k);
            }
        });

        for (Actor item : toRemove) {
            activeTimers.remove(item);
            activeFishies.remove(item);
            log.info("fish deactivated by timeout (total: " + activeFishies.size() + ")");
        }


    }

    @Subscribe
    private void onWidgetLoaded(WidgetLoaded event) {
        if (incorrectArea() || event.getGroupId() != 607) return; // 607 is the fish bank interface

        for (GameObject net : driftAnchors) {
            if (net.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) > 3) continue;

            log.info("emptied net");
            netFishCount.put(net, 0);
        }
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (incorrectArea() || event.getSource() != client.getLocalPlayer()) return;
        if (event.getTarget() != null && event.getTarget().getName().equals(FISH_NAME)) {
            lastInteractedFishy = event.getTarget();
        } else if (event.getTarget() == null && lastInteractedFishy != null
                && client.getLocalPlayer().getWorldLocation().distanceTo(lastInteractedFishy.getWorldLocation()) == 1) {
            activeFishies.add(lastInteractedFishy);
            activeTimers.put(lastInteractedFishy, Instant.now());
            log.info("activated fish (total: " + activeFishies.size() + ")");
            lastInteractedFishy = null;
        }
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

    private boolean incorrectArea() {
        if (client.getMapRegions() == null) return true;

        for (int region : client.getMapRegions()) {
            if (!DRIFT_FISHING_REGIONS.contains(region)) {
                fishies.clear();
                driftAnchors.clear();
                netFishCount.clear();
                activeFishies.clear();
                activeTimers.clear();
                return true;
            }
        }
        return false;
    }
}
