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
import net.runelite.api.coords.WorldArea;
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
        tags = {"overlay", "anchors", "underwater", "skilling", "hunter"}
)
@Slf4j
public class DriftNetFishingPlugin extends Plugin {
    // Name of fish for overlay
    private static final String FISH_NAME = "Fish shoal";

    // Distance to a net when it is interacted with by a fish or the player
    private static final int SUCC_DISTANCE = 2;

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
    private final Set<NPC> activeFishies = new HashSet<>();

    @Getter(AccessLevel.PACKAGE)
    private final Map<NPC, Instant> activeTimers = new HashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Map<GameObject, Integer> netFishCount = new HashMap<>();

    // Areas in which activated fish will run at nets - not used at the moment
    private final Map<GameObject, WorldArea> consumeAreas = new HashMap<>();

    private NPC lastInteractedFishy;

    private Boolean prodThisTick = false;

    //delete me
    @Getter(AccessLevel.PACKAGE)
    private final Map<NPC, Integer> fishNum = new HashMap<>();


    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);
        clientThread.invoke(() -> rebuildAllNpcs());
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        fishies.clear();
        fishNum.clear();
        driftAnchors.clear();
        consumeAreas.clear();
        netFishCount.clear();
        activeFishies.clear();
        activeTimers.clear();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN ||
                event.getGameState() == GameState.HOPPING) {
            fishies.clear();
            fishNum.clear();
            driftAnchors.clear();
            consumeAreas.clear();
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


        if (npcName != null && npcName.equals(FISH_NAME)) {
            fishies.add(npc);
            for (int i = 1; i <= fishies.size() + 1; i++) {
                if (!fishNum.values().contains(i)) {
                    fishNum.put(npc, i);
                    break;
                }
            }
        }
    }


    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        if (incorrectArea()) return;
        final NPC npc = npcDespawned.getNpc();

        for (GameObject net : driftAnchors) {
            if (net.getWorldLocation().distanceTo(npc.getWorldLocation()) > SUCC_DISTANCE) continue;
            log.info("netted a fish (distance: " + net.getWorldLocation().distanceTo(npc.getWorldLocation()) + ") (id: " + fishNum.get(npc) + ") (total: " + (activeFishies.size() - 1) + "), net counter: " + netFishCount.get(net));

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
        fishNum.remove(npc);
        activeFishies.remove(npc);
        activeTimers.remove(npc);
    }


    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (incorrectArea()) return;

        GameObject gameObject = event.getGameObject();
        ObjectComposition comp = client.getObjectDefinition(gameObject.getId());

        // Set fish counters (if possible)
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

        // Set fish consume areas (areas where fish will run at a net)
        switch (gameObject.getOrientation().getNearestDirection().name()) {
            case "WEST":
                consumeAreas.put(gameObject, new WorldArea(gameObject.getWorldLocation().dx(-6).dy(-2), 5, 5));
                break;
            case "NORTH":
                consumeAreas.put(gameObject, new WorldArea(gameObject.getWorldLocation().dx(-2).dy(1), 4, 5));
                break;
        }

    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (incorrectArea()) return;

        GameObject gameObject = event.getGameObject();
        driftAnchors.remove(gameObject);
        netFishCount.remove(gameObject);
        consumeAreas.remove(gameObject);
    }


    @Subscribe
    public void onGameTick(GameTick event) {
        if (incorrectArea()) return;

        prodThisTick = false;

        // Remove "activated" fish after 60 seconds (arbitrary)
        // todo: determine how long a fish is actually activated for
        List<NPC> toRemove = new ArrayList<>();
        activeTimers.forEach((k, v) -> {
            if (Instant.now().getEpochSecond() - v.getEpochSecond() >= 60) {
                toRemove.add(k);
                log.info("fish deactivated by timeout (id: " + fishNum.get(k) + ") (total: " + (activeFishies.size() - 1) + ")");
            }
        });


        //// Remove "activated" fish that move into the consume area of an inactive net
        for (GameObject net : driftAnchors) {
            ObjectComposition comp = client.getObjectDefinition(net.getId());

            if (comp.getImpostor().getId() == ObjectID.DRIFT_NET_ANCHORS
                    || comp.getImpostor().getId() == ObjectID.DRIFT_NET_FULL) {

                for (NPC fish : activeFishies) {
                    if (net.getWorldLocation().distanceTo(fish.getWorldLocation()) > SUCC_DISTANCE) continue;
                    toRemove.add(fish);
                    log.info("fish deactivated by inactive net (distance: " + net.getWorldLocation().distanceTo(fish.getWorldLocation()) + ") (id: " + fishNum.get(fish) + ") (total: " + (activeFishies.size() - 1) + ")");
                }
            }
        }

        // Remove them for reals (doing it this way because of concurrent modification errors)
        for (NPC fish : toRemove) {
            activeTimers.remove(fish);
            activeFishies.remove(fish);
        }

    }

    @Subscribe
    private void onWidgetLoaded(WidgetLoaded event) {
        if (incorrectArea() || event.getGroupId() != 607) return; // 607 is the fish bank interface

        for (GameObject net : driftAnchors) {
            if (net.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) > SUCC_DISTANCE) continue;

            log.info("emptied net");
            netFishCount.put(net, 0);
        }
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (incorrectArea()
                || event.getSource() != client.getLocalPlayer()
                || event.getTarget() == null) return;


        if (event.getTarget().getName().equals(FISH_NAME)) {
            log.info("interacting with " + event.getTarget().getName());
            lastInteractedFishy = (NPC) event.getTarget();

            if (prodThisTick) {
                activeFishies.add(lastInteractedFishy);
                activeTimers.put(lastInteractedFishy, Instant.now());
                log.info("activated fish (id: " + fishNum.get(lastInteractedFishy) + ") (total: " + activeFishies.size() + ")");
                lastInteractedFishy = null;
            }
        }
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getMessage() == null || !event.getMessage().contains("prod at the shoal")) return;

        prodThisTick = true;

        if(lastInteractedFishy != null) {
            activeFishies.add(lastInteractedFishy);
            activeTimers.put(lastInteractedFishy, Instant.now());
            log.info("activated fish (id: " + fishNum.get(lastInteractedFishy) + ") (total: " + activeFishies.size() + ")");
            lastInteractedFishy = null;
        }
    }

    private void rebuildAllNpcs() {
        fishies.clear();
        fishNum.clear();
        driftAnchors.clear();
        netFishCount.clear();
        consumeAreas.clear();

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
                fishNum.put(npc, fishNum.size() + 1);
            }
        }
    }

    private boolean incorrectArea() {
        if (client.getMapRegions() == null) return true;

        for (int region : client.getMapRegions()) {
            if (!DRIFT_FISHING_REGIONS.contains(region)) {
                fishies.clear();
                fishNum.clear();
                driftAnchors.clear();
                consumeAreas.clear();
                netFishCount.clear();
                activeFishies.clear();
                activeTimers.clear();
                return true;
            }
        }
        return false;
    }
}

//todo: moving away from area (before exiting tunnel) triggers npcdespawn on fish out of view (handle this)
//todo: sometimes an "active" fish wanders around in an active consume area but doesnt get succed - find out why
//todo: figure out real active timer (currently arbitrary 60 sec)
//todo: make 60 sec timer into a tick timer (100 ticks?) dont remove active status from fish until 1/2 ticks have passed