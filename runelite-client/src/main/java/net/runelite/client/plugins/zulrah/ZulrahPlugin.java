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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcActionChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.Arrays;
import java.util.Set;

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

    // 2042 green range
    // 2043 red melee
    // 2044 blue mage

    private NPC zulrah;
    private int prevID;
    private WorldPoint prevLoc;

    private int counter = 0;


    private void transition(int id, WorldPoint loc) {
        counter++;
        log.info("Phase num: " + counter);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (zulrah == null) return;
        log.info("Zulrah at: " + zulrah.getWorldLocation().getX() + "," + zulrah.getWorldLocation().getY() + "," + zulrah.getWorldLocation().getPlane());


        // If zulrah changes colour and/or moves location: transition to next phase
        if (zulrah.getId() != prevID || zulrah.getWorldLocation().distanceTo(prevLoc) > 3)
            transition(zulrah.getId(), zulrah.getWorldLocation());

        prevID = zulrah.getId();
        prevLoc = zulrah.getWorldLocation();
    }


    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (!ZULRAH_IDS.contains(event.getNpc().getId())) return;

        zulrah = event.getNpc();
        prevID = zulrah.getId();
        prevLoc = zulrah.getWorldLocation();
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (!ZULRAH_IDS.contains(event.getNpc().getId())) return;

        zulrah = null;
    }

}
