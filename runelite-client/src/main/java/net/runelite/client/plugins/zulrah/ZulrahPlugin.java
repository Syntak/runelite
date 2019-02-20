/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 *
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
package net.runelite.client.plugins.zulrah;


import javax.inject.Inject;

import com.google.common.collect.ImmutableSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.Arrays;
import java.util.Set;
import java.util.ArrayList;

@PluginDescriptor(
        name = "Zulrah",
        description = "Show helpful information while fighting Zulrah",
        tags = {"overlay", "zulrah", "boss", "money", "snake", "combat"}
)
@Slf4j
public class ZulrahPlugin extends Plugin {

    private static final int ZULRAH_GREEN = NpcID.ZULRAH;
    private static final int ZULRAH_RED = NpcID.ZULRAH_2043;
    private static final int ZULRAH_BLUE = NpcID.ZULRAH_2044;
    private static final Set<Integer> ZULRAH_IDS = ImmutableSet.of(ZULRAH_GREEN, ZULRAH_RED, ZULRAH_BLUE);

    private static final LocalPoint LOC_TOP = new LocalPoint(6720, 6208);
    private static final LocalPoint LOC_RIGHT = new LocalPoint(5440, 7360);
    private static final LocalPoint LOC_CENTRE = new LocalPoint(6720, 7616);
    private static final LocalPoint LOC_LEFT = new LocalPoint(8000, 7360);

    private static final LocalPoint PLAYER_BOTTOM_LEFT = new LocalPoint(7232, 8000);
    private static final LocalPoint PLAYER_TOP_LEFT = new LocalPoint(7232, 7232);
    private static final LocalPoint PLAYER_TOP_MIDDLE = new LocalPoint(6720, 6976);
    private static final LocalPoint PLAYER_TOP_RIGHT = new LocalPoint(6208, 7232);
    private static final LocalPoint PLAYER_BOTTOM_RIGHT = new LocalPoint(6208, 8000);

    // Player positions: https://i.imgur.com/h2gg7j5.png
    // a 7488, 7872
    // b 7232, 8000 BOTTOM_LEFT
    // c 7488, 7104
    // d 7232, 7232 TOP_LEFT
    // e 6335, 6848
    // f 5952, 7104
    // g 6208, 7232 TOP_RIGHT
    // h 5952, 7616
    // i 6208, 8000 BOTTOM_RIGHT
    // 6720, 6976 TOP_MIDDLE not in pic

    // D2: middle mage phase between 4th phase (mage) and 5th (melee)


    // phase 4 possibility 1 had green on left

    private static final int[][] ROTATION_FORMS = { // A, B1, B2, C, D1, D2
            {ZULRAH_GREEN, ZULRAH_RED, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_RED, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_RED},
            {ZULRAH_GREEN, ZULRAH_RED, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_RED, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_RED},
            {ZULRAH_GREEN, ZULRAH_RED, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_RED, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_RED},
            {ZULRAH_GREEN, ZULRAH_GREEN, ZULRAH_RED, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE},
            {ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_RED, ZULRAH_GREEN, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE},
            {ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_BLUE, ZULRAH_RED, ZULRAH_GREEN, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE, ZULRAH_GREEN, ZULRAH_BLUE}
    };


    private static final LocalPoint[][] ROTATION_LOCS = { // A, B1, B2, C, D1, D2
            {LOC_CENTRE, LOC_CENTRE, LOC_CENTRE, LOC_TOP, LOC_CENTRE, LOC_RIGHT, LOC_TOP, LOC_TOP, LOC_RIGHT, LOC_CENTRE},
            {LOC_CENTRE, LOC_CENTRE, LOC_CENTRE, LOC_RIGHT, LOC_TOP, LOC_CENTRE, LOC_LEFT, LOC_TOP, LOC_RIGHT, LOC_CENTRE},
            {LOC_CENTRE, LOC_CENTRE, LOC_CENTRE, LOC_RIGHT, LOC_LEFT, LOC_TOP, LOC_CENTRE, LOC_LEFT, LOC_TOP, LOC_RIGHT, LOC_CENTRE},
            {LOC_CENTRE, LOC_LEFT, LOC_CENTRE, LOC_RIGHT, LOC_TOP, LOC_LEFT, LOC_CENTRE, LOC_RIGHT, LOC_CENTRE, LOC_LEFT, LOC_CENTRE},
            {LOC_CENTRE, LOC_LEFT, LOC_TOP, LOC_RIGHT, LOC_CENTRE, LOC_LEFT, LOC_TOP, LOC_RIGHT, LOC_CENTRE, LOC_CENTRE, LOC_LEFT, LOC_CENTRE},
            {LOC_CENTRE, LOC_LEFT, LOC_TOP, LOC_RIGHT, LOC_CENTRE, LOC_CENTRE, LOC_LEFT, LOC_TOP, LOC_RIGHT, LOC_CENTRE, LOC_CENTRE, LOC_LEFT, LOC_CENTRE}
    };


    private static final LocalPoint[][] PLAYER_LOCS = { // A, B1, B2, C, D1, D2
            {PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_TOP_LEFT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_RIGHT, PLAYER_BOTTOM_RIGHT},
            {PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_LEFT, PLAYER_TOP_LEFT, PLAYER_TOP_LEFT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_RIGHT, PLAYER_BOTTOM_LEFT},
            {PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_TOP_LEFT, PLAYER_TOP_LEFT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_RIGHT, PLAYER_BOTTOM_LEFT},
            {PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_RIGHT, PLAYER_TOP_RIGHT, PLAYER_TOP_MIDDLE, PLAYER_TOP_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT},
            {PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_TOP_LEFT, PLAYER_TOP_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT},
            {PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_TOP_LEFT, PLAYER_TOP_LEFT, PLAYER_TOP_LEFT, PLAYER_TOP_RIGHT, PLAYER_TOP_RIGHT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT, PLAYER_BOTTOM_LEFT}
    };

