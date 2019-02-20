/*
 * Copyright (c) 2019, Syntak <syntaktv@gmail.com>
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
package net.runelite.client.plugins.zulrah;

import net.runelite.api.Client;
import net.runelite.api.NpcID;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.*;


public class ZulrahFormOverlay extends Overlay {

    private final Client client;
    private final ZulrahPlugin plugin;
    private final PanelComponent panelComponent = new PanelComponent();

    private static final int ZULRAH_GREEN = NpcID.ZULRAH;
    private static final int ZULRAH_RED = NpcID.ZULRAH_2043;
    private static final int ZULRAH_BLUE = NpcID.ZULRAH_2044;

    @Inject
    ZulrahFormOverlay(Client client, ZulrahPlugin plugin) {
        super(plugin);
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Color green = new Color(122, 244, 66);
        Color yellow = new Color(244, 184, 88);


        panelComponent.getChildren().clear();

        String text = "Range";
        int form = plugin.getForm();
        if (form == ZULRAH_BLUE) text = "Magic";
        else if (form == ZULRAH_RED) text = "Melee";
        if (plugin.isJad()) text = plugin.getJadType() + " Jad";
        drawPhaseText(text, green);

        text = "Range";
        form = plugin.getNextForm();
        if (form == -1) text = "---";
        else if (form == ZULRAH_BLUE) text = "Magic";
        else if (form == ZULRAH_RED) text = "Melee";
        if (plugin.isNextJad()) text = plugin.getJadType() + " Jad";
        drawPhaseText(text, yellow);

        return panelComponent.render(graphics);
    }


    private void drawPhaseText(String text, Color colour) {


        panelComponent.getChildren().add(LineComponent.builder()
                .right(text)
                .rightColor(colour)
                .build());


    }

}