    private static final int[] ROTATION_JAD = {8, 8, 9, 9, 10, 11}; // index > 2 mage first


    private NPC zulrah;
    private int prevID;
    private LocalPoint prevLoc;

    @Getter(AccessLevel.PACKAGE)
    private LocalPoint moveTo = PLAYER_BOTTOM_LEFT;

    @Getter(AccessLevel.PACKAGE)
    private LocalPoint nextMoveTo = null;

    @Getter(AccessLevel.PACKAGE)
    private int form = ZULRAH_GREEN;

    @Getter(AccessLevel.PACKAGE)
    private int nextForm = -1;

    @Getter(AccessLevel.PACKAGE)
    private boolean jad = false;

    @Getter(AccessLevel.PACKAGE)
    private boolean nextJad = false;

    @Getter(AccessLevel.PACKAGE)
    private String jadType = "Range";

    private ArrayList<Integer> possibilities = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5));
    private int phase = 0;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ZulrahTileOverlay overlay;

    @Inject
    private ZulrahFormOverlay overlay2;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);
        overlayManager.add(overlay2);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        overlayManager.remove(overlay2);
    }

    private void reset() {
        possibilities = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5));
        phase = 0;
    }

    private void filterRotations(int id, LocalPoint loc) {
        for (int rotation : new ArrayList<Integer>(possibilities)) {
            if (ROTATION_FORMS[rotation][phase] != id || ROTATION_LOCS[rotation][phase].distanceTo(loc) > 3) {
                possibilities.remove(possibilities.indexOf(rotation));
            }
        }
    }

    private void predictMove() {
        int temp = (phase + 1) >= PLAYER_LOCS[possibilities.get(0)].length ? 0 : phase + 1;
        if (possibilities.size() == 1) {
            nextMoveTo = PLAYER_LOCS[possibilities.get(0)][temp];
        } else {
            LocalPoint move = PLAYER_LOCS[possibilities.get(0)][temp];
            for (int rotation : possibilities) {
                if (move.distanceTo(PLAYER_LOCS[rotation][temp]) > 0) return;
            }
            nextMoveTo = PLAYER_LOCS[possibilities.get(0)][temp];
        }
    }

    private void predictForm() {
        int temp = (phase + 1) >= ROTATION_FORMS[possibilities.get(0)].length ? 0 : phase + 1;
        if (possibilities.size() == 1) {
            nextForm = ROTATION_FORMS[possibilities.get(0)][temp];
        } else {
            int fform = ROTATION_FORMS[possibilities.get(0)][temp];
            for (int rotation : possibilities) {
                if (fform != ROTATION_FORMS[rotation][temp]) return;
            }
            nextForm = ROTATION_FORMS[possibilities.get(0)][temp];
        }
    }

    private void predictJad() {

        if (possibilities.size() == 1) {
            nextJad = (ROTATION_JAD[possibilities.get(0)] == phase + 1);
        } else {
            boolean njad = (ROTATION_JAD[possibilities.get(0)] == phase + 1);
            for (int rotation : possibilities) {
                if (njad != (ROTATION_JAD[rotation] == phase + 1)) return;
            }
            nextJad = (ROTATION_JAD[possibilities.get(0)] == phase + 1);
        }
    }

    private void transition(int id, LocalPoint loc) {
        phase++;
        if (phase >= ROTATION_FORMS[possibilities.get(0)].length) reset();


        log.info("Phase num: " + phase);
        if (possibilities.size() > 1)
            filterRotations(id, loc);

        nextMoveTo = null;
        predictMove();
        nextForm = -1;
        predictForm();
        predictJad();

        jadType = possibilities.get(0) > 2 ? "Mage" : "Range";
        jad = (ROTATION_JAD[possibilities.get(0)] == phase);
        form = ROTATION_FORMS[possibilities.get(0)][phase];
        moveTo = PLAYER_LOCS[possibilities.get(0)][phase];
        log.info("Possibilities: " + Arrays.toString(possibilities.toArray()));
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (zulrah == null) return;
//        Player player = client.getLocalPlayer();
//        log.info("------------");
//        log.info("Zulrah at: " + zulrah.getLocalLocation().getX() + "," + zulrah.getLocalLocation().getY());
//        log.info("Player at: " + player.getLocalLocation().getX() + "," + player.getLocalLocation().getY());


        // If zulrah changes colour and/or moves location: transition to next phase
        if (zulrah.getId() != prevID || zulrah.getLocalLocation().distanceTo(prevLoc) > 3)
            transition(zulrah.getId(), zulrah.getLocalLocation());

        prevID = zulrah.getId();
        prevLoc = zulrah.getLocalLocation();
    }


    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (!ZULRAH_IDS.contains(event.getNpc().getId())) return;

        zulrah = event.getNpc();
        prevID = zulrah.getId();
        prevLoc = zulrah.getLocalLocation();
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (!ZULRAH_IDS.contains(event.getNpc().getId())) return;

        zulrah = null;
        reset();

    }

}
